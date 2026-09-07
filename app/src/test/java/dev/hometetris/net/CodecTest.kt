package dev.hometetris.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 보드 스냅샷과 공격이 그대로 왕복하는지. 여기가 어긋나면 상대 화면이 깨진다. */
class CodecTest {

    @Test
    fun serverMessagesRoundTrip() {
        val board = (0 until 200).joinToString("") { (it % 9).toString() }
        val messages = listOf(
            ServerMsg.Welcome(2),
            ServerMsg.Lobby(
                listOf(PlayerInfo(0, "아빠", true), PlayerInfo(1, "딸", false))
            ),
            ServerMsg.Start(-98765432101234L, 3000),
            ServerMsg.World(
                listOf(PlayerState(0, "아빠", board, 12345, 42, 3, true))
            ),
            ServerMsg.Garbage(4, "엄마"),
            ServerMsg.Over(listOf(Standing(1, "딸", 9000, 30), Standing(2, "아빠", 100, 2))),
            ServerMsg.Bye("방장이 방을 닫았습니다"),
        )
        for (m in messages) {
            val line = Codec.encode(m)
            assertTrue("메시지는 한 줄이어야 한다: $line", '\n' !in line)
            assertEquals(m, Codec.decodeServer(line))
        }
    }

    @Test
    fun clientMessagesRoundTrip() {
        val board = "0".repeat(199) + "7"
        val messages = listOf(
            ClientMsg.Join("막내"),
            ClientMsg.State(board, 777, 12, 5, true),
            ClientMsg.State(board, 0, 0, 0, false),
            ClientMsg.Attack(4),
            ClientMsg.Dead,
        )
        for (m in messages) {
            assertEquals(m, Codec.decodeClient(Codec.encode(m)))
        }
    }

    /** 이름에 따옴표나 이모지가 들어가도 깨지지 않아야 한다. */
    @Test
    fun namesWithAwkwardCharactersSurvive() {
        val name = "\"큰형\" \\ 😀"
        val decoded = Codec.decodeClient(Codec.encode(ClientMsg.Join(name))) as ClientMsg.Join
        assertEquals(name, decoded.name)
    }

    /** 쓰레기 입력이 들어와도 예외 대신 null 이어야 한다. */
    @Test
    fun garbageInputIsIgnored() {
        listOf("", "{", "not json", """{"t":"모르는타입"}""").forEach {
            assertEquals(null, Codec.decodeServer(it))
            assertEquals(null, Codec.decodeClient(it))
        }
    }
}
