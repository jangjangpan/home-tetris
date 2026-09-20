package dev.hometetris.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hometetris.core.Action
import dev.hometetris.core.BotConfig
import dev.hometetris.core.ItemKind
import dev.hometetris.net.GameMode
import dev.hometetris.core.TetrisEngine
import dev.hometetris.net.DEFAULT_GAME_PORT
import dev.hometetris.net.MAX_PLAYERS
import dev.hometetris.net.localIpv4

@Composable
fun AppRoot(vm: AppViewModel) {
    val t = vm.theme
    Box(
        Modifier
            .fillMaxSize()
            .background(t.bgBrush)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        // 게임 화면만 폭을 다 쓴다. 나머지는 넓은 기기에서 버튼이 우스꽝스럽게 늘어나지 않도록 모은다.
        // fillMaxSize() 를 먼저 걸면 폭이 고정돼서 widthIn 이 무시된다. 폭 제한을 먼저 줘야 한다.
        val content = Modifier.widthIn(max = 520.dp).fillMaxHeight().align(Alignment.TopCenter)
        when (vm.screen) {
            Screen.HOME -> Box(content) { HomeScreen(vm, t) }
            Screen.SOLO_SETUP -> Box(content) { SoloSetupScreen(vm, t) }
            Screen.MULTI_HOME -> Box(content) { MultiHomeScreen(vm, t) }
            Screen.SCAN -> Box(content) { ScanScreen(vm, t) }
            Screen.LOBBY -> Box(content) { LobbyScreen(vm, t) }
            Screen.PLAY -> GameScreen(vm, t)
            Screen.RESULT -> Box(content) { ResultScreen(vm, t) }
        }
    }
}

// ---- 공통 조각 ----

/** 시안의 카드. 반투명 판 + 얇은 테두리. */
@Composable
private fun Panel(
    t: GameTheme,
    modifier: Modifier = Modifier,
    radius: Int = 18,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(radius.dp))
            .background(t.panel)
            .border(1.dp, t.edge, RoundedCornerShape(radius.dp))
            .padding(10.dp),
        content = content,
    )
}

@Composable
private fun Label(text: String, t: GameTheme, size: Int = 10) {
    Text(
        text,
        color = t.inkDim,
        fontSize = size.sp,
        fontFamily = t.font,
        letterSpacing = 0.16.em(),
    )
}

private fun Double.em() = androidx.compose.ui.unit.TextUnit(
    this.toFloat(),
    androidx.compose.ui.unit.TextUnitType.Em,
)

@Composable
private fun PrimaryButton(
    text: String,
    t: GameTheme,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = t.accent,
            contentColor = t.bg,
            disabledContainerColor = t.accent.copy(alpha = 0.3f),
            disabledContentColor = t.bg.copy(alpha = 0.5f),
        ),
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = t.font)
    }
}

@Composable
private fun GhostButton(
    text: String,
    t: GameTheme,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, t.btnEdge),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = t.btnBg,
            contentColor = t.btnInk,
            disabledContentColor = t.btnInk.copy(alpha = 0.4f),
        ),
    ) {
        Text(text, fontSize = 15.sp, fontFamily = t.font)
    }
}

/** 노멀 / 아이템 고르기. 싱글 설정과 멀티 로비에서 같이 쓴다. */
@Composable
private fun ModePicker(vm: AppViewModel, t: GameTheme, enabled: Boolean = true) {
    Column {
        Label("대전 방식", t, 11)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GameMode.entries.forEach { m ->
                val on = vm.mode == m
                Box(
                    Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(if (on) t.accent else t.panel)
                        .border(1.dp, if (on) t.accent else t.edge, RoundedCornerShape(13.dp))
                        .then(if (enabled) Modifier.clickable { vm.pickMode(m) } else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (m == GameMode.NORMAL) "노멀" else "아이템",
                        color = if (on) t.bg else if (enabled) t.ink else t.inkDim,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = t.font,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (vm.mode == GameMode.ITEM)
                "줄을 지우면 아이템이 나옵니다. 슬롯은 하나뿐이라 아껴 쓸 수 없습니다."
            else
                "아이템 없이 순수하게 겨룹니다.",
            color = t.inkDim,
            fontSize = 12.sp,
            fontFamily = t.font,
        )
    }
}

/** 홈에 놓는 테마 고르기 칩. 시안 상단의 그것. */
@Composable
private fun ThemeChips(vm: AppViewModel, t: GameTheme) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Label("테마 · 블록 스타일까지 함께 바뀝니다", t, 10)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GameThemes.forEach { th ->
                val on = th.key == t.key
                Row(
                    Modifier
                        .clip(RoundedCornerShape(11.dp))
                        .background(if (on) th.bgTop else t.panel)
                        .border(
                            if (on) 2.dp else 1.dp,
                            if (on) t.accent else t.edge,
                            RoundedCornerShape(11.dp),
                        )
                        .clickable { vm.pickTheme(th) }
                        .padding(horizontal = 7.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    listOf(0, 3, 6).forEach { i ->
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(th.pieceColors[i])
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(t.label, color = t.ink, fontSize = 12.sp, fontFamily = t.font, fontWeight = FontWeight.Bold)
    }
}

// ---- 화면들 ----

@Composable
private fun HomeScreen(vm: AppViewModel, t: GameTheme) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "집안 테트리스",
            color = t.ink,
            fontSize = 33.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = t.font,
        )
        Text(
            "혼자서는 컴퓨터와, 같이 할 땐 같은 Wi-Fi에서 최대 ${MAX_PLAYERS}명",
            color = t.inkDim,
            fontSize = 13.sp,
            fontFamily = t.font,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp, bottom = 22.dp),
        )

        OutlinedTextField(
            value = vm.playerName,
            onValueChange = { vm.playerName = it.take(8) },
            label = { Text("내 이름", fontFamily = t.font) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))

        PrimaryButton(
            "싱글 모드 · 컴퓨터와 1:1",
            t,
            Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) { vm.openSoloSetup() }

        Spacer(Modifier.height(10.dp))

        GhostButton(
            "멀티 모드 · 같은 Wi-Fi에서 최대 ${MAX_PLAYERS}명",
            t,
            Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) { vm.openMultiHome() }

        vm.status?.let {
            Text(
                it,
                color = t.pieceColors[6],
                fontSize = 13.sp,
                fontFamily = t.font,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 14.dp),
            )
        }

        Spacer(Modifier.height(28.dp))
        ThemeChips(vm, t)
    }
}

@Composable
private fun MultiHomeScreen(vm: AppViewModel, t: GameTheme) {
    Column(Modifier.fillMaxSize()) {
        TextButton(onClick = { vm.leave() }) { Text("← 뒤로", color = t.inkDim, fontFamily = t.font) }
        Spacer(Modifier.weight(1f))

        Text("멀티 모드", color = t.ink, fontSize = 27.sp, fontWeight = FontWeight.Bold, fontFamily = t.font)
        Text(
            "모두 같은 Wi-Fi에 붙어 있어야 합니다.\n한 명이 방을 만들고 나머지가 들어오면 됩니다.",
            color = t.inkDim,
            fontSize = 13.sp,
            fontFamily = t.font,
            modifier = Modifier.padding(top = 6.dp, bottom = 22.dp),
        )

        PrimaryButton("방 만들기 (방장)", t, Modifier.fillMaxWidth().height(54.dp)) { vm.hostRoom() }
        Spacer(Modifier.height(10.dp))
        GhostButton("방 찾아 들어가기", t, Modifier.fillMaxWidth().height(54.dp)) { vm.openScan() }

        Spacer(Modifier.weight(1f))
        localIpv4()?.let {
            Text(
                "내 IP: $it",
                color = t.inkDim,
                fontSize = 12.sp,
                fontFamily = t.font,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun SoloSetupScreen(vm: AppViewModel, t: GameTheme) {
    Column(Modifier.fillMaxSize()) {
        TextButton(onClick = { vm.leave() }) { Text("← 뒤로", color = t.inkDim, fontFamily = t.font) }

        Text("싱글 모드", color = t.ink, fontSize = 27.sp, fontWeight = FontWeight.Bold, fontFamily = t.font)
        Text(
            "컴퓨터와 1:1로 붙습니다. 규칙은 멀티와 똑같아서,\n2줄 이상 지우거나 3콤보를 넘기면 서로 방해 줄을 보냅니다.",
            color = t.inkDim,
            fontSize = 13.sp,
            fontFamily = t.font,
            modifier = Modifier.padding(top = 6.dp, bottom = 18.dp),
        )

        ModePicker(vm, t)
        Spacer(Modifier.height(16.dp))

        Text("난이도", color = t.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = t.font)
        Spacer(Modifier.height(10.dp))

        for (row in 0 until 2) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (col in 1..5) {
                    val level = row * 5 + col
                    val on = vm.botLevel == level
                    Box(
                        Modifier
                            .weight(1f)
                            .height(50.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(if (on) t.accent else t.panel)
                            .border(1.dp, if (on) t.accent else t.edge, RoundedCornerShape(13.dp))
                            .clickable { vm.botLevel = level },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "$level",
                            color = if (on) t.bg else t.ink,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = t.font,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Panel(t, Modifier.fillMaxWidth(), radius = 14) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Lv ${vm.botLevel}",
                    color = t.accent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = t.font,
                    modifier = Modifier.width(54.dp),
                )
                Text(BotConfig.describe(vm.botLevel), color = t.ink, fontSize = 13.sp, fontFamily = t.font)
            }
        }

        Spacer(Modifier.weight(1f))
        PrimaryButton("시작!", t, Modifier.fillMaxWidth().height(56.dp)) { vm.startSolo() }
    }
}

@Composable
private fun ScanScreen(vm: AppViewModel, t: GameTheme) {
    var manualIp by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { vm.leave() }) { Text("← 뒤로", color = t.inkDim, fontFamily = t.font) }
            Spacer(Modifier.weight(1f))
            if (vm.scanning) {
                CircularProgressIndicator(Modifier.size(18.dp), color = t.accent, strokeWidth = 2.dp)
            } else {
                TextButton(onClick = { vm.startScan() }) {
                    Text("다시 찾기", color = t.accent, fontFamily = t.font)
                }
            }
        }
        Text("같은 Wi-Fi의 방", color = t.ink, fontSize = 21.sp, fontWeight = FontWeight.Bold, fontFamily = t.font)
        Spacer(Modifier.height(10.dp))

        if (vm.rooms.isEmpty()) {
            Text(
                if (vm.scanning) "방을 찾는 중…" else "방을 못 찾았어요. 아래에 방장 폰의 IP를 직접 넣어보세요.",
                color = t.inkDim,
                fontSize = 13.sp,
                fontFamily = t.font,
            )
        }

        LazyColumn(Modifier.weight(1f)) {
            items(vm.rooms) { room ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(t.panel)
                        .border(1.dp, t.edge, RoundedCornerShape(14.dp))
                        .clickable { vm.joinRoom(room.address, room.port) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(room.name, color = t.ink, fontSize = 15.sp, fontFamily = t.font)
                        Text(
                            "${room.address} · ${room.players}명",
                            color = t.inkDim,
                            fontSize = 12.sp,
                            fontFamily = t.font,
                        )
                    }
                    Text("들어가기", color = t.accent, fontSize = 14.sp, fontFamily = t.font)
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Label("IP 직접 입력", t, 12)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = manualIp,
                onValueChange = { manualIp = it },
                placeholder = { Text("192.168.0.x", fontFamily = t.font) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            PrimaryButton("접속", t, Modifier.height(52.dp)) {
                if (manualIp.isNotBlank()) vm.joinRoom(manualIp.trim(), DEFAULT_GAME_PORT)
            }
        }
    }
}

@Composable
private fun LobbyScreen(vm: AppViewModel, t: GameTheme) {
    Column(Modifier.fillMaxSize()) {
        TextButton(onClick = { vm.leave() }) { Text("← 나가기", color = t.inkDim, fontFamily = t.font) }
        Text(
            if (vm.isHost) "내 방" else "대기실",
            color = t.ink,
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = t.font,
        )
        Text(
            if (vm.isHost) "다른 사람이 '방 찾아 들어가기'로 붙으면 여기 보여요"
            else "방장이 시작하기를 기다리는 중…",
            color = t.inkDim,
            fontSize = 13.sp,
            fontFamily = t.font,
            modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
        )

        if (vm.isHost) {
            localIpv4()?.let {
                Text(
                    "자동으로 안 잡히면 이 주소를 불러주세요 → $it",
                    color = t.accent,
                    fontSize = 13.sp,
                    fontFamily = t.font,
                )
                Spacer(Modifier.height(10.dp))
            }
        }

        Column(Modifier.weight(1f)) {
            vm.lobbyPlayers.forEach { p ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(t.panel)
                        .border(1.dp, t.edge, RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(p.name, color = t.ink, fontSize = 15.sp, fontFamily = t.font, modifier = Modifier.weight(1f))
                    if (p.isHost) {
                        Text(
                            "방장",
                            color = t.accentInk,
                            fontSize = 11.sp,
                            fontFamily = t.font,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(t.accentSoft)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    if (p.id == vm.myId) {
                        Text("  (나)", color = t.inkDim, fontSize = 12.sp, fontFamily = t.font)
                    }
                }
            }
            if (vm.lobbyPlayers.isEmpty()) {
                Text("연결 중…", color = t.inkDim, fontSize = 14.sp, fontFamily = t.font)
            }
        }

        ModePicker(vm, t, enabled = vm.isHost)
        if (!vm.isHost) {
            Text(
                "대전 방식은 방장이 정합니다",
                color = t.inkDim,
                fontSize = 11.sp,
                fontFamily = t.font,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(Modifier.height(12.dp))

        if (vm.isHost) {
            PrimaryButton(
                if (vm.lobbyPlayers.size <= 1) "혼자 연습하기" else "시작!",
                t,
                Modifier.fillMaxWidth().height(56.dp),
                enabled = vm.lobbyPlayers.isNotEmpty(),
            ) { vm.startGame() }
        }
    }
}

@Composable
private fun ResultScreen(vm: AppViewModel, t: GameTheme) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(32.dp))
        Text("결과", color = t.ink, fontSize = 29.sp, fontWeight = FontWeight.Bold, fontFamily = t.font)
        // 무엇 때문에 끝났는지 적어 둔다. 안 그러면 "점수 때문에 끝났나?" 하고 헷갈린다.
        Text(
            if (vm.standings.size >= 2) "${vm.standings.first().name} 님이 마지막까지 남았습니다"
            else "판이 끝났습니다",
            color = t.inkDim,
            fontSize = 13.sp,
            fontFamily = t.font,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(16.dp))
        vm.standings.forEach { s ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (s.rank == 1) t.accentSoft else t.panel)
                    .border(1.dp, if (s.rank == 1) t.accent else t.edge, RoundedCornerShape(16.dp))
                    .padding(15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (s.rank == 1) "🏆" else "${s.rank}위",
                    color = if (s.rank == 1) t.accent else t.inkDim,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = t.font,
                    modifier = Modifier.width(50.dp),
                )
                Text(s.name, color = t.ink, fontSize = 17.sp, fontFamily = t.font, modifier = Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text("${s.score}", color = t.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = t.font)
                    Text("${s.lines}줄", color = t.inkDim, fontSize = 12.sp, fontFamily = t.font)
                }
            }
        }
        Spacer(Modifier.weight(1f))

        // 게임 패드가 있던 자리에 그대로 뜬다. 잠깐 막고, 제일 아래에는 오탭해도 무해한 것을 둔다.
        GhostButton(
            if (vm.isSolo) "그만하기" else "방 나가기",
            t,
            Modifier.fillMaxWidth().height(48.dp),
            enabled = vm.resultReady,
        ) { vm.leave() }
        Spacer(Modifier.height(8.dp))
        if (vm.isSolo) {
            GhostButton(
                "난이도 바꾸기",
                t,
                Modifier.fillMaxWidth().height(48.dp),
                enabled = vm.resultReady,
            ) { vm.leave(); vm.openSoloSetup() }
            Spacer(Modifier.height(8.dp))
        }
        PrimaryButton(
            if (vm.isSolo) "한 판 더 (Lv ${vm.botLevel})" else "대기실로",
            t,
            Modifier.fillMaxWidth().height(56.dp),
            enabled = vm.resultReady,
        ) { if (vm.isSolo) vm.playSoloAgain() else vm.backToLobby() }
    }
}

// ---- 게임 화면 ----

@Composable
private fun GameScreen(vm: AppViewModel, t: GameTheme) {
    val engine = vm.engine ?: return

    // 내 판이 끝나도 게임은 마지막 한 명이 남을 때까지 이어진다.
    // 그동안은 내 죽은 판 대신 살아있는 사람들 판을 크게 보여준다.
    if (engine.dead) {
        SpectatorScreen(vm, t, engine)
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // 폴더블을 펼쳤거나 태블릿이면 폭이 남아돈다. 그때는 점수와 상대를 보드 좌우로 보내서
        // 보드가 세로를 전부 쓰게 한다. 좁은 화면에서 같은 짓을 하면 보드가 오히려 작아진다.
        if (maxWidth >= WIDE_SCREEN_MIN) WideGame(vm, t, engine) else TallGame(vm, t, engine)
    }
}

/** 보통 폰. 위에서 아래로 쌓는다. */
@Composable
private fun TallGame(vm: AppViewModel, t: GameTheme, engine: TetrisEngine) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ScorePanel(vm, t, Modifier.fillMaxWidth())
        HoldNextRow(vm, t, Modifier.fillMaxWidth())
        Row(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BoardArea(vm, t, engine, Modifier.weight(1f).fillMaxHeight())
            OpponentStrip(vm, t, width = if (vm.opponents.size == 1) 118.dp else 96.dp)
        }
        ControlPad(vm, t)
    }
}

/** 폴더블 펼침 · 태블릿. 보드를 가운데 두고 정보와 상대를 좌우로 보낸다. */
@Composable
private fun WideGame(vm: AppViewModel, t: GameTheme, engine: TetrisEngine) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 좌우를 좁힐수록 보드가 커진다. 정보가 읽히는 선에서 최대한 양보한다.
            Column(
                Modifier.width(176.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ScorePanel(vm, t, Modifier.fillMaxWidth())
                // 세로를 다 먹게 두면 아래가 텅 빈 채 늘어난다. 내용만큼만 쓴다.
                HoldNextColumn(vm, t, Modifier.fillMaxWidth())
            }
            BoardArea(vm, t, engine, Modifier.weight(1f).fillMaxHeight())
            OpponentStrip(
                vm, t,
                width = when (vm.opponents.size) {
                    1 -> 140.dp
                    2 -> 118.dp
                    else -> 100.dp
                },
                stretch = false,
            )
        }
        ControlPad(vm, t)
    }
}

/**
 * 점수 / 줄 / 레벨 / 마스코트.
 * 높이를 고정하면 글꼴 크기를 키운 기기에서 "줄"과 "LV"가 잘려 나간다. 최소 높이만 준다.
 */
@Composable
private fun ScorePanel(vm: AppViewModel, t: GameTheme, modifier: Modifier = Modifier) {
    Panel(t, modifier.heightIn(min = 96.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Label("점수", t)
                Text(
                    "%,d".format(vm.hudScore),
                    color = t.ink,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = t.numberFont,
                    maxLines = 1,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Stat("줄", "${vm.hudLines}", t)
                    Spacer(Modifier.width(14.dp))
                    Stat("LV", "${vm.hudLevel}", t, accent = true)
                }
            }
            if (SHOW_RUNNING_DOG) {
                Spacer(Modifier.width(8.dp))
                RunningDog(
                    // 블록이 빨리 떨어질수록 빨리 뛴다. 아래 버튼을 누르는 동안은 전력질주.
                    frameMs = if (vm.softDropping) 45L else (vm.hudGravityMs / 6).coerceIn(45L, 200L),
                    running = vm.countdown == 0 && vm.engine?.dead != true,
                    theme = t,
                    modifier = Modifier.size(64.dp),
                )
            }
        }
    }
}

/**
 * 좁은 화면용 HOLD | NEXT 한 줄.
 * 여기서는 **폭**이 모자라므로 미리보기를 폭에 맞추고 높이가 따라오게 한다.
 * (넓은 화면의 세로 배치는 반대로 높이가 모자라서 기준이 반대다.)
 */
@Composable
private fun HoldNextRow(vm: AppViewModel, t: GameTheme, modifier: Modifier = Modifier) {
    Panel(t, modifier, radius = 16) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.width(52.dp)) {
                Label("HOLD", t, 9)
                Spacer(Modifier.height(3.dp))
                PiecePreview(vm.hudHold, t, Modifier.fillMaxWidth().aspectRatio(2f))
            }
            Box(
                Modifier
                    .padding(horizontal = 8.dp)
                    .width(1.dp)
                    .height(34.dp)
                    .background(t.edge)
            )
            Column(Modifier.weight(1f)) {
                Label("NEXT", t, 9)
                Spacer(Modifier.height(3.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    vm.hudNext.forEach {
                        PiecePreview(it, t, Modifier.weight(1f).aspectRatio(2f))
                    }
                }
            }
        }
    }
}

/** 넓은 화면용. HOLD 아래로 NEXT를 세로로 세운다. */
@Composable
private fun HoldNextColumn(vm: AppViewModel, t: GameTheme, modifier: Modifier = Modifier) {
    Panel(t, modifier, radius = 16) {
        Label("HOLD", t, 9)
        Spacer(Modifier.height(3.dp))
        PiecePreview(vm.hudHold, t, Modifier.fillMaxWidth().height(52.dp))
        Spacer(Modifier.height(10.dp))
        Label("NEXT", t, 9)
        Spacer(Modifier.height(3.dp))
        // 폭에 맞춰 크기를 정한다. 높이를 나눠 갖게 하면 칸이 남는 기기에서 우스꽝스럽게 커진다.
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            vm.hudNext.forEach {
                PiecePreview(it, t, Modifier.fillMaxWidth().aspectRatio(2f))
            }
        }
    }
}

/** 내 보드와 그 위에 겹치는 안내들(카운트다운, 맞은 공격, 방금 지운 줄). */
@Composable
private fun BoardArea(
    vm: AppViewModel,
    t: GameTheme,
    engine: TetrisEngine,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.clipToBounds(), contentAlignment = Alignment.Center) {
        // aspectRatio 는 폭·높이 중 한쪽만 기준으로 잡을 수 있어서, 다른 쪽이 모자라면
        // 틀만 늘어나고 판은 가운데에 뜬다(태블릿에서 위아래가 잘린 것처럼 보였다).
        // 그래서 들어갈 수 있는 최대 크기를 직접 구한다. 10:20 이므로 높이는 폭의 두 배.
        val boardHeight = minOf(maxHeight, maxWidth * 2)
        val boardWidth = boardHeight / 2

        Box(
            Modifier
                .size(boardWidth, boardHeight)
                .clip(RoundedCornerShape(18.dp))
                .background(t.boardFrame)
                .border(1.dp, t.edge, RoundedCornerShape(18.dp))
                .padding(5.dp),
        ) {
            MyBoard(
                engine = engine,
                theme = t,
                frameKey = vm.frame,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(13.dp))
                    .background(t.boardBg),
            )
        }

        // 안개: 판을 거의 가린다. 조각이 어디 있었는지 기억해서 둬야 한다.
        if (vm.hudFogMs > 0) {
            Box(
                Modifier
                    .size(boardWidth, boardHeight)
                    .clip(RoundedCornerShape(18.dp))
                    .background(t.ink.copy(alpha = 0.93f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "안개  ${(vm.hudFogMs / 1000) + 1}",
                    color = t.bg,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = t.font,
                )
            }
        }

        // 회전금지·가속은 눈에 안 보이는 효과라 글로 알려 준다.
        val effect = when {
            vm.hudNoRotateMs > 0 -> "회전금지 ${(vm.hudNoRotateMs / 1000) + 1}"
            vm.hudRushMs > 0 -> "가속 ${(vm.hudRushMs / 1000) + 1}"
            else -> null
        }
        effect?.let {
            Text(
                it,
                color = t.pieceColors[6],
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = t.font,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 30.dp),
            )
        }

        if (vm.countdown > 0) {
            Text(
                "${vm.countdown}",
                color = t.accent,
                fontSize = 80.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = t.font,
            )
        }
        vm.attackFlash?.let {
            Text(
                "${it.from} → +${it.lines}줄",
                color = t.pieceColors[6],
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = t.font,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 6.dp),
            )
        }
        vm.lastLockResult?.let {
            Text(
                it,
                color = t.accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = t.font,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
            )
        }
    }
}

/**
 * 상대들. 보드가 세로로 길어 좌우가 남으므로 그 자리를 쓴다.
 *
 * @param stretch 남는 세로를 나눠 가질지. 좁은 화면(보드 옆)은 높이가 빠듯해서 나눠 갖고,
 *   넓은 화면은 세로가 남아돌아서 콘텐츠 높이만 쓴다. 안 그러면 판 하나가 화면 끝까지 늘어난다.
 */
@Composable
private fun OpponentStrip(vm: AppViewModel, t: GameTheme, width: Dp, stretch: Boolean = true) {
    if (vm.opponents.isEmpty()) return
    Column(
        Modifier.width(width).fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        vm.opponents.forEach { p ->
            Panel(
                t,
                if (stretch) Modifier.weight(1f).fillMaxWidth() else Modifier.fillMaxWidth(),
                radius = 14,
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        p.name,
                        color = if (p.alive) t.ink else t.inkDim,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = t.font,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    if (p.pending > 0) {
                        Text(
                            "+${p.pending}",
                            color = t.pieceColors[6],
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = t.font,
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Box(
                    if (stretch) Modifier.weight(1f).fillMaxWidth() else Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    MiniBoard(
                        board = p.board,
                        theme = t,
                        alive = p.alive,
                        modifier = if (stretch) {
                            Modifier.fillMaxHeight().aspectRatio(0.5f)
                        } else {
                            Modifier.fillMaxWidth().aspectRatio(0.5f)
                        },
                    )
                    if (!p.alive) {
                        Text(
                            "OUT",
                            color = t.pieceColors[6],
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = t.font,
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    "%,d".format(p.score),
                    color = t.inkDim,
                    fontSize = 10.sp,
                    fontFamily = t.numberFont,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * 내 판이 끝난 뒤의 관전 화면.
 * 아직 살아있는 사람들의 판을 크게 보여주고, 이미 탈락한 사람들(나 포함)은 아래에 작게 둔다.
 * 마지막 한 명이 남으면 서버가 결과를 보내면서 자동으로 결과 화면으로 넘어간다.
 */
@Composable
private fun SpectatorScreen(vm: AppViewModel, t: GameTheme, engine: TetrisEngine) {
    val alive = vm.opponents.filter { it.alive }
    val eliminated = vm.opponents.filter { !it.alive }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {

        Panel(t, Modifier.fillMaxWidth().height(84.dp)) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "관전 중",
                        color = t.accent,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = t.font,
                    )
                    Text(
                        when {
                            alive.isEmpty() -> "곧 결과가 나옵니다…"
                            alive.size == 1 -> "${alive[0].name} 님이 마지막으로 남았습니다"
                            else -> "${alive.size}명이 남았습니다"
                        },
                        color = t.inkDim,
                        fontSize = 13.sp,
                        fontFamily = t.font,
                        maxLines = 1,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Label("내 기록", t, 9)
                    Text(
                        "%,d".format(engine.score),
                        color = t.ink,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = t.font,
                    )
                    Text("${engine.lines}줄", color = t.inkDim, fontSize = 11.sp, fontFamily = t.font)
                }
            }
        }

        // 살아있는 사람들 - 여기가 주인공이다
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (alive.isEmpty()) {
                Text(
                    "모두 끝났습니다",
                    color = t.inkDim,
                    fontSize = 15.sp,
                    fontFamily = t.font,
                )
            } else {
                Row(
                    Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                ) {
                    alive.forEach { p ->
                        Panel(t, Modifier.weight(1f).fillMaxHeight(), radius = 16) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    p.name,
                                    color = t.ink,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = t.font,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f),
                                )
                                if (p.pending > 0) {
                                    Text(
                                        "+${p.pending}",
                                        color = t.pieceColors[6],
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = t.font,
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                MiniBoard(
                                    board = p.board,
                                    theme = t,
                                    alive = true,
                                    modifier = Modifier.fillMaxHeight().aspectRatio(0.5f),
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "%,d".format(p.score),
                                color = t.inkDim,
                                fontSize = 12.sp,
                                fontFamily = t.font,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        // 이미 탈락한 사람들. 내 마지막 판도 여기 같이 둔다.
        Row(
            Modifier.fillMaxWidth().height(104.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EliminatedCard("나", engine.snapshot(), engine.score, t, Modifier.weight(1f))
            eliminated.forEach { p ->
                EliminatedCard(p.name, p.board, p.score, t, Modifier.weight(1f))
            }
            // 탈락자가 적을 땐 카드가 지나치게 넓어지지 않게 빈 자리를 채운다
            repeat((2 - eliminated.size).coerceAtLeast(0)) {
                Spacer(Modifier.weight(1f))
            }
        }

        GhostButton("방 나가기", t, Modifier.fillMaxWidth().height(46.dp)) { vm.leave() }
    }
}

@Composable
private fun EliminatedCard(
    name: String,
    board: String,
    score: Long,
    t: GameTheme,
    modifier: Modifier = Modifier,
) {
    Panel(t, modifier.fillMaxHeight(), radius = 12) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            MiniBoard(
                board = board,
                theme = t,
                alive = false,
                modifier = Modifier.fillMaxHeight().aspectRatio(0.5f),
            )
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    color = t.inkDim,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = t.font,
                    maxLines = 1,
                )
                Text("OUT", color = t.pieceColors[6], fontSize = 10.sp, fontFamily = t.font)
                Text(
                    "%,d".format(score),
                    color = t.inkDim,
                    fontSize = 10.sp,
                    fontFamily = t.font,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, t: GameTheme, accent: Boolean = false) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(label, color = t.inkDim, fontSize = 10.sp, fontFamily = t.font)
        Spacer(Modifier.width(4.dp))
        Text(
            value,
            color = if (accent) t.accent else t.ink,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = t.numberFont,
        )
    }
}

/**
 * 시안의 배치를 그대로 옮겼다.
 * 왼쪽은 이동(◀ ▼ ▶) 위에 반시계 회전, 오른쪽은 시계 회전 · HOLD · 하드드롭.
 * 드롭은 가장 크고 가장 바깥쪽에 둬서 오조작을 줄인다.
 */
@Composable
private fun ControlPad(vm: AppViewModel, t: GameTheme) = Box(
    Modifier.fillMaxWidth(),
    contentAlignment = Alignment.BottomCenter,
) {
    // 화면이 아주 넓으면 양 끝으로 벌어져 손이 닿지 않는다. 벌어지는 폭에 상한을 둔다.
    Row(
        Modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .height(190.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Box(Modifier.size(152.dp, 190.dp)) {
            // 십자라서 네 모서리가 빈다. 아이템 버튼은 그 자리에 넣어 폭을 더 먹지 않게 한다.
            if (vm.itemMode) {
                ItemButton(vm, t, Modifier.offset(0.dp, 2.dp).size(44.dp, 54.dp))
            }
            PadButton("↺", t, Modifier.offset(47.dp, 0.dp).size(58.dp), radius = 20) {
                vm.tap(Action.ROTATE_CCW)
            }
            PadButton(
                "◀", t,
                Modifier.offset(0.dp, 66.dp).size(64.dp, 56.dp),
                onPress = { vm.pressDirection(-1) },
                onRelease = { vm.releaseDirection() },
            )
            PadButton(
                "▶", t,
                Modifier.offset(88.dp, 66.dp).size(64.dp, 56.dp),
                onPress = { vm.pressDirection(1) },
                onRelease = { vm.releaseDirection() },
            )
            PadButton(
                "▼", t,
                Modifier.offset(44.dp, 130.dp).size(64.dp, 56.dp),
                onPress = { vm.pressSoftDrop() },
                onRelease = { vm.releaseSoftDrop() },
            )
        }

        Spacer(Modifier.weight(1f))

        Box(Modifier.size(170.dp, 132.dp)) {
            PadButton("↻", t, Modifier.offset(0.dp, 0.dp).size(58.dp), radius = 20) {
                vm.tap(Action.ROTATE_CW)
            }
            PadButton(
                "HOLD", t,
                Modifier.offset(0.dp, 66.dp).size(58.dp, 44.dp),
                fontSize = 11,
                radius = 16,
            ) { vm.tap(Action.HOLD) }
            DropButton(t, Modifier.offset(82.dp, 40.dp).size(88.dp)) { vm.tap(Action.HARD_DROP) }
        }
    }
}

@Composable
private fun PadButton(
    label: String,
    t: GameTheme,
    modifier: Modifier = Modifier,
    fontSize: Int = 19,
    radius: Int = 18,
    onRelease: (() -> Unit)? = null,
    onPress: () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier
            .clip(RoundedCornerShape(radius.dp))
            .background(if (pressed) t.accentSoft else t.btnBg)
            .border(1.dp, if (pressed) t.accent else t.btnEdge, RoundedCornerShape(radius.dp))
            .pointerInput(onRelease) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        onPress()
                        tryAwaitRelease()
                        pressed = false
                        onRelease?.invoke()
                    }
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = t.btnInk,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = if (label.length > 2) t.font else FontFamily.Default,
        )
    }
}

/**
 * 아이템 슬롯 겸 사용 버튼. 비어 있으면 눌러도 아무 일도 없고 흐릿하게 보인다.
 * 슬롯이 하나뿐이라 "지금 쓸까" 를 고민하게 만드는 게 이 모드의 핵심이다.
 */
@Composable
private fun ItemButton(vm: AppViewModel, t: GameTheme, modifier: Modifier = Modifier) {
    val item: ItemKind? = vm.hudItem
    val ready = item != null
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (pressed) t.accent.copy(alpha = 0.35f) else if (ready) t.accentSoft else t.btnBg)
            .border(1.dp, if (ready) t.accent else t.btnEdge, RoundedCornerShape(12.dp))
            .pointerInput(ready) {
                detectTapGestures(
                    onPress = {
                        if (!ready) return@detectTapGestures
                        pressed = true
                        vm.useItem()
                        tryAwaitRelease()
                        pressed = false
                    }
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                item?.icon ?: "·",
                color = if (ready) t.accent else t.inkDim,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                item?.label ?: "아이템",
                color = if (ready) t.accentInk else t.inkDim,
                fontSize = 8.sp,
                fontFamily = t.font,
                maxLines = 1,
            )
        }
    }
}

/** 하드드롭. 가장 크고 둥글게, 화면 바깥쪽에 둔다. */
@Composable
private fun DropButton(t: GameTheme, modifier: Modifier = Modifier, onPress: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier
            .clip(CircleShape)
            .background(if (pressed) t.accent.copy(alpha = 0.35f) else t.accentSoft)
            .border(1.5.dp, t.accent, CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        onPress()
                        tryAwaitRelease()
                        pressed = false
                    }
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("↓", color = t.accentInk, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                "DROP",
                color = t.accentInk,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = t.font,
            )
        }
    }
}

/**
 * 마스코트를 점수 패널 안에 보여줄지.
 * 스프라이트와 코드는 `ui/PixelDog.kt` 에 있다.
 */
private const val SHOW_RUNNING_DOG = true

/** 이 폭을 넘으면 폴더블 펼침·태블릿으로 보고 보드를 가운데 둔 가로 배치를 쓴다. */
private val WIDE_SCREEN_MIN = 600.dp
