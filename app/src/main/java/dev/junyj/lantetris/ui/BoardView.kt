package dev.junyj.lantetris.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import dev.junyj.lantetris.core.BOARD_W
import dev.junyj.lantetris.core.HIDDEN_H
import dev.junyj.lantetris.core.PieceType
import dev.junyj.lantetris.core.TetrisEngine
import dev.junyj.lantetris.core.VISIBLE_H
import dev.junyj.lantetris.core.pieceCells
import kotlin.math.min

private fun lighten(c: Color, amount: Float) = lerp(c, Color.White, amount)
private fun darken(c: Color, amount: Float) = lerp(c, Color.Black, amount)

/**
 * 블록 한 칸. 테마마다 질감이 다르다 - 광택, 평면, 네온, 부드러움, 외곽선.
 * 칸이 작을 때 디테일이 뭉개지지 않도록 선 굵기는 칸 크기에 비례시킨다.
 */
private fun DrawScope.drawCell(
    theme: GameTheme,
    color: Color,
    left: Float,
    top: Float,
    cell: Float,
    ghost: Boolean = false,
) {
    val inset = (cell * 0.06f).coerceIn(0.5f, 2f)
    val x = left + inset
    val y = top + inset
    val s = cell - inset * 2
    if (s <= 0f) return

    if (ghost) {
        val r = if (theme.style == BlockStyle.OUTLINE) 0f else cell * 0.16f
        drawRoundRect(
            color = color.copy(alpha = 0.45f),
            topLeft = Offset(x, y),
            size = Size(s, s),
            cornerRadius = CornerRadius(r),
            style = Stroke(width = (cell * 0.09f).coerceAtLeast(1f)),
        )
        return
    }

    when (theme.style) {
        BlockStyle.GLOSS -> {
            drawRoundRect(
                brush = Brush.linearGradient(
                    0f to lighten(color, 0.38f),
                    0.48f to color,
                    1f to darken(color, 0.22f),
                    start = Offset(x, y),
                    end = Offset(x + s, y + s),
                ),
                topLeft = Offset(x, y),
                size = Size(s, s),
                cornerRadius = CornerRadius(cell * 0.16f),
            )
            // 위쪽 하이라이트와 아래쪽 그림자로 살짝 볼록해 보이게
            val band = (s * 0.14f).coerceAtLeast(1f)
            drawRect(
                color = Color.White.copy(alpha = 0.30f),
                topLeft = Offset(x + band, y + band * 0.5f),
                size = Size(s - band * 2, band * 0.7f),
            )
            drawRect(
                color = Color.Black.copy(alpha = 0.20f),
                topLeft = Offset(x + band, y + s - band * 1.2f),
                size = Size(s - band * 2, band * 0.7f),
            )
        }

        BlockStyle.FLAT -> {
            drawRoundRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(s, s),
                cornerRadius = CornerRadius(cell * 0.07f),
            )
            drawRoundRect(
                color = darken(color, 0.38f),
                topLeft = Offset(x, y),
                size = Size(s, s),
                cornerRadius = CornerRadius(cell * 0.07f),
                style = Stroke(width = (cell * 0.07f).coerceAtLeast(1f)),
            )
        }

        BlockStyle.GLOW -> {
            // 진짜 blur는 매 프레임 돌리기엔 비싸다. 반투명 사각형을 겹쳐 번짐을 흉내낸다.
            val halo = cell * 0.28f
            drawRoundRect(
                color = color.copy(alpha = 0.22f),
                topLeft = Offset(x - halo, y - halo),
                size = Size(s + halo * 2, s + halo * 2),
                cornerRadius = CornerRadius(cell * 0.3f),
            )
            drawRoundRect(
                color = color.copy(alpha = 0.35f),
                topLeft = Offset(x - halo * 0.5f, y - halo * 0.5f),
                size = Size(s + halo, s + halo),
                cornerRadius = CornerRadius(cell * 0.22f),
            )
            drawRoundRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(s, s),
                cornerRadius = CornerRadius(cell * 0.07f),
            )
            drawRoundRect(
                color = lighten(color, 0.55f),
                topLeft = Offset(x + s * 0.22f, y + s * 0.22f),
                size = Size(s * 0.56f, s * 0.56f),
                cornerRadius = CornerRadius(cell * 0.06f),
            )
        }

        BlockStyle.SOFT -> {
            drawRoundRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(s, s),
                cornerRadius = CornerRadius(cell * 0.30f),
            )
            val lip = (s * 0.18f).coerceAtLeast(1f)
            drawRoundRect(
                color = darken(color, 0.16f),
                topLeft = Offset(x, y + s - lip),
                size = Size(s, lip),
                cornerRadius = CornerRadius(cell * 0.16f),
            )
        }

        BlockStyle.OUTLINE -> {
            drawRect(
                color = color.copy(alpha = 0.16f),
                topLeft = Offset(x, y),
                size = Size(s, s),
            )
            drawRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(s, s),
                style = Stroke(width = (cell * 0.1f).coerceAtLeast(1f)),
            )
        }
    }
}

/** 내 보드. 격자, 쌓인 블록, 고스트, 현재 조각, 대기 중인 방해 줄 게이지까지. */
@Composable
fun MyBoard(
    engine: TetrisEngine,
    theme: GameTheme,
    frameKey: Long,
    showGrid: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        // frameKey를 람다 안에서 실제로 읽어야 매 프레임 새 람다가 되어 다시 그려진다.
        if (frameKey == Long.MIN_VALUE) return@Canvas

        val cell = min(size.width / BOARD_W, size.height / VISIBLE_H)
        val originX = (size.width - cell * BOARD_W) / 2f
        val originY = (size.height - cell * VISIBLE_H) / 2f
        fun px(x: Int) = originX + x * cell
        fun py(y: Int) = originY + y * cell

        if (showGrid) {
            val w = (cell * 0.04f).coerceIn(0.5f, 1.5f)
            for (x in 0..BOARD_W) {
                drawLine(theme.grid, Offset(px(x), py(0)), Offset(px(x), py(VISIBLE_H)), w)
            }
            for (y in 0..VISIBLE_H) {
                drawLine(theme.grid, Offset(px(0), py(y)), Offset(px(BOARD_W), py(y)), w)
            }
        }

        for (y in HIDDEN_H until HIDDEN_H + VISIBLE_H) {
            for (x in 0 until BOARD_W) {
                theme.cellColor(engine.board[y][x])?.let {
                    drawCell(theme, it, px(x), py(y - HIDDEN_H), cell)
                }
            }
        }

        if (!engine.dead) {
            val color = theme.pieceColors[engine.type.ordinal]
            for (c in engine.cells(oy = engine.ghostY())) {
                val y = c[1] - HIDDEN_H
                if (y in 0 until VISIBLE_H) drawCell(theme, color, px(c[0]), py(y), cell, ghost = true)
            }
            for (c in engine.cells()) {
                val y = c[1] - HIDDEN_H
                if (y in 0 until VISIBLE_H) drawCell(theme, color, px(c[0]), py(y), cell)
            }
        }

        // 대기 중인 방해 줄은 보드 왼쪽 바깥의 게이지로 보여준다.
        if (engine.pendingGarbage > 0) {
            val h = engine.pendingGarbage.coerceAtMost(VISIBLE_H) * cell
            val w = (cell * 0.22f).coerceAtLeast(3f)
            drawRoundRect(
                color = theme.pieceColors[6],
                topLeft = Offset(px(0) - w * 1.8f, py(VISIBLE_H) - h),
                size = Size(w, h),
                cornerRadius = CornerRadius(w / 2),
            )
        }
    }
}

/** 상대 보드. 200자 스냅샷 문자열을 그대로 그린다. 작아서 질감은 생략하고 단색으로. */
@Composable
fun MiniBoard(board: String, theme: GameTheme, alive: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(theme.boardBg)
            .padding(2.dp)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cell = min(size.width / BOARD_W, size.height / VISIBLE_H)
            val offX = (size.width - cell * BOARD_W) / 2f
            val offY = (size.height - cell * VISIBLE_H) / 2f
            val dim = if (alive) 1f else 0.3f
            for (i in 0 until minOf(board.length, VISIBLE_H * BOARD_W)) {
                val v = board[i] - '0'
                val color = theme.cellColor(v) ?: continue
                drawRect(
                    color = color.copy(alpha = dim),
                    topLeft = Offset(offX + (i % BOARD_W) * cell, offY + (i / BOARD_W) * cell),
                    size = Size(cell, cell),
                )
            }
        }
    }
}

/**
 * HOLD / NEXT 칸. 시안처럼 4x2 격자에 조각을 넣어, 조각이 달라도 칸 크기가 흔들리지 않게 한다.
 */
@Composable
fun PiecePreview(type: PieceType?, theme: GameTheme, modifier: Modifier = Modifier) {
    // 비율은 부르는 쪽이 정한다. 여기서 고정하면 세로로 쌓을 때 칸 밖으로 넘친다.
    Box(
        modifier
            .clip(RoundedCornerShape(9.dp))
            .background(theme.boardBg)
    ) {
        if (type == null) return@Box
        Canvas(Modifier.fillMaxSize().padding(3.dp)) {
            val cell = min(size.width / 4f, size.height / 2f)
            val pts = pieceCells(type)
            val minX = pts.minOf { it[0] }
            val minY = pts.minOf { it[1] }
            val w = pts.maxOf { it[0] } - minX + 1
            val h = pts.maxOf { it[1] } - minY + 1
            val offX = (size.width - cell * w) / 2f
            val offY = (size.height - cell * h) / 2f
            val color = theme.pieceColors[type.ordinal]
            for (p in pts) {
                drawCell(theme, color, offX + (p[0] - minX) * cell, offY + (p[1] - minY) * cell, cell)
            }
        }
    }
}
