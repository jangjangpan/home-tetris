package dev.junyj.lantetris.net

/** 등수를 매길 때 필요한 것만 추린 참가자 상태. */
data class FinishState(
    val name: String,
    val alive: Boolean,
    /** 몇 번째로 죽었는지. 살아 있으면 0. */
    val deathOrder: Int,
    val score: Long,
    val lines: Int,
)

/**
 * 배틀로얄 규칙: **점수가 아니라 오래 버틴 순서**로 등수를 매긴다.
 * 살아남은 사람이 1등이고, 그다음은 나중에 죽은 사람 순이다.
 * 점수가 높아도 먼저 죽었으면 아래 등수가 된다.
 *
 * 싱글과 멀티가 같은 규칙을 써야 해서 여기 한 곳에 둔다.
 */
fun standingsOf(players: List<FinishState>): List<Standing> =
    players
        .sortedWith(compareByDescending<FinishState> { it.alive }.thenByDescending { it.deathOrder })
        .mapIndexed { i, p -> Standing(i + 1, p.name, p.score, p.lines) }
