package dev.hometetris.net

import org.json.JSONArray
import org.json.JSONObject

/** 같은 Wi-Fi 안에서만 쓰는 프로토콜이라 인증은 없다. 줄바꿈으로 구분되는 JSON 한 줄이 메시지 하나. */

const val DISCOVERY_PORT = 45123
const val DEFAULT_GAME_PORT = 45124
const val MAX_PLAYERS = 4

const val DISCOVER_PING = "LANTETRIS?v1"
const val DISCOVER_PONG = "LANTETRIS!v1"

data class PlayerInfo(val id: Int, val name: String, val isHost: Boolean)

data class PlayerState(
    val id: Int,
    val name: String,
    val board: String,
    val score: Long,
    val lines: Int,
    val pending: Int,
    val alive: Boolean,
)

data class Standing(val rank: Int, val name: String, val score: Long, val lines: Int)

sealed interface ServerMsg {
    data class Welcome(val id: Int) : ServerMsg
    data class Lobby(val players: List<PlayerInfo>) : ServerMsg
    data class Start(val seed: Long, val countdownMs: Long) : ServerMsg
    data class World(val players: List<PlayerState>) : ServerMsg
    data class Garbage(val lines: Int, val from: String) : ServerMsg
    data class Over(val standings: List<Standing>) : ServerMsg
    data class Bye(val reason: String) : ServerMsg
}

sealed interface ClientMsg {
    data class Join(val name: String) : ClientMsg
    data class State(
        val board: String,
        val score: Long,
        val lines: Int,
        val pending: Int,
        val alive: Boolean,
    ) : ClientMsg

    data class Attack(val lines: Int) : ClientMsg
    data object Dead : ClientMsg
}

object Codec {

    fun encode(m: ServerMsg): String = when (m) {
        is ServerMsg.Welcome -> JSONObject().put("t", "welcome").put("id", m.id)
        is ServerMsg.Lobby -> JSONObject().put("t", "lobby").put(
            "p",
            JSONArray().apply {
                m.players.forEach {
                    put(JSONObject().put("id", it.id).put("n", it.name).put("h", it.isHost))
                }
            }
        )

        is ServerMsg.Start -> JSONObject().put("t", "start").put("seed", m.seed).put("cd", m.countdownMs)
        is ServerMsg.World -> JSONObject().put("t", "world").put(
            "p",
            JSONArray().apply {
                m.players.forEach {
                    put(
                        JSONObject()
                            .put("id", it.id).put("n", it.name).put("b", it.board)
                            .put("sc", it.score).put("ln", it.lines)
                            .put("pg", it.pending).put("a", it.alive)
                    )
                }
            }
        )

        is ServerMsg.Garbage -> JSONObject().put("t", "gb").put("n", m.lines).put("f", m.from)
        is ServerMsg.Over -> JSONObject().put("t", "over").put(
            "s",
            JSONArray().apply {
                m.standings.forEach {
                    put(JSONObject().put("r", it.rank).put("n", it.name).put("sc", it.score).put("ln", it.lines))
                }
            }
        )

        is ServerMsg.Bye -> JSONObject().put("t", "bye").put("r", m.reason)
    }.toString()

    fun decodeServer(line: String): ServerMsg? {
        val o = runCatching { JSONObject(line) }.getOrNull() ?: return null
        return when (o.optString("t")) {
            "welcome" -> ServerMsg.Welcome(o.optInt("id"))
            "lobby" -> ServerMsg.Lobby(o.optJSONArray("p").mapObjects {
                PlayerInfo(it.optInt("id"), it.optString("n"), it.optBoolean("h"))
            })

            "start" -> ServerMsg.Start(o.optLong("seed"), o.optLong("cd"))
            "world" -> ServerMsg.World(o.optJSONArray("p").mapObjects {
                PlayerState(
                    it.optInt("id"), it.optString("n"), it.optString("b"),
                    it.optLong("sc"), it.optInt("ln"), it.optInt("pg"), it.optBoolean("a")
                )
            })

            "gb" -> ServerMsg.Garbage(o.optInt("n"), o.optString("f"))
            "over" -> ServerMsg.Over(o.optJSONArray("s").mapObjects {
                Standing(it.optInt("r"), it.optString("n"), it.optLong("sc"), it.optInt("ln"))
            })

            "bye" -> ServerMsg.Bye(o.optString("r"))
            else -> null
        }
    }

    fun encode(m: ClientMsg): String = when (m) {
        is ClientMsg.Join -> JSONObject().put("t", "join").put("n", m.name)
        is ClientMsg.State -> JSONObject().put("t", "state").put("b", m.board)
            .put("sc", m.score).put("ln", m.lines).put("pg", m.pending).put("a", m.alive)

        is ClientMsg.Attack -> JSONObject().put("t", "atk").put("n", m.lines)
        ClientMsg.Dead -> JSONObject().put("t", "dead")
    }.toString()

    fun decodeClient(line: String): ClientMsg? {
        val o = runCatching { JSONObject(line) }.getOrNull() ?: return null
        return when (o.optString("t")) {
            "join" -> ClientMsg.Join(o.optString("n"))
            "state" -> ClientMsg.State(
                o.optString("b"), o.optLong("sc"), o.optInt("ln"), o.optInt("pg"), o.optBoolean("a")
            )

            "atk" -> ClientMsg.Attack(o.optInt("n"))
            "dead" -> ClientMsg.Dead
            else -> null
        }
    }

    private fun <T> JSONArray?.mapObjects(f: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optJSONObject(it) }.map(f)
    }
}
