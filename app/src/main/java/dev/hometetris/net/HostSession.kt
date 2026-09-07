package dev.hometetris.net

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.BufferedWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentSkipListMap
import kotlin.random.Random

private const val TAG = "LanTetris/Host"

private enum class Phase { LOBBY, PLAYING, OVER }

/**
 * 방장 기기에서 도는 게임 서버. 자기 자신도 0번 플레이어로 참가한다.
 * 각자 자기 폰에서 자기 게임을 돌리고, 여기로는 보드 스냅샷과 공격만 오간다.
 */
class HostSession(
    private val context: Context,
    private val myName: String,
    private val roomName: String,
    private val scope: CoroutineScope,
    private val port: Int = DEFAULT_GAME_PORT,
) : Session {

    override val isHost = true

    private val _incoming = MutableSharedFlow<ServerMsg>(replay = 0, extraBufferCapacity = 64)
    override val incoming: SharedFlow<ServerMsg> = _incoming

    private class Peer(
        val id: Int,
        val socket: Socket?,
        val writer: BufferedWriter?,
    ) {
        var name: String = "플레이어"
        var joined = false
        var board: String = "0".repeat(200)
        var score: Long = 0
        var lines: Int = 0
        var pending: Int = 0
        var alive: Boolean = true
        var deathOrder: Int = 0
        var connected: Boolean = true
    }

    // accept 코루틴과 process 코루틴이 같이 건드리므로 동시성 맵을 쓴다. id 순 정렬이라 표시 순서도 안정적이다.
    private val peers = ConcurrentSkipListMap<Int, Peer>()
    private val inbox = Channel<Pair<Int, ClientMsg?>>(Channel.UNLIMITED)
    private var nextId = 1
    private var phase = Phase.LOBBY
    private var deaths = 0
    private val rng = Random(System.nanoTime())

    private var serverSocket: ServerSocket? = null
    private var responder: DiscoveryResponder? = null
    private val jobs = mutableListOf<Job>()

    init {
        val me = Peer(0, null, null).apply {
            name = myName
            joined = true
        }
        peers[0] = me
        jobs += scope.launch(Dispatchers.IO) { processLoop() }
        jobs += scope.launch(Dispatchers.IO) { acceptLoop() }
        jobs += scope.launch(Dispatchers.IO) { worldLoop() }
        responder = DiscoveryResponder(context, roomName, port) { peers.count { it.value.joined } }
        jobs += scope.launch(Dispatchers.IO) { responder?.run() }
        scope.launch {
            // UI가 구독하기 전에 보내면 그대로 버려진다. 첫 구독자가 붙은 뒤에 알린다.
            _incoming.subscriptionCount.first { it > 0 }
            _incoming.emit(ServerMsg.Welcome(0))
            emitLobby()
        }
    }

    override fun send(m: ClientMsg) {
        inbox.trySend(0 to m)
    }

    override fun startGame() {
        inbox.trySend(-1 to null) // 시작 신호
    }

    override fun close() {
        peers.values.filter { it.id != 0 }.forEach { writeTo(it, ServerMsg.Bye("방장이 방을 닫았습니다")) }
        runCatching { serverSocket?.close() }
        responder?.stop()
        peers.values.forEach { runCatching { it.socket?.close() } }
        jobs.forEach { it.cancel() }
        inbox.close()
    }

    // ---- 연결 관리 ----

    private suspend fun acceptLoop() {
        val ss = runCatching { ServerSocket(port) }.getOrElse {
            Log.e(TAG, "포트 $port 열기 실패", it)
            _incoming.emit(ServerMsg.Bye("포트 $port 를 열 수 없습니다"))
            return
        }
        serverSocket = ss
        while (scope.isActive && !ss.isClosed) {
            val s = runCatching { ss.accept() }.getOrNull() ?: break
            s.tcpNoDelay = true
            val id = nextId++
            val writer = s.getOutputStream().bufferedWriter()
            val peer = Peer(id, s, writer)
            peers[id] = peer
            jobs += scope.launch(Dispatchers.IO) { readLoop(peer, s.getInputStream().bufferedReader()) }
        }
    }

    private suspend fun readLoop(peer: Peer, reader: BufferedReader) {
        try {
            while (scope.isActive) {
                val line = reader.readLine() ?: break
                Codec.decodeClient(line)?.let { inbox.trySend(peer.id to it) }
            }
        } catch (_: Exception) {
        } finally {
            peer.connected = false
            inbox.trySend(peer.id to null)
        }
    }

    private fun writeTo(peer: Peer, m: ServerMsg) {
        if (peer.id == 0) {
            _incoming.tryEmit(m)
            return
        }
        val w = peer.writer ?: return
        runCatching {
            w.write(Codec.encode(m))
            w.write("\n")
            w.flush()
        }.onFailure { peer.connected = false }
    }

    private fun broadcast(m: ServerMsg) {
        peers.values.forEach { writeTo(it, m) }
    }

    private fun emitLobby() {
        val list = peers.values.filter { it.joined && it.connected }
            .map { PlayerInfo(it.id, it.name, it.id == 0) }
        broadcast(ServerMsg.Lobby(list))
    }

    // ---- 게임 진행 ----

    private suspend fun processLoop() {
        for ((id, msg) in inbox) {
            if (id == -1) {
                doStart()
                continue
            }
            val peer = peers[id] ?: continue
            if (msg == null) {
                handleDisconnect(peer)
                continue
            }
            when (msg) {
                is ClientMsg.Join -> {
                    if (phase != Phase.LOBBY) {
                        writeTo(peer, ServerMsg.Bye("이미 게임이 시작됐습니다"))
                        peers.remove(peer.id)
                        runCatching { peer.socket?.close() }
                        continue
                    }
                    if (peers.count { it.value.joined } >= MAX_PLAYERS) {
                        writeTo(peer, ServerMsg.Bye("방이 가득 찼습니다 (최대 ${MAX_PLAYERS}명)"))
                        peers.remove(peer.id)
                        runCatching { peer.socket?.close() }
                        continue
                    }
                    peer.name = msg.name.ifBlank { "플레이어${peer.id}" }
                    peer.joined = true
                    writeTo(peer, ServerMsg.Welcome(peer.id))
                    emitLobby()
                }

                is ClientMsg.State -> {
                    peer.board = msg.board
                    peer.score = msg.score
                    peer.lines = msg.lines
                    peer.pending = msg.pending
                    if (!msg.alive && peer.alive) markDead(peer)
                }

                is ClientMsg.Attack -> routeAttack(peer, msg.lines)
                ClientMsg.Dead -> markDead(peer)
            }
        }
    }

    private fun handleDisconnect(peer: Peer) {
        peers.remove(peer.id)
        runCatching { peer.socket?.close() }
        if (phase == Phase.PLAYING && peer.alive) markDead(peer)
        emitLobby()
    }

    private fun doStart() {
        if (phase != Phase.LOBBY) return
        val joined = peers.values.filter { it.joined && it.connected }
        if (joined.isEmpty()) return
        phase = Phase.PLAYING
        deaths = 0
        joined.forEach {
            it.alive = true
            it.deathOrder = 0
            it.score = 0
            it.lines = 0
            it.pending = 0
            it.board = "0".repeat(200)
        }
        // 모두 같은 seed를 쓰므로 블록 순서가 똑같다. 운이 아니라 실력으로 갈리게.
        broadcast(ServerMsg.Start(rng.nextLong(), 3000L))
    }

    /** 공격은 나를 뺀 생존자 중 무작위 한 명에게 간다. */
    private fun routeAttack(from: Peer, lines: Int) {
        if (phase != Phase.PLAYING || lines <= 0) return
        val targets = peers.values.filter { it.id != from.id && it.joined && it.connected && it.alive }
        if (targets.isEmpty()) return
        val target = targets[rng.nextInt(targets.size)]
        writeTo(target, ServerMsg.Garbage(lines, from.name))
    }

    private fun markDead(peer: Peer) {
        if (!peer.alive) return
        peer.alive = false
        peer.deathOrder = ++deaths
        checkGameOver()
    }

    private fun checkGameOver() {
        if (phase != Phase.PLAYING) return
        val joined = peers.values.filter { it.joined }
        val aliveCount = joined.count { it.alive }
        val endThreshold = if (joined.size <= 1) 0 else 1
        if (aliveCount > endThreshold) return

        phase = Phase.OVER
        val standings = standingsOf(
            joined.map { FinishState(it.name, it.alive, it.deathOrder, it.score, it.lines) }
        )
        broadcast(ServerMsg.Over(standings))
        phase = Phase.LOBBY
        scope.launch(Dispatchers.IO) {
            delay(200)
            emitLobby()
        }
    }

    private suspend fun worldLoop() {
        while (scope.isActive) {
            delay(90)
            if (phase != Phase.PLAYING) continue
            val list = peers.values.filter { it.joined }.map {
                PlayerState(it.id, it.name, it.board, it.score, it.lines, it.pending, it.alive)
            }
            broadcast(ServerMsg.World(list))
        }
    }
}
