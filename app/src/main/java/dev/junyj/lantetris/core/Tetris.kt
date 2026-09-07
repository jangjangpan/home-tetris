package dev.junyj.lantetris.core

import kotlin.math.max
import kotlin.random.Random

const val BOARD_W = 10
const val VISIBLE_H = 20
const val HIDDEN_H = 2
const val BOARD_H = VISIBLE_H + HIDDEN_H

const val CELL_EMPTY = 0
const val CELL_GARBAGE = 8

enum class PieceType { I, J, L, O, S, T, Z }

/** 회전 상태별 블록 좌표. x는 오른쪽, y는 아래쪽이 + 방향. */
private typealias Shape = Array<Array<IntArray>>

private fun shape(vararg rots: IntArray): Shape =
    Array(rots.size) { r -> Array(4) { i -> intArrayOf(rots[r][i * 2], rots[r][i * 2 + 1]) } }

private val SHAPES: Map<PieceType, Shape> = mapOf(
    PieceType.I to shape(
        intArrayOf(0, 1, 1, 1, 2, 1, 3, 1),
        intArrayOf(2, 0, 2, 1, 2, 2, 2, 3),
        intArrayOf(0, 2, 1, 2, 2, 2, 3, 2),
        intArrayOf(1, 0, 1, 1, 1, 2, 1, 3),
    ),
    PieceType.J to shape(
        intArrayOf(0, 0, 0, 1, 1, 1, 2, 1),
        intArrayOf(1, 0, 2, 0, 1, 1, 1, 2),
        intArrayOf(0, 1, 1, 1, 2, 1, 2, 2),
        intArrayOf(1, 0, 1, 1, 0, 2, 1, 2),
    ),
    PieceType.L to shape(
        intArrayOf(2, 0, 0, 1, 1, 1, 2, 1),
        intArrayOf(1, 0, 1, 1, 1, 2, 2, 2),
        intArrayOf(0, 1, 1, 1, 2, 1, 0, 2),
        intArrayOf(0, 0, 1, 0, 1, 1, 1, 2),
    ),
    PieceType.O to shape(
        intArrayOf(1, 0, 2, 0, 1, 1, 2, 1),
        intArrayOf(1, 0, 2, 0, 1, 1, 2, 1),
        intArrayOf(1, 0, 2, 0, 1, 1, 2, 1),
        intArrayOf(1, 0, 2, 0, 1, 1, 2, 1),
    ),
    PieceType.S to shape(
        intArrayOf(1, 0, 2, 0, 0, 1, 1, 1),
        intArrayOf(1, 0, 1, 1, 2, 1, 2, 2),
        intArrayOf(1, 1, 2, 1, 0, 2, 1, 2),
        intArrayOf(0, 0, 0, 1, 1, 1, 1, 2),
    ),
    PieceType.T to shape(
        intArrayOf(1, 0, 0, 1, 1, 1, 2, 1),
        intArrayOf(1, 0, 1, 1, 2, 1, 1, 2),
        intArrayOf(0, 1, 1, 1, 2, 1, 1, 2),
        intArrayOf(1, 0, 0, 1, 1, 1, 1, 2),
    ),
    PieceType.Z to shape(
        intArrayOf(0, 0, 1, 0, 1, 1, 2, 1),
        intArrayOf(2, 0, 1, 1, 2, 1, 1, 2),
        intArrayOf(0, 1, 1, 1, 1, 2, 2, 2),
        intArrayOf(1, 0, 0, 1, 1, 1, 0, 2),
    ),
)

/**
 * SRS 월킥 테이블. 원본 표는 y축 위쪽이 +라서, 화면 좌표(아래쪽이 +)에 맞게 y 부호를 뒤집어 두었다.
 * 키는 (시작 회전 * 4 + 목표 회전).
 */
private val KICKS_JLSTZ: Map<Int, Array<IntArray>> = mapOf(
    0 * 4 + 1 to arrayOf(intArrayOf(0, 0), intArrayOf(-1, 0), intArrayOf(-1, -1), intArrayOf(0, 2), intArrayOf(-1, 2)),
    1 * 4 + 0 to arrayOf(intArrayOf(0, 0), intArrayOf(1, 0), intArrayOf(1, 1), intArrayOf(0, -2), intArrayOf(1, -2)),
    1 * 4 + 2 to arrayOf(intArrayOf(0, 0), intArrayOf(1, 0), intArrayOf(1, 1), intArrayOf(0, -2), intArrayOf(1, -2)),
    2 * 4 + 1 to arrayOf(intArrayOf(0, 0), intArrayOf(-1, 0), intArrayOf(-1, -1), intArrayOf(0, 2), intArrayOf(-1, 2)),
    2 * 4 + 3 to arrayOf(intArrayOf(0, 0), intArrayOf(1, 0), intArrayOf(1, -1), intArrayOf(0, 2), intArrayOf(1, 2)),
    3 * 4 + 2 to arrayOf(intArrayOf(0, 0), intArrayOf(-1, 0), intArrayOf(-1, 1), intArrayOf(0, -2), intArrayOf(-1, -2)),
    3 * 4 + 0 to arrayOf(intArrayOf(0, 0), intArrayOf(-1, 0), intArrayOf(-1, 1), intArrayOf(0, -2), intArrayOf(-1, -2)),
    0 * 4 + 3 to arrayOf(intArrayOf(0, 0), intArrayOf(1, 0), intArrayOf(1, -1), intArrayOf(0, 2), intArrayOf(1, 2)),
)

private val KICKS_I: Map<Int, Array<IntArray>> = mapOf(
    0 * 4 + 1 to arrayOf(intArrayOf(0, 0), intArrayOf(-2, 0), intArrayOf(1, 0), intArrayOf(-2, 1), intArrayOf(1, -2)),
    1 * 4 + 0 to arrayOf(intArrayOf(0, 0), intArrayOf(2, 0), intArrayOf(-1, 0), intArrayOf(2, -1), intArrayOf(-1, 2)),
    1 * 4 + 2 to arrayOf(intArrayOf(0, 0), intArrayOf(-1, 0), intArrayOf(2, 0), intArrayOf(-1, -2), intArrayOf(2, 1)),
    2 * 4 + 1 to arrayOf(intArrayOf(0, 0), intArrayOf(1, 0), intArrayOf(-2, 0), intArrayOf(1, 2), intArrayOf(-2, -1)),
    2 * 4 + 3 to arrayOf(intArrayOf(0, 0), intArrayOf(2, 0), intArrayOf(-1, 0), intArrayOf(2, -1), intArrayOf(-1, 2)),
    3 * 4 + 2 to arrayOf(intArrayOf(0, 0), intArrayOf(-2, 0), intArrayOf(1, 0), intArrayOf(-2, 1), intArrayOf(1, -2)),
    3 * 4 + 0 to arrayOf(intArrayOf(0, 0), intArrayOf(1, 0), intArrayOf(-2, 0), intArrayOf(1, 2), intArrayOf(-2, -1)),
    0 * 4 + 3 to arrayOf(intArrayOf(0, 0), intArrayOf(-1, 0), intArrayOf(2, 0), intArrayOf(-1, -2), intArrayOf(2, 1)),
)

/** 모든 참가자가 같은 seed를 쓰면 같은 순서로 블록이 나온다. */
class SevenBag(seed: Long) {
    private val rng = Random(seed)
    private val queue = ArrayDeque<PieceType>()

    private fun refill() {
        val bag = PieceType.entries.toMutableList()
        bag.shuffle(rng)
        queue.addAll(bag)
    }

    fun next(): PieceType {
        if (queue.size <= 7) refill()
        return queue.removeFirst()
    }

    fun peek(n: Int): List<PieceType> {
        while (queue.size < n + 7) refill()
        return (0 until n).map { queue.elementAt(it) }
    }
}

/** 미리보기(HOLD/NEXT)처럼 보드 밖에서 조각 모양이 필요할 때 쓴다. */
fun pieceCells(type: PieceType, rot: Int = 0): Array<IntArray> {
    val s = SHAPES.getValue(type)[((rot % 4) + 4) % 4]
    return Array(4) { intArrayOf(s[it][0], s[it][1]) }
}

enum class Action { LEFT, RIGHT, ROTATE_CW, ROTATE_CCW, SOFT_DROP, HARD_DROP, HOLD }

/** 블록 하나를 굳혔을 때 무슨 일이 있었는지. UI 연출과 공격 전송에 쓴다. */
data class LockResult(
    val linesCleared: Int,
    val combo: Int,
    val attackSent: Int,
    val garbageCancelled: Int,
    val perfectClear: Boolean,
)

class TetrisEngine(seed: Long) {

    val board: Array<IntArray> = Array(BOARD_H) { IntArray(BOARD_W) }
    private val bag = SevenBag(seed)
    private val garbageRng = Random(seed xor 0x5DEECE66DL)

    var type: PieceType = bag.next(); private set
    var rot: Int = 0; private set
    var px: Int = 3; private set
    var py: Int = 1; private set

    var hold: PieceType? = null; private set
    private var holdUsed = false

    var score: Long = 0; private set
    var lines: Int = 0; private set
    var combo: Int = 0; private set
    var dead: Boolean = false; private set

    /** 아직 바닥에 붙지 않고 대기 중인 방해 줄. 줄을 지우면 여기서부터 상쇄된다. */
    var pendingGarbage: Int = 0; private set

    private var gravityAcc = 0L
    private var lockAcc = 0L
    private var lockResets = 0
    private var onGround = false

    val level: Int get() = lines / 10 + 1
    /** 한 칸 떨어지는 데 걸리는 시간. 화면의 달리는 강아지도 이 속도에 맞춰 뛴다. */
    val gravityMs: Long get() = max(50L, 800L - (level - 1) * 65L)

    fun nextQueue(n: Int): List<PieceType> = bag.peek(n)

    fun cells(t: PieceType = type, r: Int = rot, ox: Int = px, oy: Int = py): Array<IntArray> {
        val s = SHAPES.getValue(t)[((r % 4) + 4) % 4]
        return Array(4) { intArrayOf(ox + s[it][0], oy + s[it][1]) }
    }

    private fun collides(t: PieceType, r: Int, ox: Int, oy: Int): Boolean {
        for (c in cells(t, r, ox, oy)) {
            val x = c[0]
            val y = c[1]
            if (x < 0 || x >= BOARD_W || y >= BOARD_H) return true
            if (y >= 0 && board[y][x] != CELL_EMPTY) return true
        }
        return false
    }

    private fun touchLockReset() {
        if (lockResets < 15) {
            lockAcc = 0
            lockResets++
        }
    }

    fun input(a: Action): LockResult? {
        if (dead) return null
        when (a) {
            Action.LEFT -> if (!collides(type, rot, px - 1, py)) { px--; touchLockReset() }
            Action.RIGHT -> if (!collides(type, rot, px + 1, py)) { px++; touchLockReset() }
            Action.ROTATE_CW -> rotate(1)
            Action.ROTATE_CCW -> rotate(-1)
            Action.HOLD -> doHold()
            // 소프트드롭은 딱 한 칸이다. 하드드롭처럼 바닥까지 가면 안 된다.
            Action.SOFT_DROP -> {
                if (!collides(type, rot, px, py + 1)) {
                    py++
                    score += 1
                    gravityAcc = 0
                    lockAcc = 0
                    onGround = false
                }
            }

            Action.HARD_DROP -> {
                var dropped = 0
                while (!collides(type, rot, px, py + 1)) { py++; dropped++ }
                score += dropped * 2L
                return lockPiece()
            }
        }
        return null
    }

    private fun rotate(dir: Int) {
        if (type == PieceType.O) return
        val target = ((rot + dir) % 4 + 4) % 4
        val table = if (type == PieceType.I) KICKS_I else KICKS_JLSTZ
        val kicks = table[rot * 4 + target] ?: return
        for (k in kicks) {
            if (!collides(type, target, px + k[0], py + k[1])) {
                rot = target
                px += k[0]
                py += k[1]
                touchLockReset()
                return
            }
        }
    }

    private fun doHold() {
        if (holdUsed) return
        holdUsed = true
        val swap = hold
        hold = type
        spawn(swap ?: bag.next())
    }

    private fun spawn(t: PieceType) {
        type = t
        rot = 0
        px = 3
        py = 1
        gravityAcc = 0
        lockAcc = 0
        lockResets = 0
        onGround = false
        if (collides(type, rot, px, py)) dead = true
    }

    /** dtMs만큼 시간을 흘려보낸다. 블록이 굳었으면 그 결과를 돌려준다. */
    fun update(dtMs: Long): LockResult? {
        if (dead) return null
        val step = gravityMs
        gravityAcc += dtMs
        var result: LockResult? = null
        while (gravityAcc >= step && result == null) {
            gravityAcc -= step
            if (!collides(type, rot, px, py + 1)) {
                py++
                onGround = false
                lockAcc = 0
            } else {
                onGround = true
            }
        }
        if (onGround && collides(type, rot, px, py + 1)) {
            lockAcc += dtMs
            if (lockAcc >= 500L) result = lockPiece()
        }
        return result
    }

    private fun lockPiece(): LockResult {
        var allHidden = true
        for (c in cells()) {
            val x = c[0]
            val y = c[1]
            if (y in 0 until BOARD_H) {
                board[y][x] = type.ordinal + 1
                if (y >= HIDDEN_H) allHidden = false
            }
        }
        val cleared = clearLines()

        if (cleared > 0) combo++ else combo = 0
        score += when (cleared) {
            1 -> 100L
            2 -> 300L
            3 -> 500L
            4 -> 800L
            else -> 0L
        } * level
        if (combo > 1) score += 50L * (combo - 1) * level
        lines += cleared

        val perfect = cleared > 0 && board.all { row -> row.all { it == CELL_EMPTY } }

        // 공격 규칙: 2줄 이상 지웠을 때, 그리고 3콤보 이상일 때 방해 줄을 보낸다.
        var attack = 0
        if (cleared >= 2) attack += when (cleared) { 2 -> 1; 3 -> 2; else -> 4 }
        if (combo >= 3) attack += comboBonus(combo)
        if (perfect) attack += 6

        // 내가 보낼 공격으로, 들어와 있던 방해 줄을 먼저 상쇄한다.
        var cancelled = 0
        if (attack > 0 && pendingGarbage > 0) {
            cancelled = minOf(attack, pendingGarbage)
            attack -= cancelled
            pendingGarbage -= cancelled
        }
        // 줄을 못 지웠으면 대기 중이던 방해 줄이 바닥에서 올라온다.
        if (cleared == 0 && pendingGarbage > 0) {
            applyGarbage(pendingGarbage)
            pendingGarbage = 0
        }

        if (allHidden) dead = true
        holdUsed = false
        spawn(bag.next())
        return LockResult(cleared, combo, attack, cancelled, perfect)
    }

    private fun comboBonus(c: Int): Int = when {
        c >= 10 -> 4
        c >= 7 -> 3
        c >= 5 -> 2
        else -> 1
    }

    private fun clearLines(): Int {
        var cleared = 0
        var y = BOARD_H - 1
        while (y >= 0) {
            if (board[y].all { it != CELL_EMPTY }) {
                for (yy in y downTo 1) board[yy] = board[yy - 1].copyOf()
                board[0] = IntArray(BOARD_W)
                cleared++
            } else {
                y--
            }
        }
        return cleared
    }

    fun receiveGarbage(n: Int) {
        if (dead || n <= 0) return
        pendingGarbage += n
    }

    private fun applyGarbage(n: Int) {
        val hole = garbageRng.nextInt(BOARD_W)
        repeat(n) {
            for (y in 0 until BOARD_H - 1) board[y] = board[y + 1]
            board[BOARD_H - 1] = IntArray(BOARD_W) { if (it == hole) CELL_EMPTY else CELL_GARBAGE }
        }
        // 밀려 올라온 블록이 현재 조각과 겹치면 조각을 위로 띄운다.
        while (collides(type, rot, px, py) && py > -2) py--
        if (collides(type, rot, px, py)) dead = true
    }

    /** 고스트(착지 예상 위치)의 y좌표. */
    fun ghostY(): Int {
        var y = py
        while (!collides(type, rot, px, y + 1)) y++
        return y
    }

    fun kill() {
        dead = true
    }

    /** 상대에게 보낼 보드 스냅샷. 보이는 20줄 × 10칸을 숫자 한 글자씩, 현재 조각까지 얹어 200자로. */
    fun snapshot(): String {
        val sb = StringBuilder(VISIBLE_H * BOARD_W)
        val overlay = HashMap<Int, Int>()
        if (!dead) {
            for (c in cells()) {
                val x = c[0]
                val y = c[1]
                if (y >= HIDDEN_H && y < BOARD_H && x in 0 until BOARD_W) {
                    overlay[(y - HIDDEN_H) * BOARD_W + x] = type.ordinal + 1
                }
            }
        }
        for (y in HIDDEN_H until BOARD_H) {
            for (x in 0 until BOARD_W) {
                val idx = (y - HIDDEN_H) * BOARD_W + x
                sb.append(overlay[idx] ?: board[y][x])
            }
        }
        return sb.toString()
    }
}
