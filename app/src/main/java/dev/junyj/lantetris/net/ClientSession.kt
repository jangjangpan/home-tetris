package dev.junyj.lantetris.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.net.Socket

/** 참가자 기기에서 도는 쪽. 방장 폰의 서버에 TCP로 붙는다. */
class ClientSession(
    private val host: String,
    // 이름을 'port'로 두면 아래 Socket 블록 안에서 Socket.getPort()에 가려진다. 그래서 gamePort.
    private val gamePort: Int,
    private val myName: String,
    private val scope: CoroutineScope,
) : Session {

    override val isHost = false

    private val _incoming = MutableSharedFlow<ServerMsg>(replay = 0, extraBufferCapacity = 64)
    override val incoming: SharedFlow<ServerMsg> = _incoming

    private val outbox = Channel<ClientMsg>(Channel.UNLIMITED)
    private var socket: Socket? = null
    private val jobs = mutableListOf<Job>()

    init {
        jobs += scope.launch(Dispatchers.IO) { connectAndRun() }
    }

    override fun send(m: ClientMsg) {
        outbox.trySend(m)
    }

    override fun startGame() = Unit // 시작은 방장만 할 수 있다

    override fun close() {
        runCatching { socket?.close() }
        jobs.forEach { it.cancel() }
        outbox.close()
    }

    private suspend fun connectAndRun() {
        // UI가 구독을 붙이기 전에 보낸 메시지는 버려지므로 첫 구독자를 기다린다.
        _incoming.subscriptionCount.first { it > 0 }

        // apply 가 아니라 also 를 쓴다. apply 안에서는 이름 해석이 Socket 쪽으로 먼저 가서,
        // 바깥의 포트 대신 미연결 Socket 의 port(=0)를 집어가 버린다. 실제로 그 버그를 겪었다.
        val s = runCatching {
            Socket().also {
                it.tcpNoDelay = true
                it.connect(InetSocketAddress(host, gamePort), 5000)
            }
        }.getOrElse {
            _incoming.emit(ServerMsg.Bye("연결 실패: ${it.message ?: "호스트에 닿지 못했습니다"}"))
            return
        }
        socket = s

        val writer = s.getOutputStream().bufferedWriter()
        val reader = s.getInputStream().bufferedReader()

        val writerJob = scope.launch(Dispatchers.IO) {
            runCatching {
                for (m in outbox) {
                    writer.write(Codec.encode(m))
                    writer.write("\n")
                    writer.flush()
                }
            }
        }
        jobs += writerJob

        send(ClientMsg.Join(myName))

        try {
            while (scope.isActive) {
                val line = reader.readLine() ?: break
                Codec.decodeServer(line)?.let { _incoming.emit(it) }
            }
            _incoming.emit(ServerMsg.Bye("호스트와 연결이 끊겼습니다"))
        } catch (e: Exception) {
            _incoming.emit(ServerMsg.Bye("연결이 끊겼습니다: ${e.message ?: ""}"))
        } finally {
            writerJob.cancel()
            runCatching { s.close() }
        }
    }
}
