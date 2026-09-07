package dev.junyj.lantetris.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TetrisEngineTest {

    /** 같은 seed면 블록 순서가 완전히 같아야 대결이 공평하다. */
    @Test
    fun sameSeedGivesSameSequence() {
        val a = SevenBag(12345L)
        val b = SevenBag(12345L)
        val seqA = (1..50).map { a.next() }
        val seqB = (1..50).map { b.next() }
        assertEquals(seqA, seqB)
    }

    /** 7-bag: 연속한 7개 안에 7종류가 정확히 한 번씩 나와야 한다. */
    @Test
    fun bagContainsEachPieceOncePerSeven() {
        val bag = SevenBag(999L)
        repeat(20) {
            val seven = (1..7).map { bag.next() }
            assertEquals(7, seven.toSet().size)
        }
    }

    @Test
    fun peekDoesNotConsume() {
        val bag = SevenBag(7L)
        val peeked = bag.peek(5)
        val taken = (1..5).map { bag.next() }
        assertEquals(peeked, taken)
    }

    /** 한 줄을 가득 채우면 지워지고 점수가 오른다. */
    @Test
    fun fullRowIsCleared() {
        val e = TetrisEngine(1L)
        val bottom = BOARD_H - 1
        for (x in 0 until BOARD_W) e.board[bottom][x] = 8
        val before = e.lines
        // clearLines는 lockPiece 안에서만 도니, 조각을 굳혀서 확인한다.
        e.input(Action.HARD_DROP)
        assertTrue("가득 찬 줄이 지워져야 한다", e.lines > before)
    }

    /** 방해 줄은 바로 안 쌓이고 대기했다가, 줄을 못 지운 다음 턴에 올라온다. */
    @Test
    fun garbageIsQueuedThenApplied() {
        val e = TetrisEngine(2L)
        e.receiveGarbage(3)
        assertEquals(3, e.pendingGarbage)

        // 줄을 못 지우는 하드드롭 한 번 -> 방해 줄이 실제로 바닥에 쌓인다
        e.input(Action.HARD_DROP)
        assertEquals(0, e.pendingGarbage)

        val garbageRows = e.board.count { row -> row.any { it == CELL_GARBAGE } }
        assertEquals(3, garbageRows)
        // 방해 줄에는 구멍이 정확히 한 칸 있어야 한다
        e.board.filter { row -> row.any { it == CELL_GARBAGE } }.forEach { row ->
            assertEquals(1, row.count { it == CELL_EMPTY })
        }
    }

    /** 방해 줄 구멍은 한 묶음 안에서 같은 열이어야 한다(연달아 지우기 가능하게). */
    @Test
    fun garbageHolesLineUp() {
        val e = TetrisEngine(3L)
        e.receiveGarbage(4)
        e.input(Action.HARD_DROP)
        val holes = e.board
            .filter { row -> row.any { it == CELL_GARBAGE } }
            .map { row -> row.indexOfFirst { it == CELL_EMPTY } }
        assertEquals(1, holes.toSet().size)
    }

    /** 2줄 이상 지우면 공격이 나가고, 1줄은 나가지 않는다. */
    @Test
    fun attackRulesFollowTheHouseRules() {
        assertEquals(0, attackFor(cleared = 1, combo = 1))
        assertEquals(1, attackFor(cleared = 2, combo = 1))
        assertEquals(2, attackFor(cleared = 3, combo = 1))
        assertEquals(4, attackFor(cleared = 4, combo = 1))
        // 1줄이라도 3콤보부터는 공격이 나간다
        assertEquals(1, attackFor(cleared = 1, combo = 3))
        assertEquals(2, attackFor(cleared = 1, combo = 5))
        // 2줄 + 3콤보는 합산
        assertEquals(2, attackFor(cleared = 2, combo = 3))
    }

    /** 실제 엔진에서 2줄 클리어가 공격 1을 만들어내는지. */
    @Test
    fun doubleClearSendsOneLine() {
        val e = TetrisEngine(4L)
        bringUpIPiece(e)
        fillBottomRowsExceptRightColumn(e, 2)
        val r = dropIPieceIntoRightWell(e)
        assertEquals(2, r.linesCleared)
        assertEquals(1, r.attackSent)
    }

    /** 들어와 있던 방해 줄은 내 공격으로 먼저 상쇄된다. */
    @Test
    fun outgoingAttackCancelsPendingGarbage() {
        val e = TetrisEngine(5L)
        bringUpIPiece(e)
        fillBottomRowsExceptRightColumn(e, 2)
        // 방해 줄은 반드시 I를 손에 쥔 뒤에 받아야 한다.
        // 그 전에 받으면 중간에 떨어뜨린 조각들이 먼저 소진해 버린다.
        e.receiveGarbage(1)
        val r = dropIPieceIntoRightWell(e)
        assertEquals(2, r.linesCleared)
        assertEquals(1, r.garbageCancelled)
        assertEquals(0, r.attackSent)
        assertEquals(0, e.pendingGarbage)
    }

    /** 소프트드롭은 딱 한 칸만 내려간다. 하드드롭처럼 바닥까지 가면 안 된다. */
    @Test
    fun softDropMovesExactlyOneRow() {
        val e = TetrisEngine(11L)
        val before = e.py
        e.input(Action.SOFT_DROP)
        assertEquals("한 번에 한 칸", before + 1, e.py)
        e.input(Action.SOFT_DROP)
        assertEquals(before + 2, e.py)
        // 조각이 굳지 않아야 한다 - 굳었다면 새 조각이 나와 py가 처음으로 돌아갔을 것이다
        assertTrue(e.py > before)
    }

    /** 소프트드롭 한 칸마다 1점. */
    @Test
    fun softDropScoresOnePerRow() {
        val e = TetrisEngine(12L)
        val before = e.score
        repeat(5) { e.input(Action.SOFT_DROP) }
        assertEquals(before + 5, e.score)
    }

    /** 바닥에 닿았으면 소프트드롭을 눌러도 그대로다(굳혀 버리면 안 된다). */
    @Test
    fun softDropAtBottomDoesNothing() {
        val e = TetrisEngine(13L)
        while (e.ghostY() != e.py) e.input(Action.SOFT_DROP)
        val restingY = e.py
        val restingScore = e.score
        e.input(Action.SOFT_DROP)
        assertEquals(restingY, e.py)
        assertEquals(restingScore, e.score)
    }

    /** 하드드롭은 여전히 바닥까지 내려가서 굳는다. 둘이 같은 동작이면 안 된다. */
    @Test
    fun hardDropStillLocksImmediately() {
        val e = TetrisEngine(14L)
        val r = e.input(Action.HARD_DROP)
        assertTrue("하드드롭은 조각을 굳힌다", r != null)
        assertTrue("바닥에 블록이 쌓여야 한다", e.board.any { row -> row.any { it != CELL_EMPTY } })
    }

    /**
     * 조각을 하나 쓸 때마다 NEXT 목록이 한 칸씩 당겨져야 한다.
     * 화면에서 "NEXT가 처음 것만 계속 나온다"는 제보를 받고, 엔진 문제인지 가리려고 넣은 테스트다.
     * (엔진은 정상이었고 원인은 화면 갱신 쪽이었다.)
     */
    @Test
    fun nextQueueAdvancesWhenAPieceIsUsed() {
        val e = TetrisEngine(42L)
        val before = e.nextQueue(4)

        e.input(Action.HARD_DROP)
        val after = e.nextQueue(4)

        assertEquals("한 칸 당겨져야 한다", before.drop(1), after.take(3))
        assertTrue("목록이 그대로면 안 된다", before != after)
    }

    /** 여러 개를 연달아 써도 계속 당겨져야 한다. */
    @Test
    fun nextQueueKeepsAdvancing() {
        val e = TetrisEngine(7L)
        val seen = mutableListOf<List<PieceType>>()
        repeat(8) {
            seen.add(e.nextQueue(4))
            e.input(Action.HARD_DROP)
        }
        assertTrue("8번 두는 동안 목록이 여러 번 달라져야 한다", seen.toSet().size >= 7)
    }

    /** 홀드가 비어 있으면 NEXT 에서 꺼내 온다. */
    @Test
    fun firstHoldPullsFromNextQueue() {
        val e = TetrisEngine(99L)
        val firstPiece = e.type
        val firstNext = e.nextQueue(1).first()

        e.input(Action.HOLD)

        assertEquals("들고 있던 조각이 홀드로 들어간다", firstPiece, e.hold)
        assertEquals("홀드가 비었으면 NEXT 에서 꺼내 온다", firstNext, e.type)
    }

    /**
     * 홀드에 이미 조각이 있으면 **맞바꿔야** 한다. 그냥 건너뛰기가 되면 안 된다.
     * ("HOLD가 스킵 기능밖에 안 된다"는 제보를 받고 엔진인지 화면인지 가리려고 넣었다.)
     */
    @Test
    fun secondHoldSwapsWithStoredPiece() {
        val e = TetrisEngine(5L)
        val stored = e.type
        e.input(Action.HOLD)                 // 홀드에 stored 를 넣는다
        assertEquals(stored, e.hold)

        e.input(Action.HARD_DROP)            // 한 조각 굳혀서 홀드를 다시 쓸 수 있게
        val current = e.type

        e.input(Action.HOLD)                 // 여기서 맞바꿔야 한다

        assertEquals("들고 있던 게 홀드로 들어간다", current, e.hold)
        assertEquals("홀드에 있던 게 손으로 나온다", stored, e.type)
    }

    /** 한 조각에 홀드는 한 번만. 연타로 조각을 계속 넘길 수 없어야 한다. */
    @Test
    fun holdCanNotBeUsedTwiceOnTheSamePiece() {
        val e = TetrisEngine(3L)
        e.input(Action.HOLD)
        val afterFirst = e.type
        val heldAfterFirst = e.hold

        e.input(Action.HOLD)
        e.input(Action.HOLD)

        assertEquals("두 번째 홀드는 무시된다", afterFirst, e.type)
        assertEquals(heldAfterFirst, e.hold)
    }

    /** 스냅샷은 보이는 20 x 10 = 200자여야 한다. */
    @Test
    fun snapshotIsFixedLength() {
        val e = TetrisEngine(6L)
        assertEquals(VISIBLE_H * BOARD_W, e.snapshot().length)
        assertTrue(e.snapshot().all { it in '0'..'8' })
    }

    /** 벽에 붙어서도 회전이 되어야 한다(SRS 월킥). */
    @Test
    fun wallKickLetsPieceRotateAgainstLeftWall() {
        val e = TetrisEngine(7L)
        repeat(6) { e.input(Action.LEFT) }
        val xBefore = e.px
        e.input(Action.ROTATE_CW)
        e.input(Action.ROTATE_CW)
        assertTrue("왼쪽 벽에 붙어도 회전이 되어야 한다", e.px >= 0)
        assertFalse(e.dead)
        assertTrue(xBefore >= 0)
    }

    /** 고스트는 항상 현재 조각과 같거나 아래에 있고, 보드 안이어야 한다. */
    @Test
    fun ghostStaysBelowAndInsideBoard() {
        val e = TetrisEngine(8L)
        repeat(30) {
            e.update(60)
            assertTrue(e.ghostY() >= e.py)
            for (c in e.cells(oy = e.ghostY())) assertTrue(c[1] < BOARD_H)
        }
    }

    // ---- 도우미 ----

    /** 우리 집 규칙을 테스트에서 다시 계산해 본 것. 엔진과 값이 어긋나면 규칙이 바뀐 것. */
    private fun attackFor(cleared: Int, combo: Int): Int {
        var a = 0
        if (cleared >= 2) a += when (cleared) { 2 -> 1; 3 -> 2; else -> 4 }
        if (combo >= 3) a += when {
            combo >= 10 -> 4
            combo >= 7 -> 3
            combo >= 5 -> 2
            else -> 1
        }
        return a
    }

    /** 현재 조각이 I가 될 때까지, 다른 조각은 왼쪽 끝에 치워 둔다. */
    private fun bringUpIPiece(e: TetrisEngine) {
        var guard = 0
        while (e.type != PieceType.I && guard++ < 20) {
            repeat(5) { e.input(Action.LEFT) }
            e.input(Action.HARD_DROP)
        }
        assertEquals(PieceType.I, e.type)
    }

    /** 맨 아래 [rows]줄을 오른쪽 한 칸만 빼고 채운다. 세로 I 하나로 지울 수 있는 상태. */
    private fun fillBottomRowsExceptRightColumn(e: TetrisEngine, rows: Int) {
        for (y in BOARD_H - rows until BOARD_H) {
            for (x in 0 until BOARD_W - 1) e.board[y][x] = 8
            e.board[y][BOARD_W - 1] = CELL_EMPTY
        }
    }

    /** 오른쪽 한 칸 우물에 세로 I를 떨어뜨려 줄을 지운다. */
    private fun dropIPieceIntoRightWell(e: TetrisEngine): LockResult {
        e.input(Action.ROTATE_CW)
        repeat(8) { e.input(Action.RIGHT) }
        return e.input(Action.HARD_DROP)!!
    }
}
