package dev.junyj.lantetris.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.junyj.lantetris.R
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 마스코트 조이(Joy)의 달리기.
 *
 * 그림은 `res/drawable-nodpi/joy_run_sheet.png` - 64x64 프레임 8장이 가로로 늘어선 시트다.
 * drawable-nodpi 에 둬서 화면 배율에 따라 미리 늘어나지 않게 했고,
 * 그릴 때도 [FilterQuality.None] 으로 확대해야 픽셀이 뭉개지지 않는다.
 */
object JoySprite {
    const val FRAME_COUNT = 8

    /** 시안에서 권장한 12fps. 낙하 속도에 연동할 때의 기준이 된다. */
    const val BASE_FRAME_MS = 1000L / 12
}

/**
 * @param frameMs 한 프레임이 유지되는 시간. 블록 낙하 속도에서 넘겨준다.
 * @param running false면 첫 프레임에서 멈춰 선다(카운트다운, 게임 오버).
 */
@Composable
fun RunningDog(
    frameMs: Long,
    running: Boolean,
    theme: GameTheme,
    modifier: Modifier = Modifier,
) {
    val sheet = androidx.compose.ui.graphics.ImageBitmap.imageResource(R.drawable.joy_run_sheet)
    var frame by remember { mutableIntStateOf(0) }

    LaunchedEffect(frameMs, running) {
        if (!running) {
            frame = 0
            return@LaunchedEffect
        }
        while (true) {
            delay(frameMs.coerceIn(30L, 400L))
            frame = (frame + 1) % JoySprite.FRAME_COUNT
        }
    }

    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(theme.accentSoft)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawJoyFrame(sheet, frame)
        }
    }
}

private fun DrawScope.drawJoyFrame(
    sheet: androidx.compose.ui.graphics.ImageBitmap,
    frame: Int,
) {
    val fw = sheet.width / JoySprite.FRAME_COUNT
    val fh = sheet.height
    if (fw <= 0 || fh <= 0) return

    // 정수 배율로 키워야 픽셀이 고르게 보인다. 칸이 원본보다 작으면 그때만 축소한다.
    val fit = min(size.width / fw, size.height / fh)
    val scale = if (fit >= 1f) fit.toInt().coerceAtLeast(1).toFloat() else fit
    val w = (fw * scale).roundToInt()
    val h = (fh * scale).roundToInt()

    drawImage(
        image = sheet,
        srcOffset = IntOffset(frame * fw, 0),
        srcSize = IntSize(fw, fh),
        dstOffset = IntOffset(
            ((size.width - w) / 2f).roundToInt(),
            ((size.height - h) / 2f).roundToInt(),
        ),
        dstSize = IntSize(w, h),
        filterQuality = FilterQuality.None,
    )
}
