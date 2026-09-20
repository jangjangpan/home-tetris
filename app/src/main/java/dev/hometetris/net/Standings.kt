package dev.hometetris.net

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
/**
 * 판이 끝났는지. **조건은 오직 "몇 명이 살아 있는가" 하나뿐이다.**
 * 점수나 지운 줄로는 절대 끝나지 않는다 - 마지막 한 명이 남을 때까지 계속한다.
 * (사용자가 "점수가 일정 수준 도달해서 끝나는 것 같다"고 해서 여기 한 곳으로 모아 뒀다.
 *  그런 조건은 원래도 없었고, 앞으로도 넣지 말 것.)
 *
 * @param joined 방에 들어와 있는 사람 수
 * @param alive  그중 아직 살아 있는 사람 수
 */
fun isMatchOver(joined: Int, alive: Int): Boolean {
    // 혼자면 내가 죽어야 끝, 둘 이상이면 한 명 남으면 끝.
    val threshold = if (joined <= 1) 0 else 1
    return alive <= threshold
}

fun standingsOf(players: List<FinishState>): List<Standing> =
    players
        .sortedWith(compareByDescending<FinishState> { it.alive }.thenByDescending { it.deathOrder })
        .mapIndexed { i, p -> Standing(i + 1, p.name, p.score, p.lines) }
