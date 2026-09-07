package dev.junyj.lantetris.net

import kotlinx.coroutines.flow.SharedFlow

/**
 * 로컬 플레이어가 게임 서버와 주고받는 통로.
 * 호스트든 참가자든 UI 쪽에서는 똑같이 쓴다 - 호스트는 자기 자신에게 붙는 셈.
 */
interface Session {
    val incoming: SharedFlow<ServerMsg>
    val isHost: Boolean

    fun send(m: ClientMsg)

    /** 호스트만 의미가 있다. 로비에서 게임을 시작한다. */
    fun startGame()

    fun close()
}
