package dev.hometetris.net

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 등수는 점수가 아니라 **버틴 순서**로 매겨진다. 3인 이상에서만 드러나는 규칙이라
 * 손으로 확인하기 어려워서 여기에 고정해 둔다.
 */
class StandingsTest {

    private fun alive(name: String, score: Long = 0, lines: Int = 0) =
        FinishState(name, alive = true, deathOrder = 0, score = score, lines = lines)

    private fun died(name: String, order: Int, score: Long = 0, lines: Int = 0) =
        FinishState(name, alive = false, deathOrder = order, score = score, lines = lines)

    /** 실기기 3인 테스트에서 실제로 나온 상황. 점수가 높아도 먼저 죽으면 아래 등수다. */
    @Test
    fun lastSurvivorWinsRegardlessOfScore() {
        val s = standingsOf(
            listOf(
                died("플레이어", order = 1, score = 204),   // 먼저 죽음, 점수는 더 높다
                died("준녕이", order = 2, score = 106),     // 나중에 죽음
                alive("PC참가자", score = 6526),            // 끝까지 생존
            )
        )
        assertEquals(listOf("PC참가자", "준녕이", "플레이어"), s.map { it.name })
        assertEquals(listOf(1, 2, 3), s.map { it.rank })
    }

    /** 점수와 줄 수는 등수와 상관없이 그대로 실려 나가야 한다. */
    @Test
    fun scoreAndLinesArePreserved() {
        val s = standingsOf(listOf(alive("가", score = 10, lines = 2), died("나", 1, score = 999, lines = 30)))
        assertEquals(10L, s[0].score)
        assertEquals(2, s[0].lines)
        assertEquals(999L, s[1].score)
        assertEquals(30, s[1].lines)
    }

    /** 4인에서도 죽은 순서의 역순이어야 한다. */
    @Test
    fun fourPlayersRankByReverseDeathOrder() {
        val s = standingsOf(
            listOf(
                died("첫번째로죽음", 1),
                died("세번째로죽음", 3),
                alive("생존"),
                died("두번째로죽음", 2),
            )
        )
        assertEquals(
            listOf("생존", "세번째로죽음", "두번째로죽음", "첫번째로죽음"),
            s.map { it.name },
        )
    }

    /** 둘 다 죽은 경우(동시 사망 등)에도 나중에 죽은 쪽이 위다. */
    @Test
    fun whenEveryoneDiedTheLastOneRanksFirst() {
        val s = standingsOf(listOf(died("먼저", 1), died("나중", 2)))
        assertEquals(listOf("나중", "먼저"), s.map { it.name })
    }

    /** 혼자 있을 때도 1위 하나만 나온다. */
    @Test
    fun soloProducesSingleRank() {
        val s = standingsOf(listOf(died("혼자", 1, score = 50)))
        assertEquals(1, s.size)
        assertEquals(1, s[0].rank)
        assertEquals("혼자", s[0].name)
    }
}
