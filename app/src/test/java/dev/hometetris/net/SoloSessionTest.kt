package dev.hometetris.net

import kotlinx.coroutines.ExperimentalCoroutinesApi
import dev.hometetris.core.ItemTarget
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 싱글 방은 만들어지자마자 스스로 시작한다. 그래서 **고른 모드가 생성자에 실려야** 한다.
 * (실제로 여기서 한 번 틀렸다. 화면에서 아이템을 골라도 노멀로 시작했다.)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SoloSessionTest {

    @Test
    fun autoStartUsesTheChosenMode() = runTest {
        assertEquals(GameMode.ITEM, firstStart(GameMode.ITEM).mode)
        assertEquals(GameMode.NORMAL, firstStart(GameMode.NORMAL).mode)
    }

    @Test
    fun startingAnotherRoundKeepsTheMode() = runTest {
        val seen = mutableListOf<ServerMsg.Start>()
        val session = SoloSession("나", 1, backgroundScope, GameMode.ITEM)
        backgroundScope.launch {
            session.incoming.collect { if (it is ServerMsg.Start) seen += it }
        }
        runCurrent()

        session.startGame(GameMode.ITEM) // "한 판 더"
        runCurrent()
        session.close()

        assertEquals("두 판 다 시작 메시지가 와야 한다", 2, seen.size)
        seen.forEach { assertEquals(GameMode.ITEM, it.mode) }
    }

    /** 씨앗은 판마다 달라야 한다 - 같은 판을 또 하면 재미가 없다. */
    @Test
    fun eachRoundGetsItsOwnSeed() = runTest {
        val seen = mutableListOf<ServerMsg.Start>()
        val session = SoloSession("나", 3, backgroundScope, GameMode.NORMAL)
        backgroundScope.launch {
            session.incoming.collect { if (it is ServerMsg.Start) seen += it }
        }
        runCurrent()
        session.startGame(GameMode.NORMAL)
        runCurrent()
        session.close()

        assertEquals(2, seen.size)
        assertEquals("씨앗이 겹치면 안 된다", 2, seen.map { it.seed }.toSet().size)
    }

    private fun TestScope.firstStart(mode: GameMode): ServerMsg.Start {
        var start: ServerMsg.Start? = null
        val session = SoloSession("나", 1, backgroundScope, mode)
        backgroundScope.launch {
            session.incoming.collect { if (it is ServerMsg.Start && start == null) start = it }
        }
        runCurrent()
        session.close()
        return requireNotNull(start) { "시작 메시지가 오지 않았다" }
    }

    /** 아이템 모드에서는 컴퓨터가 줄을 지우다 방해 아이템을 날려야 한다. */
    @Test
    fun botSendsItemsInItemMode() = runTest {
        val seen = collectFor(GameMode.ITEM, 90_000)
        val hits = seen.filterIsInstance<ServerMsg.ItemHit>()
        assertTrue("컴퓨터가 방해 아이템을 한 번은 써야 한다 (받은 메시지 ${seen.size}개)", hits.isNotEmpty())
        hits.forEach { assertEquals(ItemTarget.ENEMY, it.kind.target) }
    }

    /** 노멀 모드에서는 아이템이 아예 날아오면 안 된다. */
    @Test
    fun botNeverSendsItemsInNormalMode() = runTest {
        val seen = collectFor(GameMode.NORMAL, 90_000)
        assertTrue("노멀 모드인데 아이템이 날아왔다", seen.none { it is ServerMsg.ItemHit })
        assertTrue("대신 방해 줄은 와야 한다", seen.any { it is ServerMsg.Garbage })
    }

    /** 가상 시간으로 [ms] 만큼 한 판 돌려 보고 그동안 온 메시지를 모은다. */
    private fun TestScope.collectFor(mode: GameMode, ms: Long): List<ServerMsg> {
        val seen = mutableListOf<ServerMsg>()
        // 컴퓨터의 시계를 가상 시간에 물려 한 판을 눈 깜짝할 새에 돌린다.
        val session = SoloSession("나", 10, backgroundScope, mode) { testScheduler.currentTime * 1_000_000 }
        backgroundScope.launch { session.incoming.collect { seen += it } }
        runCurrent()
        advanceTimeBy(ms)
        session.close()
        runCurrent()
        return seen.toList()
    }
}
