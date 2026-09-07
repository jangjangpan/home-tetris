package dev.hometetris.core

import kotlin.math.abs
import kotlin.random.Random

/**
 * 싱글 모드에서 상대할 컴퓨터. 사람과 똑같은 [TetrisEngine] 위에서 놀고,
 * 여기서는 "이 조각을 어디에 놓을지"만 정한다.
 *
 * 난이도는 세 가지를 한꺼번에 움직인다: 손이 빠른 정도, 실수하는 빈도,
 * 그리고 한 수 앞을 보는지 여부.
 */
data class BotConfig(
    val level: Int,
    /** 조각 하나를 놓는 데 쓰는 시간. 낮을수록 빠르다. */
    val placeIntervalMs: Long,
    /** 최선 대신 아무 데나 놓아버릴 확률. */
    val mistakeChance: Double,
    /** 다음 조각까지 보고 고를지. */
    val lookahead: Boolean,
    /** 방해 줄을 보내는 쪽으로 얼마나 욕심을 내는지. */
    val aggression: Double,
) {
    companion object {
        const val MIN_LEVEL = 1
        const val MAX_LEVEL = 10

        fun forLevel(level: Int): BotConfig {
            val l = level.coerceIn(MIN_LEVEL, MAX_LEVEL)
            return BotConfig(
                level = l,
                placeIntervalMs = 1800L - (l - 1) * 175L,
                mistakeChance = (0.55 - (l - 1) * 0.08).coerceAtLeast(0.0),
                lookahead = l >= 6,
                aggression = (l - 1) / 9.0,
            )
        }

        /** 난이도 설명. 화면에 그대로 띄운다. */
        fun describe(level: Int): String = when (level.coerceIn(MIN_LEVEL, MAX_LEVEL)) {
            1 -> "아주 느리고 실수가 잦아요"
            2 -> "느긋하고 자주 헤맵니다"
            3 -> "가끔 이상한 데 놓습니다"
            4 -> "천천히, 그래도 제법 둡니다"
            5 -> "평범하게 쌓습니다"
            6 -> "한 수 앞을 봅니다"
            7 -> "빠르고 실수가 거의 없습니다"
            8 -> "구멍을 잘 안 만듭니다"
            9 -> "빈틈없이 쌓고 자주 공격합니다"
            else -> "아주 빠릅니다. 봐주지 않아요"
        }
    }
}

/** 조각을 놓을 자리 하나. */
data class Placement(val rot: Int, val x: Int, val score: Double)

object BotAi {

    /**
     * 지금 조각을 놓을 자리를 고른다. [next]를 주면 한 수 앞까지 본다.
     * 놓을 자리가 하나도 없으면(= 사실상 게임 오버) null.
     */
    fun choose(
        board: Array<IntArray>,
        piece: PieceType,
        next: PieceType?,
        config: BotConfig,
        rng: Random,
    ): Placement? {
        val all = candidates(board, piece, config)
        if (all.isEmpty()) return null
        // 실수: 최선 대신 아무 자리나 고른다. 낮은 난이도일수록 자주 그런다.
        if (rng.nextDouble() < config.mistakeChance) return all[rng.nextInt(all.size)]
        if (!config.lookahead || next == null) return all.maxByOrNull { it.score }

        // 한 수 앞을 보는 건 비싸다(후보 하나당 다시 수십 번 시뮬레이션).
        // 그래서 당장 좋아 보이는 몇 개만 더 들여다본다. 세기는 거의 그대로면서 훨씬 싸다.
        return all.sortedByDescending { it.score }
            .take(LOOKAHEAD_WIDTH)
            .map { p ->
                val sim = simulate(board, piece, p.rot, p.x) ?: return@map p.copy(score = Double.NEGATIVE_INFINITY)
                val after = bestScore(sim.board, next, config) ?: Double.NEGATIVE_INFINITY
                p.copy(score = p.score + after * 0.5)
            }
            .maxByOrNull { it.score }
    }

    /** 놓을 수 있는 모든 자리와 그 점수(다음 조각은 보지 않은 값). 테스트에서도 쓴다. */
    fun candidates(
        board: Array<IntArray>,
        piece: PieceType,
        config: BotConfig = BotConfig.forLevel(10),
    ): List<Placement> {
        val out = ArrayList<Placement>(40)
        val rotations = if (piece == PieceType.O) 1 else 4
        for (rot in 0 until rotations) {
            for (x in -3..BOARD_W) {
                val sim = simulate(board, piece, rot, x) ?: continue
                out.add(Placement(rot, x, evaluate(sim, config)))
            }
        }
        return out
    }

    /** 한 수 앞을 볼 때 들여다보는 후보 개수. */
    private const val LOOKAHEAD_WIDTH = 6

    private fun bestScore(board: Array<IntArray>, piece: PieceType, config: BotConfig): Double? {
        var best: Double? = null
        val rotations = if (piece == PieceType.O) 1 else 4
        for (rot in 0 until rotations) {
            for (x in -3..BOARD_W) {
                val sim = simulate(board, piece, rot, x) ?: continue
                val s = evaluate(sim, config)
                if (best == null || s > best) best = s
            }
        }
        return best
    }

    /** 조각을 [rot] 모양으로 [x]열에 떨어뜨린 결과. 놓을 수 없으면 null. */
    fun simulate(board: Array<IntArray>, piece: PieceType, rot: Int, x: Int): Sim? {
        val shape = pieceCells(piece, rot)
        var y = -3
        if (collides(board, shape, x, y)) return null
        while (!collides(board, shape, x, y + 1)) y++

        val nb = Array(BOARD_H) { board[it].copyOf() }
        for (c in shape) {
            val cx = x + c[0]
            val cy = y + c[1]
            // 보드 위로 삐져나가면 그건 게임 오버다. 후보에서 뺀다.
            if (cy < 0 || cx !in 0 until BOARD_W) return null
            nb[cy][cx] = piece.ordinal + 1
        }
        val cleared = clearRows(nb)
        return Sim(nb, cleared)
    }

    data class Sim(val board: Array<IntArray>, val cleared: Int) {
        override fun equals(other: Any?): Boolean =
            other is Sim && cleared == other.cleared && board.contentDeepEquals(other.board)

        override fun hashCode(): Int = 31 * board.contentDeepHashCode() + cleared
    }

    /**
     * 널리 쓰이는 4가지 지표(높이 / 지운 줄 / 구멍 / 울퉁불퉁함)에,
     * 우리 집 공격 규칙을 반영한 항을 하나 더 붙였다.
     * 난이도가 높을수록 1줄씩 지우기보다 2줄 이상 모아 치는 쪽을 택한다.
     */
    fun evaluate(sim: Sim, config: BotConfig): Double {
        val b = sim.board
        val heights = IntArray(BOARD_W)
        for (x in 0 until BOARD_W) {
            var h = 0
            for (y in 0 until BOARD_H) {
                if (b[y][x] != CELL_EMPTY) {
                    h = BOARD_H - y
                    break
                }
            }
            heights[x] = h
        }
        val aggregate = heights.sum()

        var holes = 0
        for (x in 0 until BOARD_W) {
            var seen = false
            for (y in 0 until BOARD_H) {
                if (b[y][x] != CELL_EMPTY) seen = true
                else if (seen) holes++
            }
        }

        var bumpiness = 0
        for (x in 0 until BOARD_W - 1) bumpiness += abs(heights[x] - heights[x + 1])

        return -0.510066 * aggregate +
            0.760666 * sim.cleared +
            -0.35663 * holes +
            -0.184483 * bumpiness +
            config.aggression * attackValue(sim.cleared, heights.max())
    }

    /**
     * 우리 집 규칙에서 이 클리어가 얼마나 값진가. 2줄 이상이어야 방해 줄이 나가므로,
     * 아직 여유가 있을 때(스택이 낮을 때)는 1줄로 까먹지 않고 참는 쪽이 이득이다.
     * 위험한 높이가 되면 참지 않고 바로 지운다 - 안 그러면 자기가 먼저 죽는다.
     */
    private fun attackValue(cleared: Int, maxHeight: Int): Double = when (cleared) {
        1 -> if (maxHeight < SAFE_HEIGHT) -6.0 else 0.0
        2 -> 1.0
        3 -> 2.0
        4 -> 4.0
        else -> 0.0
    }

    /** 이 높이 아래에서는 서둘러 지우지 않고 큰 거 한 방을 노린다. */
    private const val SAFE_HEIGHT = 12

    private fun collides(board: Array<IntArray>, shape: Array<IntArray>, ox: Int, oy: Int): Boolean {
        for (c in shape) {
            val x = ox + c[0]
            val y = oy + c[1]
            if (x < 0 || x >= BOARD_W || y >= BOARD_H) return true
            if (y >= 0 && board[y][x] != CELL_EMPTY) return true
        }
        return false
    }

    private fun clearRows(b: Array<IntArray>): Int {
        var cleared = 0
        var y = BOARD_H - 1
        while (y >= 0) {
            if (b[y].all { it != CELL_EMPTY }) {
                for (yy in y downTo 1) b[yy] = b[yy - 1].copyOf()
                b[0] = IntArray(BOARD_W)
                cleared++
            } else {
                y--
            }
        }
        return cleared
    }
}
