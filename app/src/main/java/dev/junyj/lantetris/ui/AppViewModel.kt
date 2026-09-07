package dev.junyj.lantetris.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.junyj.lantetris.core.Action
import dev.junyj.lantetris.core.BotConfig
import dev.junyj.lantetris.core.PieceType
import dev.junyj.lantetris.core.TetrisEngine
import dev.junyj.lantetris.net.ClientMsg
import dev.junyj.lantetris.net.ClientSession
import dev.junyj.lantetris.net.GameSessionService
import dev.junyj.lantetris.net.HostSession
import dev.junyj.lantetris.net.SoloSession
import dev.junyj.lantetris.net.PlayerInfo
import dev.junyj.lantetris.net.PlayerState
import dev.junyj.lantetris.net.Room
import dev.junyj.lantetris.net.ServerMsg
import dev.junyj.lantetris.net.Session
import dev.junyj.lantetris.net.Standing
import dev.junyj.lantetris.net.scanForRooms
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class Screen { HOME, SOLO_SETUP, MULTI_HOME, SCAN, LOBBY, PLAY, RESULT }

/** 최근에 상대가 나에게 방해 줄을 보냈다는 걸 잠깐 띄우기 위한 것. */
data class AttackFlash(val from: String, val lines: Int, val at: Long)

private const val DAS_MS = 160L
private const val ARR_MS = 40L
/** 아래 버튼을 누르고 있을 때 한 칸씩 내려가는 간격. */
private const val SOFT_DROP_REPEAT_MS = 55L
private const val STATE_SEND_MS = 90L
private val EMPTY_BOARD = "0".repeat(200)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("lantetris", Context.MODE_PRIVATE)

    var screen by mutableStateOf(Screen.HOME)
        private set
    var playerName by mutableStateOf(prefs.getString("name", "") ?: "")
    var status by mutableStateOf<String?>(null)

    val rooms = mutableStateListOf<Room>()
    var scanning by mutableStateOf(false)
        private set

    val lobbyPlayers = mutableStateListOf<PlayerInfo>()
    var myId by mutableStateOf(0)
        private set
    var isHost by mutableStateOf(false)
        private set

    /** 싱글 모드인지. 결과 화면에서 '다시 하기'와 '대기실로'가 갈린다. */
    var isSolo by mutableStateOf(false)
        private set
    var botLevel by mutableStateOf(prefs.getInt("botLevel", 3).coerceIn(BotConfig.MIN_LEVEL, BotConfig.MAX_LEVEL))

    /** 화면 테마. 블록 질감까지 함께 바뀐다. */
    var theme by mutableStateOf(themeByKey(prefs.getString("theme", null)))
        private set

    fun pickTheme(t: GameTheme) {
        theme = t
        prefs.edit().putString("theme", t.key).apply()
    }

    var engine by mutableStateOf<TetrisEngine?>(null)
        private set

    /** Canvas가 매 프레임 다시 그리도록 붙잡아 두는 값. */
    var frame by mutableStateOf(0L)
        private set

    /**
     * 화면에 띄울 내 상태를 엔진에서 복사해 둔 것.
     *
     * 엔진은 Compose State 가 아니라서, 컴포저블이 `engine.score` 같은 걸 그냥 읽으면
     * **처음 값에 멈춘 채로 남는다**(다시 그릴 계기가 없다). 실제로 NEXT·HOLD·점수가
     * 시작 시점 그대로였다. 그래서 여기 State 로 옮겨 담고 화면은 이것만 본다.
     * 보드는 60fps 로 따로 그리지만 이쪽은 10Hz면 충분하다.
     */
    var hudScore by mutableStateOf(0L)
        private set
    var hudLines by mutableStateOf(0)
        private set
    var hudLevel by mutableStateOf(1)
        private set
    var hudGravityMs by mutableStateOf(800L)
        private set
    var hudHold by mutableStateOf<PieceType?>(null)
        private set
    val hudNext = mutableStateListOf<PieceType>()

    private fun syncHud(e: TetrisEngine) {
        if (hudScore != e.score) hudScore = e.score
        if (hudLines != e.lines) hudLines = e.lines
        if (hudLevel != e.level) hudLevel = e.level
        if (hudGravityMs != e.gravityMs) hudGravityMs = e.gravityMs
        if (hudHold != e.hold) hudHold = e.hold
        val next = e.nextQueue(4)
        if (hudNext != next) {
            hudNext.clear()
            hudNext.addAll(next)
        }
    }

    var countdown by mutableStateOf(0)
        private set

    val opponents = mutableStateListOf<PlayerState>()
    val standings = mutableStateListOf<Standing>()

    /** 아래 버튼을 누르고 있는 중인지. 마스코트가 전력질주할지 정하는 데 쓴다. */
    var softDropping by mutableStateOf(false)
        private set

    /** 결과 화면 버튼을 받을 준비가 됐는지. 뜨자마자는 오탭이 나서 잠깐 막는다. */
    var resultReady by mutableStateOf(false)
        private set
    var attackFlash by mutableStateOf<AttackFlash?>(null)
        private set
    var lastLockResult by mutableStateOf<String?>(null)
        private set

    private var session: Session? = null
    private var sessionJob: Job? = null
    private var loopJob: Job? = null
    private var scanJob: Job? = null

    private var heldDir = 0
    private var heldSince = 0L
    private var lastRepeat = 0L
    private var softDropAcc = 0L
    private var stateAcc = 0L
    private var frameAcc = 0L
    private var hudAcc = 0L
    private var flashUntil = 0L

    // ---- 접속 ----

    private fun rememberName(): String {
        val n = playerName.trim().ifBlank { "플레이어" }
        prefs.edit().putString("name", n).apply()
        playerName = n
        return n
    }

    fun openSoloSetup() {
        rememberName()
        screen = Screen.SOLO_SETUP
    }

    fun openMultiHome() {
        rememberName()
        screen = Screen.MULTI_HOME
    }

    /** 싱글 모드 시작. 로비 없이 바로 카운트다운으로 들어간다. */
    fun startSolo(level: Int = botLevel) {
        val n = rememberName()
        botLevel = level.coerceIn(BotConfig.MIN_LEVEL, BotConfig.MAX_LEVEL)
        prefs.edit().putInt("botLevel", botLevel).apply()
        closeSession()
        isSolo = true
        isHost = true
        bind(SoloSession(n, botLevel, viewModelScope))
    }

    fun hostRoom() {
        val n = rememberName()
        closeSession()
        isSolo = false
        isHost = true
        bind(HostSession(getApplication(), n, "${n}의 방", viewModelScope))
        // 앱을 잠시 내려도 참가자와의 연결이 끊기지 않게 붙잡아 둔다.
        GameSessionService.start(getApplication(), "방을 열어 두는 중 · ${n}의 방")
        screen = Screen.LOBBY
    }

    fun openScan() {
        rememberName()
        rooms.clear()
        screen = Screen.SCAN
        startScan()
    }

    fun startScan() {
        scanJob?.cancel()
        scanning = true
        scanJob = viewModelScope.launch {
            scanForRooms(6000) { room ->
                if (rooms.none { it.address == room.address && it.port == room.port }) rooms.add(room)
            }
            scanning = false
        }
    }

    fun joinRoom(address: String, port: Int) {
        val n = rememberName()
        closeSession()
        isSolo = false
        isHost = false
        status = "$address 에 접속 중…"
        bind(ClientSession(address, port, n, viewModelScope))
        // 참가자도 앱을 내리면 똑같이 끊긴다. 같이 붙잡아 둔다.
        GameSessionService.start(getApplication(), "게임에 참가 중")
        screen = Screen.LOBBY
    }

    private fun bind(s: Session) {
        session = s
        sessionJob = viewModelScope.launch {
            s.incoming.collect { onServerMsg(it) }
        }
    }

    fun leave() {
        closeSession()
        stopLoop()
        lobbyPlayers.clear()
        opponents.clear()
        engine = null
        screen = Screen.HOME
    }

    private fun closeSession() {
        // 먼저 수신을 끊는다. 안 그러면 닫히는 세션의 마지막 Bye가
        // 방금 새로 연 화면을 다시 홈으로 되돌려 버린다.
        sessionJob?.cancel()
        sessionJob = null
        session?.close()
        session = null
        GameSessionService.stop(getApplication())
    }

    fun startGame() {
        session?.startGame()
    }

    fun backToLobby() {
        stopLoop()
        engine = null
        opponents.clear()
        screen = Screen.LOBBY
    }

    /** 싱글 모드에서 같은 난이도로 한 판 더. */
    fun playSoloAgain() {
        stopLoop()
        engine = null
        opponents.clear()
        session?.startGame()
    }

    // ---- 서버 메시지 ----

    private fun onServerMsg(m: ServerMsg) {
        when (m) {
            is ServerMsg.Welcome -> {
                myId = m.id
                status = null
            }

            is ServerMsg.Lobby -> {
                lobbyPlayers.clear()
                lobbyPlayers.addAll(m.players)
            }

            is ServerMsg.Start -> beginMatch(m.seed, m.countdownMs)

            is ServerMsg.World -> {
                opponents.clear()
                opponents.addAll(m.players.filter { it.id != myId })
            }

            is ServerMsg.Garbage -> {
                engine?.receiveGarbage(m.lines)
                attackFlash = AttackFlash(m.from, m.lines, System.currentTimeMillis())
                flashUntil = System.currentTimeMillis() + 1200
            }

            is ServerMsg.Over -> {
                stopLoop()
                standings.clear()
                standings.addAll(m.standings)
                screen = Screen.RESULT
                // 결과 화면은 게임이 끝나는 순간 바로 뜬다. 그 자리에 하드드롭/HOLD 버튼이 있었으므로
                // 마지막까지 누르던 손가락이 '한 판 더'나 '난이도 바꾸기'를 눌러 버린다. 잠깐 막아 둔다.
                resultReady = false
                viewModelScope.launch {
                    delay(1200)
                    resultReady = true
                }
            }

            is ServerMsg.Bye -> {
                status = m.reason
                closeSession()
                stopLoop()
                screen = Screen.HOME
            }
        }
    }

    private fun beginMatch(seed: Long, countdownMs: Long) {
        stopLoop()
        val e = TetrisEngine(seed)
        engine = e
        // 카운트다운 동안에도 상대 자리를 미리 잡아 둔다.
        // 안 그러면 첫 World가 도착하는 순간 내 보드가 아래로 밀리며 화면이 출렁인다.
        opponents.clear()
        opponents.addAll(
            lobbyPlayers.filter { it.id != myId }
                .map { PlayerState(it.id, it.name, EMPTY_BOARD, 0, 0, 0, true) }
        )
        standings.clear()
        attackFlash = null
        lastLockResult = null
        heldDir = 0
        softDropping = false
        syncHud(e)
        screen = Screen.PLAY
        countdown = ((countdownMs + 999) / 1000).toInt()

        loopJob = viewModelScope.launch {
            var remaining = countdownMs
            while (remaining > 0 && isActive) {
                delay(100)
                remaining -= 100
                countdown = ((remaining + 999) / 1000).toInt().coerceAtLeast(0)
            }
            countdown = 0
            runLoop(e)
        }
    }

    private suspend fun runLoop(e: TetrisEngine) {
        var last = System.nanoTime()
        var deadReported = false
        while (viewModelScope.isActive && loopJob?.isActive == true) {
            delay(8)
            val now = System.nanoTime()
            val dt = ((now - last) / 1_000_000L).coerceIn(0L, 100L)
            last = now

            applyAutoRepeat(dt)
            e.update(dt)?.let { handleLock(it) }

            if (e.dead && !deadReported) {
                deadReported = true
                session?.send(ClientMsg.Dead)
            }
            if (attackFlash != null && System.currentTimeMillis() > flashUntil) attackFlash = null

            stateAcc += dt
            if (stateAcc >= STATE_SEND_MS) {
                stateAcc = 0
                session?.send(
                    ClientMsg.State(e.snapshot(), e.score, e.lines, e.pendingGarbage, !e.dead)
                )
            }
            // 화면은 60fps면 충분하다. 매 tick마다 갱신하면 불필요하게 recompose 된다.
            frameAcc += dt
            if (frameAcc >= 16) {
                frameAcc = 0
                frame = now
            }
            hudAcc += dt
            if (hudAcc >= 100) {
                hudAcc = 0
                syncHud(e)
            }
        }
    }

    private fun handleLock(r: dev.junyj.lantetris.core.LockResult) {
        if (r.attackSent > 0) session?.send(ClientMsg.Attack(r.attackSent))
        lastLockResult = when {
            r.perfectClear -> "퍼펙트 클리어! +${r.attackSent}"
            r.combo >= 3 && r.linesCleared > 0 -> "${r.combo} COMBO → ${r.attackSent}줄"
            r.linesCleared == 4 -> "테트리스! → ${r.attackSent}줄"
            r.linesCleared >= 2 -> "${r.linesCleared}줄 → ${r.attackSent}줄"
            else -> null
        }
    }

    // ---- 입력 ----

    private fun applyAutoRepeat(dt: Long) {
        val e = engine ?: return
        if (heldDir != 0) {
            heldSince += dt
            if (heldSince >= DAS_MS) {
                lastRepeat += dt
                if (lastRepeat >= ARR_MS) {
                    lastRepeat = 0
                    e.input(if (heldDir < 0) Action.LEFT else Action.RIGHT)
                }
            }
        }
        // 아래 버튼은 누르고 있는 동안 한 칸씩 이어서 내려간다.
        if (softDropping) {
            softDropAcc += dt
            while (softDropAcc >= SOFT_DROP_REPEAT_MS) {
                softDropAcc -= SOFT_DROP_REPEAT_MS
                e.input(Action.SOFT_DROP)
            }
        }
    }

    fun pressDirection(dir: Int) {
        val e = engine ?: return
        if (countdown > 0) return
        heldDir = dir
        heldSince = 0
        lastRepeat = 0
        e.input(if (dir < 0) Action.LEFT else Action.RIGHT)
    }

    fun releaseDirection() {
        heldDir = 0
    }

    /** 누른 즉시 한 칸. 계속 누르고 있으면 [SOFT_DROP_REPEAT_MS] 간격으로 이어서 내려간다. */
    fun pressSoftDrop() {
        val e = engine ?: return
        if (countdown > 0) return
        softDropping = true
        softDropAcc = 0
        e.input(Action.SOFT_DROP)
    }

    fun releaseSoftDrop() {
        softDropping = false
        softDropAcc = 0
    }

    fun tap(action: Action) {
        val e = engine ?: return
        if (countdown > 0) return
        e.input(action)?.let { handleLock(it) }
        // 홀드 교환처럼 즉시 보여야 하는 것이 있어서, 다음 주기를 기다리지 않고 바로 맞춘다.
        syncHud(e)
    }

    private fun stopLoop() {
        loopJob?.cancel()
        loopJob = null
        heldDir = 0
        softDropping = false
        softDropAcc = 0
    }

    override fun onCleared() {
        stopLoop()
        scanJob?.cancel()
        closeSession()
        super.onCleared()
    }
}
