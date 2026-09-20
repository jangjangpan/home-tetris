package dev.hometetris.net

import dev.hometetris.core.Action
import dev.hometetris.core.BotAi
import dev.hometetris.core.BotConfig
import dev.hometetris.core.ItemTarget
import dev.hometetris.core.TetrisEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

/**
 * 싱글 모드. 네트워크 없이 컴퓨터와 둘이 붙는다.
 * 멀티와 똑같은 [Session] 이라서 화면과 게임 루프는 그대로 쓴다 -
 * 컴퓨터가 1번 플레이어 자리에 앉는 셈이다.
 */
class SoloSession(
    private val myName: String,
    val level: Int,
    private val scope: CoroutineScope,
    /** 시작 화면에서 고른 대전 방식. 방을 열자마자 이 모드로 시작한다. */
    private var mode: GameMode = GameMode.NORMAL,
    /** 컴퓨터의 손 속도를 재는 시계. 테스트에서 가상 시간을 물리려고 뽑아 뒀다. */
    private val nanoTime: () -> Long = System::nanoTime,
) : Session {

    override val isHost = true

    private val config = BotConfig.forLevel(level)
    val botName = "컴퓨터 Lv${config.level}"

    private val _incoming = MutableSharedFlow<ServerMsg>(replay = 0, extraBufferCapacity = 64)
    override val incoming: SharedFlow<ServerMsg> = _incoming

    private val rng = Random(System.nanoTime())
    private var loopJob: Job? = null

    private var bot: TetrisEngine? = null
    private var botAlive = true
    private var botDeathOrder = 0

    private var playerAlive = true
    private var playerDeathOrder = 0
    private var playerBoard = EMPTY_BOARD
    private var playerScore = 0L
    private var playerLines = 0
    private var playerPending = 0

    private var deaths = 0
    private var finished = false

    // 컴퓨터가 지금 조각을 어디로 옮기는 중인지
    private var hasPlan = false
    private var targetRot = 0
    private var targetX = 3
    private var stepBudget = 0
    private var stepsUsed = 0
    private var stepDelayMs = 200L
    private var stepAcc = 0L
    private var worldAcc = 0L

    init {
        scope.launch {
            // 화면이 구독을 붙인 뒤에 알려야 메시지가 버려지지 않는다.
            _incoming.subscriptionCount.first { it > 0 }
            _incoming.emit(ServerMsg.Welcome(0))
            _incoming.emit(
                ServerMsg.Lobby(
                    listOf(PlayerInfo(0, myName, true), PlayerInfo(1, botName, false))
                )
            )
            startGame(mode)
        }
    }

    override fun send(m: ClientMsg) {
        when (m) {
            is ClientMsg.Join -> Unit
            is ClientMsg.State -> {
                playerBoard = m.board
                playerScore = m.score
                playerLines = m.lines
                playerPending = m.pending
                if (!m.alive && playerAlive) killPlayer()
            }

            is ClientMsg.Attack -> bot?.receiveGarbage(m.lines)
            is ClientMsg.UseItem -> bot?.receiveItem(m.kind)
            ClientMsg.Dead -> killPlayer()
        }
    }

    override fun startGame(mode: GameMode) {
        this.mode = mode
        loopJob?.cancel()
        val seed = rng.nextLong()
        bot = TetrisEngine(seed, itemMode = mode == GameMode.ITEM)
        botAlive = true
        playerAlive = true
        botDeathOrder = 0
        playerDeathOrder = 0
        deaths = 0
        finished = false
        playerBoard = EMPTY_BOARD
        playerScore = 0
        playerLines = 0
        playerPending = 0
        hasPlan = false
        stepAcc = 0
        worldAcc = 0

        loopJob = scope.launch {
            // 사람 쪽과 똑같이 3초 세고 시작한다. 그동안 컴퓨터도 손을 놓고 있는다.
            _incoming.emit(ServerMsg.Start(seed, COUNTDOWN_MS, mode))
            delay(COUNTDOWN_MS)
            runBotLoop()
        }
    }

    override fun close() {
        loopJob?.cancel()
        loopJob = null
    }

    private suspend fun runBotLoop() {
        var last = nanoTime()
        while (scope.isActive && !finished) {
            delay(16)
            val now = nanoTime()
            val dt = ((now - last) / 1_000_000L).coerceIn(0L, 100L)
            last = now

            val b = bot
            if (b != null && botAlive && !b.dead) {
                stepAcc += dt
                while (stepAcc >= stepDelayMs && botAlive && !b.dead) {
                    stepAcc -= stepDelayMs
                    advanceBot(b)
                }
                if (b.dead) killBot()
            }

            worldAcc += dt
            if (worldAcc >= WORLD_INTERVAL_MS) {
                worldAcc = 0
                emitWorld()
            }
        }
    }

    /** 컴퓨터의 손 한 번. 회전 -> 좌우 이동 -> 하드드롭 순서로 한 단계씩 움직인다. */
    private suspend fun advanceBot(b: TetrisEngine) {
        if (!hasPlan) {
            plan(b)
            if (!hasPlan) return
        }
        stepsUsed++
        // 회전이나 이동이 막혀서 목표에 못 닿는 경우가 있다. 그때는 그냥 떨어뜨린다.
        val giveUp = stepsUsed > stepBudget
        val action = when {
            !giveUp && b.rot != targetRot -> Action.ROTATE_CW
            !giveUp && b.px < targetX -> Action.RIGHT
            !giveUp && b.px > targetX -> Action.LEFT
            else -> Action.HARD_DROP
        }
        val result = b.input(action)
        if (action == Action.HARD_DROP) {
            hasPlan = false
            if (result != null && result.attackSent > 0) {
                _incoming.emit(ServerMsg.Garbage(result.attackSent, botName))
            }
            // 아이템은 손에 들어오는 대로 쓴다. 언제 쓸지까지 재는 판단은 넣지 않았다.
            b.item?.let {
                val used = b.takeItem()
                if (used != null && used.target == ItemTarget.ENEMY) {
                    _incoming.emit(ServerMsg.ItemHit(used, botName))
                }
            }
        }
    }

    private fun plan(b: TetrisEngine) {
        // 두 수 앞을 보는 난이도면 다음다음 조각까지 넘긴다.
        val upcoming = if (config.lookahead) b.nextQueue(2) else emptyList()
        val p = BotAi.choose(
            b.board, b.type,
            next = upcoming.getOrNull(0),
            config = config,
            rng = rng,
            next2 = upcoming.getOrNull(1),
        )
        if (p == null) {
            // 놓을 자리가 없다 = 컴퓨터도 게임 오버.
            b.kill()
            return
        }
        targetRot = p.rot
        targetX = p.x
        val moves = p.rot + abs(p.x - b.px) + 1
        stepBudget = moves + 6
        stepsUsed = 0
        stepDelayMs = max(30L, config.placeIntervalMs / moves)
        hasPlan = true
    }

    private suspend fun emitWorld() {
        val b = bot ?: return
        _incoming.emit(
            ServerMsg.World(
                listOf(
                    PlayerState(0, myName, playerBoard, playerScore, playerLines, playerPending, playerAlive),
                    PlayerState(1, botName, b.snapshot(), b.score, b.lines, b.pendingGarbage, botAlive && !b.dead),
                )
            )
        )
    }

    private fun killPlayer() {
        if (!playerAlive) return
        playerAlive = false
        playerDeathOrder = ++deaths
        finish()
    }

    private fun killBot() {
        if (!botAlive) return
        botAlive = false
        botDeathOrder = ++deaths
        finish()
    }

    /** 둘 중 하나만 죽어도 끝이다. 결과 발표는 딱 한 번만 나가야 한다. */
    private fun finish() {
        if (finished) return
        finished = true
        scope.launch { emitOver() }
    }

    private suspend fun emitOver() {
        val b = bot
        _incoming.emit(
            ServerMsg.Over(
                standingsOf(
                    listOf(
                        FinishState(myName, playerAlive, playerDeathOrder, playerScore, playerLines),
                        FinishState(botName, botAlive, botDeathOrder, b?.score ?: 0L, b?.lines ?: 0),
                    )
                )
            )
        )
    }

    private companion object {
        const val COUNTDOWN_MS = 3000L
        const val WORLD_INTERVAL_MS = 90L
        val EMPTY_BOARD = "0".repeat(200)
    }
}
