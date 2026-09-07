package dev.junyj.lantetris.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BotAiTest {

    private fun emptyBoard() = Array(BOARD_H) { IntArray(BOARD_W) }

    private val hardest = BotConfig.forLevel(10)

    /** 난이도가 올라가면 빨라지고, 실수는 줄고, 공격 욕심은 늘어야 한다. */
    @Test
    fun difficultyScalesMonotonically() {
        val configs = (1..10).map { BotConfig.forLevel(it) }
        for (i in 0 until configs.size - 1) {
            val lo = configs[i]
            val hi = configs[i + 1]
            assertTrue("Lv${hi.level}이 Lv${lo.level}보다 빨라야 한다", hi.placeIntervalMs < lo.placeIntervalMs)
            assertTrue("실수는 줄어야 한다", hi.mistakeChance <= lo.mistakeChance)
            assertTrue("공격성은 늘어야 한다", hi.aggression >= lo.aggression)
        }
        assertTrue(configs.first().mistakeChance > 0.4)
        assertEquals(0.0, configs.last().mistakeChance, 1e-9)
        assertTrue("최고 난이도는 한 수 앞을 봐야 한다", configs.last().lookahead)
        assertTrue("최저 난이도는 안 본다", !configs.first().lookahead)
        assertTrue("가장 빠를 때도 사람이 볼 수 있는 속도여야 한다", configs.last().placeIntervalMs > 100)
    }

    @Test
    fun levelIsClamped() {
        assertEquals(1, BotConfig.forLevel(0).level)
        assertEquals(1, BotConfig.forLevel(-5).level)
        assertEquals(10, BotConfig.forLevel(99).level)
    }

    /** 빈 보드에서는 어떤 조각이든 놓을 자리가 있어야 한다. */
    @Test
    fun everyPieceHasPlacementsOnEmptyBoard() {
        for (p in PieceType.entries) {
            val c = BotAi.candidates(emptyBoard(), p)
            assertTrue("$p 놓을 자리가 있어야 한다", c.isNotEmpty())
            assertNotNull(BotAi.choose(emptyBoard(), p, null, hardest, Random(1)))
        }
    }

    /** 시뮬레이션 결과에는 꽉 찬 줄이 남아 있으면 안 된다(이미 지워졌어야 한다). */
    @Test
    fun simulationClearsFullRows() {
        val b = emptyBoard()
        for (x in 0 until BOARD_W - 1) b[BOARD_H - 1][x] = 8
        // 세로 I를 오른쪽 끝에 떨어뜨리면 맨 아랫줄이 채워진다
        val sim = BotAi.simulate(b, PieceType.I, 1, BOARD_W - 3)
        assertNotNull(sim)
        assertEquals(1, sim!!.cleared)
        assertTrue(sim.board.none { row -> row.all { it != CELL_EMPTY } })
    }

    /** 보드가 천장까지 찼으면 놓을 자리가 없다고 알려야 한다. */
    @Test
    fun fullBoardHasNoPlacement() {
        val b = Array(BOARD_H) { IntArray(BOARD_W) { 8 } }
        assertTrue(BotAi.candidates(b, PieceType.T).isEmpty())
        assertNull(BotAi.choose(b, PieceType.T, null, hardest, Random(1)))
    }

    /** 최고 난이도는 한 칸짜리 우물에 세로 I를 꽂아 줄을 지워야 한다. */
    @Test
    fun strongBotFillsTheWellWithVerticalI() {
        val b = emptyBoard()
        for (y in BOARD_H - 4 until BOARD_H) {
            for (x in 0 until BOARD_W - 1) b[y][x] = 8
        }
        val p = BotAi.choose(b, PieceType.I, null, hardest, Random(42))
        assertNotNull(p)
        val sim = BotAi.simulate(b, PieceType.I, p!!.rot, p.x)
        assertNotNull(sim)
        assertEquals("우물을 메워 4줄을 지워야 한다", 4, sim!!.cleared)
    }

    /** 최고 난이도는 구멍을 만드는 자리를 피해야 한다. */
    @Test
    fun strongBotAvoidsMakingHoles() {
        val b = emptyBoard()
        // 왼쪽에 한 칸 깊은 골을 파 둔다. O를 여기 걸치면 그 아래가 구멍이 된다.
        for (y in BOARD_H - 3 until BOARD_H) {
            for (x in 2 until BOARD_W) b[y][x] = 8
        }
        val p = BotAi.choose(b, PieceType.O, null, hardest, Random(7))
        assertNotNull(p)
        val sim = BotAi.simulate(b, PieceType.O, p!!.rot, p.x)!!
        assertEquals("구멍을 만들지 않아야 한다", 0, holesIn(sim.board))
    }

    /**
     * 높은 난이도는 여유가 있을 때 1줄로 까먹지 않고 참아야 한다.
     * (우리 집 규칙상 1줄은 공격이 0이라, 지워봐야 상대에게 아무 일도 안 생긴다.)
     */
    @Test
    fun aggressiveBotSavesSingleLineClearsWhenStackIsLow() {
        val b = emptyBoard()
        // 맨 아랫줄만 한 칸 빼고 채워 둔다. 스택이 아주 낮은 상황.
        for (x in 0 until BOARD_W - 1) b[BOARD_H - 1][x] = 8

        val greedy = BotAi.choose(b, PieceType.I, null, BotConfig.forLevel(1).copy(mistakeChance = 0.0), Random(1))!!
        val patient = BotAi.choose(b, PieceType.I, null, hardest, Random(1))!!

        assertEquals("공격성 0이면 눈앞의 1줄을 지운다", 1, BotAi.simulate(b, PieceType.I, greedy.rot, greedy.x)!!.cleared)
        assertEquals("최고 난이도는 1줄을 참는다", 0, BotAi.simulate(b, PieceType.I, patient.rot, patient.x)!!.cleared)
    }

    /** 단, 위험한 높이까지 쌓였으면 참지 말고 지워야 한다. 안 그러면 자기가 죽는다. */
    @Test
    fun aggressiveBotStillClearsWhenStackIsDangerous() {
        val b = emptyBoard()
        // 위쪽까지 높게 쌓고, 맨 윗줄 하나만 한 칸 비워 둔다.
        for (y in BOARD_H - 15 until BOARD_H) {
            for (x in 0 until BOARD_W) b[y][x] = 8
        }
        for (y in BOARD_H - 15 until BOARD_H) b[y][BOARD_W - 1] = CELL_EMPTY
        b[BOARD_H - 15][BOARD_W - 1] = CELL_EMPTY

        val p = BotAi.choose(b, PieceType.I, null, hardest, Random(1))!!
        val sim = BotAi.simulate(b, PieceType.I, p.rot, p.x)!!
        assertTrue("위험할 땐 지워야 한다", sim.cleared > 0)
    }

    /** 실수 확률이 1이면 최선이 아닌 자리도 나온다(= 약한 컴퓨터가 실제로 약하다). */
    @Test
    fun mistakeChanceProducesSuboptimalMoves() {
        val alwaysWrong = BotConfig.forLevel(1).copy(mistakeChance = 1.0)
        val board = emptyBoard()
        val best = BotAi.candidates(board, PieceType.T).maxByOrNull { it.score }!!
        val rng = Random(3)
        val picks = (1..40).map { BotAi.choose(board, PieceType.T, null, alwaysWrong, rng)!! }
        assertTrue(
            "실수 확률 1이면 최선 말고 다른 자리도 골라야 한다",
            picks.any { it.rot != best.rot || it.x != best.x }
        )
    }

    /** 실수 확률이 0이면 항상 같은(최선의) 자리를 고른다. */
    @Test
    fun perfectBotIsDeterministic() {
        val board = emptyBoard()
        val rng = Random(11)
        val first = BotAi.choose(board, PieceType.S, null, hardest, rng)!!
        repeat(20) {
            val p = BotAi.choose(board, PieceType.S, null, hardest, rng)!!
            assertEquals(first.rot, p.rot)
            assertEquals(first.x, p.x)
        }
    }

    /**
     * 실제로 한 판을 끝까지 돌려 본다.
     * 최고 난이도 컴퓨터는 조각 200개를 놓는 동안 죽지 않고 줄도 꽤 지워야 한다.
     */
    @Test
    fun strongBotSurvivesLongGame() {
        val e = TetrisEngine(20260906L)
        val rng = Random(5)
        var placed = 0
        while (placed < 200 && !e.dead) {
            val p = BotAi.choose(e.board, e.type, e.nextQueue(1).firstOrNull(), hardest, rng) ?: break
            playPlacement(e, p)
            placed++
        }
        assertTrue("200개를 다 놓기 전에 죽으면 안 된다 (놓은 개수=$placed)", placed == 200)
        assertTrue("줄도 지워야 한다 (지운 줄=${e.lines})", e.lines > 70)
    }

    /** 가장 약한 컴퓨터는 확실히 더 못해야 한다. */
    @Test
    fun weakBotIsClearlyWorseThanStrongBot() {
        fun linesAfter(config: BotConfig, seed: Long): Int {
            val e = TetrisEngine(seed)
            val rng = Random(seed)
            var placed = 0
            while (placed < 200 && !e.dead) {
                val p = BotAi.choose(e.board, e.type, e.nextQueue(1).firstOrNull(), config, rng) ?: break
                playPlacement(e, p)
                placed++
            }
            return e.lines
        }
        val weak = (1..3).sumOf { linesAfter(BotConfig.forLevel(1), it * 1000L) }
        val strong = (1..3).sumOf { linesAfter(BotConfig.forLevel(10), it * 1000L) }
        assertTrue("Lv1(${weak}줄)이 Lv10(${strong}줄)보다 훨씬 못해야 한다", strong > weak * 2)
    }

    // ---- 도우미 ----

    /** SoloSession이 하는 것과 같은 순서로 조각을 옮겨 떨어뜨린다. */
    private fun playPlacement(e: TetrisEngine, p: Placement) {
        var guard = 0
        while (e.rot != p.rot && guard++ < 6) e.input(Action.ROTATE_CW)
        while (e.px < p.x && guard++ < 20) e.input(Action.RIGHT)
        while (e.px > p.x && guard++ < 20) e.input(Action.LEFT)
        e.input(Action.HARD_DROP)
    }

    private fun holesIn(b: Array<IntArray>): Int {
        var holes = 0
        for (x in 0 until BOARD_W) {
            var seen = false
            for (y in 0 until BOARD_H) {
                if (b[y][x] != CELL_EMPTY) seen = true else if (seen) holes++
            }
        }
        return holes
    }
}
