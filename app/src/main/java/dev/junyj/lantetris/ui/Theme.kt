package dev.junyj.lantetris.ui

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/** 블록을 어떤 질감으로 그릴지. 테마마다 다르다. */
enum class BlockStyle { GLOSS, FLAT, GLOW, SOFT, OUTLINE }

/**
 * 화면 전체의 색과 질감. 디자인 시안(Blocks Game Redesign)의 5개 테마를 그대로 옮겼다.
 * 색을 새로 넣을 때는 [pieceColors] 순서를 반드시 [dev.junyj.lantetris.core.PieceType] 과 맞출 것.
 */
data class GameTheme(
    val key: String,
    val label: String,
    val style: BlockStyle,
    /**
     * 글자용. **한글에는 Monospace 를 쓰면 안 된다.**
     * 한글 글리프는 fallback 되는데 공백만 고정폭으로 그려져서
     * "집안 테트리스" 가 "집안  테트리스" 처럼 벌어진다.
     */
    val font: FontFamily,
    /** 숫자용. 고정폭이면 점수가 바뀔 때 자릿수가 흔들리지 않는다. 테마 성격은 여기서 낸다. */
    val numberFont: FontFamily,
    val bg: Color,
    val bgTop: Color,
    val panel: Color,
    val edge: Color,
    val boardFrame: Color,
    val boardBg: Color,
    val grid: Color,
    val ink: Color,
    val inkDim: Color,
    val accent: Color,
    val accentSoft: Color,
    val accentInk: Color,
    val btnBg: Color,
    val btnEdge: Color,
    val btnInk: Color,
    /** I, J, L, O, S, T, Z 순서. PieceType.ordinal 로 바로 찾는다. */
    val pieceColors: List<Color>,
    val garbage: Color,
) {
    val bgBrush: Brush get() = Brush.verticalGradient(listOf(bgTop, bg))

    /** 보드 셀 값(0=빈칸, 1..7=조각, 8=방해줄)에 대한 색. */
    fun cellColor(v: Int): Color? = when {
        v in 1..7 -> pieceColors[v - 1]
        v == 8 -> garbage
        else -> null
    }
}

private fun c(hex: Long) = Color(hex)

val GameThemes: List<GameTheme> = listOf(
    GameTheme(
        key = "midnight", label = "미드나이트", style = BlockStyle.GLOSS, font = FontFamily.SansSerif, numberFont = FontFamily.SansSerif,
        bg = c(0xFF0B1020), bgTop = c(0xFF111935),
        panel = c(0x0EFFFFFF), edge = c(0x17FFFFFF),
        boardFrame = c(0x0AFFFFFF), boardBg = c(0xFF080D1A), grid = c(0x0DFFFFFF),
        ink = c(0xFFEAF0FF), inkDim = c(0x80EAF0FF),
        accent = c(0xFF5AD1FF), accentSoft = c(0x295AD1FF), accentInk = c(0xFF9FE6FF),
        btnBg = c(0x12FFFFFF), btnEdge = c(0x1FFFFFFF), btnInk = c(0xFFEAF0FF),
        pieceColors = listOf(
            c(0xFF4FD6E8), c(0xFF4D84F0), c(0xFFF0913C), c(0xFFF5C94A),
            c(0xFF5AD46F), c(0xFFB06CF0), c(0xFFF0605A),
        ),
        garbage = c(0xFF6B7280),
    ),
    GameTheme(
        key = "paper", label = "페이퍼", style = BlockStyle.FLAT, font = FontFamily.SansSerif, numberFont = FontFamily.SansSerif,
        bg = c(0xFFF0EADD), bgTop = c(0xFFFAF6EE),
        panel = c(0xFFFFFDF8), edge = c(0xFFE0D7C6),
        boardFrame = c(0xFFFFFDF8), boardBg = c(0xFFEFE8D9), grid = c(0xFFE2DAC9),
        ink = c(0xFF221D16), inkDim = c(0x80221D16),
        accent = c(0xFFC2513A), accentSoft = c(0x1FC2513A), accentInk = c(0xFF9C3F2D),
        btnBg = c(0xFFFFFDF8), btnEdge = c(0xFFDED3C0), btnInk = c(0xFF221D16),
        pieceColors = listOf(
            c(0xFF3F9AA8), c(0xFF3F68A8), c(0xFFCF7A34), c(0xFFD9A534),
            c(0xFF5E9B57), c(0xFF8A5EA8), c(0xFFC2513A),
        ),
        garbage = c(0xFFA79C88),
    ),
    GameTheme(
        key = "arcade", label = "네온 아케이드", style = BlockStyle.GLOW, font = FontFamily.SansSerif, numberFont = FontFamily.Monospace,
        bg = c(0xFF06060C), bgTop = c(0xFF1B0F2E),
        panel = c(0x0DFFFFFF), edge = c(0x47FF3EA5),
        boardFrame = c(0x0FFF3EA5), boardBg = c(0xFF0A0714), grid = c(0x1AFF3EA5),
        ink = c(0xFFF4E9FF), inkDim = c(0x85F4E9FF),
        accent = c(0xFFFF3EA5), accentSoft = c(0x2EFF3EA5), accentInk = c(0xFFFF8CC8),
        btnBg = c(0x17FF3EA5), btnEdge = c(0x66FF3EA5), btnInk = c(0xFFFFD9EC),
        pieceColors = listOf(
            c(0xFF2EF2FF), c(0xFF4D7BFF), c(0xFFFF9B2E), c(0xFFFFE23D),
            c(0xFF3DFF9E), c(0xFFC14DFF), c(0xFFFF3E5F),
        ),
        garbage = c(0xFF6E5C87),
    ),
    GameTheme(
        key = "sakura", label = "사쿠라", style = BlockStyle.SOFT, font = FontFamily.SansSerif, numberFont = FontFamily.SansSerif,
        bg = c(0xFFFBE9EE), bgTop = c(0xFFFFF7F9),
        panel = c(0xFFFFFAFB), edge = c(0xFFF3D9E1),
        boardFrame = c(0xFFFFFAFB), boardBg = c(0xFFF7E6EB), grid = c(0xFFF0D6DE),
        ink = c(0xFF472A33), inkDim = c(0x7A472A33),
        accent = c(0xFFE0688B), accentSoft = c(0x24E0688B), accentInk = c(0xFFB84C6C),
        btnBg = c(0xFFFFFAFB), btnEdge = c(0xFFF2D3DC), btnInk = c(0xFF472A33),
        pieceColors = listOf(
            c(0xFF7FCFD6), c(0xFF8FA9E0), c(0xFFF2AB7C), c(0xFFF6CF72),
            c(0xFF95D29A), c(0xFFC193DC), c(0xFFEF8B95),
        ),
        garbage = c(0xFFCBB3BC),
    ),
    GameTheme(
        key = "terminal", label = "터미널", style = BlockStyle.OUTLINE, font = FontFamily.SansSerif, numberFont = FontFamily.Monospace,
        bg = c(0xFF03110A), bgTop = c(0xFF062015),
        panel = c(0x0D7DFFAB), edge = c(0x387DFFAB),
        boardFrame = c(0x0A7DFFAB), boardBg = c(0xFF02100A), grid = c(0x177DFFAB),
        ink = c(0xFF7DFFAB), inkDim = c(0x807DFFAB),
        accent = c(0xFFC8FF7D), accentSoft = c(0x24C8FF7D), accentInk = c(0xFFC8FF7D),
        btnBg = c(0x0F7DFFAB), btnEdge = c(0x4D7DFFAB), btnInk = c(0xFF7DFFAB),
        pieceColors = listOf(
            c(0xFF7DFFAB), c(0xFF7DD6FF), c(0xFFB6FFAB), c(0xFFC8FF7D),
            c(0xFFA6FF8A), c(0xFF7DFFE0), c(0xFFE4FF7D),
        ),
        garbage = c(0xFF4A7A5C),
    ),
)

fun themeByKey(key: String?): GameTheme =
    GameThemes.firstOrNull { it.key == key } ?: GameThemes[0]
