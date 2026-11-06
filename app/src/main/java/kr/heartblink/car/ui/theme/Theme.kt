package kr.heartblink.car.ui.theme

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kr.heartblink.car.R

/** Colors from the Figma variable collection "하트깜빡이". */
@Immutable
data class HeartColors(
    val bgBase: Color,
    val bgCard: Color,
    val pinkLight: Color,
    val pinkSoft: Color,
    val pinkMid: Color,
    val pinkAccent: Color,
    val pinkDeep: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textOnAccent: Color,
    val line: Color,
    val positive: Color,
)

/** Mode "기본". */
val PlainColors = HeartColors(
    bgBase = Color(0xFFFFFFFF),
    bgCard = Color(0xFFFFFFFF),
    pinkLight = Color(0xFFF4F2F3),
    pinkSoft = Color(0xFFF7F5F6),
    pinkMid = Color(0xFFE6E1E3),
    pinkAccent = Color(0xFFE9557F),
    pinkDeep = Color(0xFFC93E68),
    textPrimary = Color(0xFF2E2328),
    textSecondary = Color(0xFF6E676A),
    textTertiary = Color(0xFFA39D9F),
    textOnAccent = Color(0xFFFFFFFF),
    line = Color(0xFFECE8EA),
    positive = Color(0xFF1FB866),
)

/** Mode "감성 (연베이지)", the one the screens use. */
val WarmColors = HeartColors(
    bgBase = Color(0xFFFBF7F1),
    bgCard = Color(0xFFFFFFFF),
    pinkLight = Color(0xFFF6EFE6),
    pinkSoft = Color(0xFFF3EBE1),
    pinkMid = Color(0xFFE2D4C3),
    pinkAccent = Color(0xFFE9557F),
    pinkDeep = Color(0xFFC93E68),
    textPrimary = Color(0xFF3A2F2A),
    textSecondary = Color(0xFF7A6A60),
    textTertiary = Color(0xFFA99B90),
    textOnAccent = Color(0xFFFFFFFF),
    line = Color(0xFFECE2D6),
    positive = Color(0xFF1FB866),
)

/** Ink of the hand-drawn car and plate lettering. */
val Ink = Color(0xFF2B2226)

@OptIn(ExperimentalTextApi::class)
private fun noto(weight: Int) = Font(
    R.font.noto_sans_kr,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val NotoSansKr = FontFamily(noto(400), noto(500), noto(700))
val Jua = FontFamily(Font(R.font.jua))
val Gaegu = FontFamily(Font(R.font.gaegu_regular, FontWeight.Normal), Font(R.font.gaegu_bold, FontWeight.Bold))

private val trim = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun style(family: FontFamily, weight: FontWeight, size: Int, lineHeight: Float?, spacing: Float = -0.015f) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = if (lineHeight != null) (size * lineHeight).sp else TextStyle.Default.lineHeight,
    letterSpacing = spacing.em,
    lineHeightStyle = trim,
)

/** Text styles from Figma ("Car/..." and "Heart/..."). Sizes are Figma pixels, see [DesignScale]. */
object HeartType {
    /** Noto Sans KR at any size; Figma tracking is -1.5% on every Noto text. */
    fun noto(size: Float, weight: Int = 500, lineHeight: Float = 1.4f) = TextStyle(
        fontFamily = NotoSansKr, fontWeight = FontWeight(weight), fontSize = size.sp,
        lineHeight = (size * lineHeight).sp, letterSpacing = (-0.015f).em, lineHeightStyle = trim,
    )

    /** Jua or Gaegu at any size with Figma's "auto" line height (1.25 for both fonts). */
    fun jua(size: Float) = TextStyle(fontFamily = Jua, fontSize = size.sp, lineHeight = (size * 1.25f).sp, lineHeightStyle = trim)

    fun gaegu(size: Float, bold: Boolean = true, spacing: Float = 0f) = TextStyle(
        fontFamily = Gaegu, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, fontSize = size.sp,
        lineHeight = (size * 1.25f).sp, letterSpacing = spacing.em, lineHeightStyle = trim,
    )

    val Display = style(NotoSansKr, FontWeight.Bold, 64, 1.3f)
    val Title = style(NotoSansKr, FontWeight.Bold, 40, 1.3f)
    val Headline = style(NotoSansKr, FontWeight.Bold, 32, 1.35f)
    val Body = style(NotoSansKr, FontWeight.Normal, 28, 1.45f)
    val BodyStrong = style(NotoSansKr, FontWeight.Medium, 28, 1.45f)
    val Label = style(NotoSansKr, FontWeight.Medium, 24, 1.4f)
    val Small = style(NotoSansKr, FontWeight.Medium, 18, 1.4f)

    val Wordmark = style(Jua, FontWeight.Normal, 34, 1.25f, 0f)
    val HeartDisplay = style(Jua, FontWeight.Normal, 64, 1.25f, 0f)
    val HeartTitle = style(Jua, FontWeight.Normal, 44, 1.25f, 0f)
    val HandXL = style(Gaegu, FontWeight.Bold, 76, 1.25f, 0f)
    val HandL = style(Gaegu, FontWeight.Bold, 58, 1.25f, 0f)
    val HandM = style(Gaegu, FontWeight.Bold, 40, 1.25f, 0f)
    val HandS = style(Gaegu, FontWeight.Normal, 32, 1.25f, 0f)
}

val LocalHeartColors = staticCompositionLocalOf { WarmColors }

object Heart {
    val colors: HeartColors
        @Composable get() = LocalHeartColors.current
}

/** The car app window in Figma is 1682 x 1210 px. */
const val DesignWidth = 1682f
const val DesignHeight = 1210f

/**
 * Lays the app out in Figma pixels: 1.dp and 1.sp equal one Figma pixel, scaled to the real window width.
 * On the Pleos head unit the app window is 1682 x 1210 px, so the scale there is exactly 1.
 */
@Composable
fun DesignScale(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val outer = LocalDensity.current
        val windowPx = with(outer) { maxWidth.toPx() }
        CompositionLocalProvider(LocalDensity provides Density(windowPx / DesignWidth, 1f)) {
            content()
        }
    }
}

@Composable
fun HeartTheme(colors: HeartColors = WarmColors, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalHeartColors provides colors) {
        DesignScale(content)
    }
}
