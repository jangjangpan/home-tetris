package dev.hometetris.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 아이템 모드 규칙. 노멀 모드가 아이템에 물들지 않는지도 같이 지킨다. */
class ItemModeTest {

    /** 노멀 모드에서는 줄을 아무리 지워도 아이템이 안 생긴다. */
    @Test
    fun normalModeNeverGrantsItems() {
        val e = TetrisEngine(1L, itemMode = false)
        repeat(10) {
            clearFourLines(e)
            assertNull("노멀 모드에 아이템이 생기면 안 된다", e.item)
        }
    }

    /** 4줄을 지우면(확률 100%) 아이템이 반드시 들어온다. */
    @Test
    fun tetrisAlwaysGrantsAnItem() {
        val e = TetrisEngine(2L, itemMode = true)
        val r = clearFourLines(e)
        assertEquals(4, r.linesCleared)
        assertNotNull("4줄을 지웠으면 아이템이 나와야 한다", e.item)
    }

    /** 슬롯은 하나뿐이다. 들고 있으면 더 안 들어온다. */
    @Test
    fun slotHoldsOnlyOneItem() {
        val e = TetrisEngine(3L, itemMode = true)
        clearFourLines(e)
        val held = e.item
        assertNotNull(held)
        repeat(5) { clearFourLines(e) }
        assertEquals("들고 있는 동안에는 바뀌지 않는다", held, e.item)
    }

    /** 아이템을 꺼내면 슬롯이 빈다. */
    @Test
    fun takingItemEmptiesTheSlot() {
        val e = TetrisEngine(4L, itemMode = true)
        clearFourLines(e)
        assertNotNull(e.takeItem())
        assertNull("꺼내면 비어야 한다", e.item)
        assertNull("빈 슬롯에서 또 꺼낼 수는 없다", e.takeItem())
    }

    /** 청소를 쓰면 내 판 맨 아래 두 줄이 사라진다. */
    @Test
    fun cleanRemovesTwoBottomRows() {
        val e = TetrisEngine(5L, itemMode = true)
        holdUntilItemIs(e, ItemKind.CLEAN)

        wipe(e)
        for (y in BOARD_H - 3 until BOARD_H) for (x in 0 until BOARD_W) e.board[y][x] = 8

        e.takeItem()

        assertEquals("세 줄 중 한 줄만 남는다", 1, filledRows(e))
        assertTrue("남은 줄은 맨 아래로 내려와 있다", e.board[BOARD_H - 1].all { it != CELL_EMPTY })
    }

    /** 폭탄을 쓰면 아래쪽이 듬성듬성 날아간다 - 다 지우지는 않는다. */
    @Test
    fun bombBlastsSomeCellsButNotAll() {
        val e = TetrisEngine(6L, itemMode = true)
        holdUntilItemIs(e, ItemKind.BOMB)

        wipe(e)
        for (y in BOARD_H - 4 until BOARD_H) for (x in 0 until BOARD_W) e.board[y][x] = 8
        val before = filledCells(e)

        e.takeItem()
        val after = filledCells(e)

        assertTrue("일부는 날아가야 한다 ($before -> $after)", after < before)
        assertTrue("전부 날아가면 안 된다", after > 0)
    }

    /** 청소·폭탄은 내 것이므로 상대에게서 날아와도 무시한다. */
    @Test
    fun selfItemsArrivingFromEnemyAreIgnored() {
        val e = TetrisEngine(7L, itemMode = true)
        for (y in BOARD_H - 3 until BOARD_H) for (x in 0 until BOARD_W) e.board[y][x] = 8
        val before = filledCells(e)

        e.receiveItem(ItemKind.CLEAN)
        e.receiveItem(ItemKind.BOMB)

        assertEquals("남이 보낸 내 이득 아이템은 내 판을 건드리지 않는다", before, filledCells(e))
    }

    /** 회전금지를 맞으면 회전이 안 되고, 시간이 지나면 풀린다. */
    @Test
    fun noRotateBlocksRotationThenExpires() {
        val e = TetrisEngine(8L, itemMode = true)
        bringUpRotatablePiece(e)
        val before = e.rot

        e.receiveItem(ItemKind.NO_ROTATE)
        e.input(Action.ROTATE_CW)
        assertEquals("걸린 동안에는 회전이 막힌다", before, e.rot)

        e.update(ItemDuration.NO_ROTATE_MS + 50)
        assertEquals("시간이 지나면 풀린다", 0L, e.noRotateMs)

        bringUpRotatablePiece(e)
        val nowRot = e.rot
        e.input(Action.ROTATE_CW)
        assertTrue("풀린 뒤에는 돈다", e.rot != nowRot)
    }

    /** 가속을 맞으면 낙하가 빨라지고, 끝나면 돌아온다. */
    @Test
    fun rushSpeedsUpGravityThenExpires() {
        val e = TetrisEngine(9L, itemMode = true)
        val normal = e.gravityMs

        e.receiveItem(ItemKind.RUSH)
        assertTrue("걸린 동안 더 빨리 떨어진다 (${e.gravityMs} < $normal)", e.gravityMs < normal)

        e.update(ItemDuration.RUSH_MS + 50)
        assertEquals("끝나면 원래 속도로", normal, e.gravityMs)
    }

    /** 안개는 시간이 지나면 걷힌다. */
    @Test
    fun fogExpires() {
        val e = TetrisEngine(10L, itemMode = true)
        e.receiveItem(ItemKind.FOG)
        assertTrue(e.fogMs > 0)
        e.update(ItemDuration.FOG_MS + 50)
        assertEquals(0L, e.fogMs)
    }

    /** 지진은 쌓인 줄을 좌우로 어긋나게 만든다. */
    @Test
    fun quakeShiftsStackedRows() {
        val e = TetrisEngine(11L, itemMode = true)
        for (x in 0 until 6) e.board[BOARD_H - 1][x] = 8
        val before = e.board[BOARD_H - 1].toList()

        e.receiveItem(ItemKind.QUAKE)

        assertTrue("줄이 밀려 모양이 달라져야 한다", before != e.board[BOARD_H - 1].toList())
    }

    /** 죽은 판에는 방해가 걸리지 않는다 - 관전 중에 효과가 남으면 곤란하다. */
    @Test
    fun deadPlayerIgnoresIncomingItems() {
        val e = TetrisEngine(12L, itemMode = true)
        e.kill()
        e.receiveItem(ItemKind.FOG)
        e.receiveItem(ItemKind.NO_ROTATE)
        e.receiveItem(ItemKind.RUSH)
        assertEquals(0L, e.fogMs)
        assertEquals(0L, e.noRotateMs)
        assertEquals(0L, e.rushMs)
    }

    /** 내 이득용과 방해용이 제대로 갈려 있어야 상대에게 잘못 날아가지 않는다. */
    @Test
    fun itemTargetsAreSplitCorrectly() {
        assertEquals(ItemTarget.SELF, ItemKind.CLEAN.target)
        assertEquals(ItemTarget.SELF, ItemKind.BOMB.target)
        listOf(ItemKind.QUAKE, ItemKind.FOG, ItemKind.NO_ROTATE, ItemKind.RUSH).forEach {
            assertEquals("${it.label} 은 상대용", ItemTarget.ENEMY, it.target)
        }
        assertEquals("방해 아이템은 넷", 4, ItemKind.enemyItems.size)
    }

    /** 이름으로 되찾을 수 있어야 네트워크로 주고받을 수 있다. */
    @Test
    fun itemsRoundTripByName() {
        ItemKind.entries.forEach { assertEquals(it, ItemKind.byName(it.name)) }
        assertNull(ItemKind.byName("없는아이템"))
    }

    /** 같은 씨앗이면 아이템도 같은 순서로 나온다 - 멀티에서 서로 다르면 시비가 붙는다. */
    @Test
    fun sameSeedGivesSameItems() {
        val a = TetrisEngine(777L, itemMode = true)
        val b = TetrisEngine(777L, itemMode = true)
        repeat(5) {
            clearFourLines(a)
            clearFourLines(b)
            assertEquals(a.item, b.item)
            a.takeItem()
            b.takeItem()
        }
    }

    /** 노멀 모드 엔진은 아이템 플래그가 꺼져 있다. */
    @Test
    fun modeFlagIsCarried() {
        assertFalse(TetrisEngine(13L).itemMode)
        assertTrue(TetrisEngine(13L, itemMode = true).itemMode)
    }

    // ---- 도우미 ----

    private fun wipe(e: TetrisEngine) {
        for (y in 0 until BOARD_H) e.board[y].fill(CELL_EMPTY)
    }

    private fun filledRows(e: TetrisEngine) = e.board.count { row -> row.any { it != CELL_EMPTY } }

    private fun filledCells(e: TetrisEngine) = e.board.sumOf { row -> row.count { it != CELL_EMPTY } }

    /** 판을 비우고 아래 네 줄을 오른쪽 한 칸만 빼고 채운 뒤, 세로 I로 한 번에 지운다. */
    private fun clearFourLines(e: TetrisEngine): LockResult {
        bringUpIPiece(e)
        wipe(e)
        for (y in BOARD_H - 4 until BOARD_H) {
            for (x in 0 until BOARD_W - 1) e.board[y][x] = 8
            e.board[y][BOARD_W - 1] = CELL_EMPTY
        }
        e.input(Action.ROTATE_CW)
        repeat(8) { e.input(Action.RIGHT) }
        return e.input(Action.HARD_DROP)!!
    }

    private fun bringUpIPiece(e: TetrisEngine) {
        var guard = 0
        while (e.type != PieceType.I && guard++ < 20) {
            wipe(e)
            repeat(5) { e.input(Action.LEFT) }
            e.input(Action.HARD_DROP)
        }
        assertEquals(PieceType.I, e.type)
    }

    /** O는 원래 회전하지 않으므로 회전 시험에는 다른 조각을 쓴다. */
    private fun bringUpRotatablePiece(e: TetrisEngine) {
        var guard = 0
        while (e.type == PieceType.O && guard++ < 20) {
            wipe(e)
            e.input(Action.HARD_DROP)
        }
        assertTrue(e.type != PieceType.O)
    }

    /** 원하는 아이템이 슬롯에 들어올 때까지 지우고, 아닌 것은 버린다. */
    private fun holdUntilItemIs(e: TetrisEngine, want: ItemKind) {
        var guard = 0
        while (e.item != want && guard++ < 200) {
            e.takeItem()
            clearFourLines(e)
        }
        assertEquals("${want.label} 이 나올 때까지 돌리지 못했다", want, e.item)
    }
}
