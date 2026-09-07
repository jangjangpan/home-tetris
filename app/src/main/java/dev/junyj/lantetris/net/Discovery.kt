package dev.junyj.lantetris.net

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import kotlin.coroutines.coroutineContext

private const val TAG = "LanTetris/Discovery"

data class Room(
    val name: String,
    val hostName: String,
    val address: String,
    val port: Int,
    val players: Int,
)

/** 이 기기가 Wi-Fi에서 받은 IPv4 주소. 자동 탐색이 막혔을 때 손으로 불러줄 용도. */
fun localIpv4(): String? = runCatching {
    NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.toList() }
        .firstOrNull { !it.isLoopbackAddress && it.address.size == 4 }
        ?.hostAddress
}.getOrNull()

private fun broadcastTargets(): List<InetAddress> {
    val out = mutableListOf<InetAddress>()
    runCatching {
        for (ni in NetworkInterface.getNetworkInterfaces()) {
            if (!ni.isUp || ni.isLoopback) continue
            for (ia in ni.interfaceAddresses) {
                ia.broadcast?.let { out.add(it) }
            }
        }
    }
    runCatching { out.add(InetAddress.getByName("255.255.255.255")) }
    return out.distinct()
}

/**
 * 호스트가 켜 두는 응답기. 클라이언트의 UDP 브로드캐스트를 듣고 방 정보를 되돌려준다.
 * 공유기가 브로드캐스트를 막는 경우를 대비해 IP 직접 입력 경로도 UI에 남겨 뒀다.
 */
class DiscoveryResponder(
    private val context: Context,
    private val roomName: String,
    private val gamePort: Int,
    private val playerCount: () -> Int,
) {
    private var socket: DatagramSocket? = null
    private var lock: WifiManager.MulticastLock? = null

    suspend fun run() = withContext(Dispatchers.IO) {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        // 일부 기기는 멀티캐스트 락 없이는 브로드캐스트 패킷을 아예 올려주지 않는다.
        lock = wifi?.createMulticastLock("lantetris")?.apply {
            setReferenceCounted(false)
            runCatching { acquire() }
        }
        val s = DatagramSocket(null).apply {
            reuseAddress = true
            broadcast = true
            bind(InetSocketAddress(DISCOVERY_PORT))
            soTimeout = 1000
        }
        socket = s
        val buf = ByteArray(256)
        try {
            while (coroutineContext.isActive) {
                val p = DatagramPacket(buf, buf.size)
                try {
                    s.receive(p)
                } catch (e: SocketTimeoutException) {
                    continue
                }
                val msg = String(p.data, p.offset, p.length).trim()
                if (!msg.startsWith(DISCOVER_PING)) continue
                val reply = "$DISCOVER_PONG|$roomName|$gamePort|${playerCount()}".toByteArray()
                runCatching { s.send(DatagramPacket(reply, reply.size, p.address, p.port)) }
            }
        } finally {
            stop()
        }
    }

    fun stop() {
        runCatching { socket?.close() }
        socket = null
        runCatching { lock?.release() }
        lock = null
    }
}

/**
 * 방을 찾는다. [durationMs] 동안 브로드캐스트를 반복해서 쏘고, 답한 호스트를 [onFound]로 넘긴다.
 */
suspend fun scanForRooms(durationMs: Long, onFound: (Room) -> Unit) = withContext(Dispatchers.IO) {
    val socket = DatagramSocket(null).apply {
        reuseAddress = true
        broadcast = true
        bind(InetSocketAddress(0))
        soTimeout = 400
    }
    val ping = DISCOVER_PING.toByteArray()
    val seen = HashSet<String>()
    val deadline = System.currentTimeMillis() + durationMs
    var lastPing = 0L
    try {
        while (coroutineContext.isActive && System.currentTimeMillis() < deadline) {
            val now = System.currentTimeMillis()
            if (now - lastPing > 800) {
                lastPing = now
                for (t in broadcastTargets()) {
                    runCatching { socket.send(DatagramPacket(ping, ping.size, t, DISCOVERY_PORT)) }
                }
            }
            val buf = ByteArray(256)
            val p = DatagramPacket(buf, buf.size)
            try {
                socket.receive(p)
            } catch (e: SocketTimeoutException) {
                continue
            }
            val msg = String(p.data, p.offset, p.length).trim()
            if (!msg.startsWith(DISCOVER_PONG)) continue
            val parts = msg.split("|")
            if (parts.size < 4) continue
            val addr = p.address.hostAddress ?: continue
            val port = parts[2].toIntOrNull() ?: continue
            val key = "$addr:$port"
            if (!seen.add(key)) continue
            Log.i(TAG, "찾음 $key ${parts[1]}")
            onFound(Room(parts[1], parts[1], addr, port, parts[3].toIntOrNull() ?: 1))
        }
    } finally {
        runCatching { socket.close() }
    }
}
