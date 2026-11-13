package kr.heartblink.car.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kr.heartblink.car.ui.theme.Heart
import kr.heartblink.car.ui.theme.HeartType

/** Click without the Material ripple; the Figma buttons have no pressed state. */
fun Modifier.tap(onClick: (() -> Unit)?): Modifier = if (onClick == null) this else this.then(
    Modifier.clickable(interactionSource = MutableInteractionSource(), indication = null, onClick = onClick)
)

private val ButtonShape = RoundedCornerShape(28.dp)

/** Raised surface used by buttons and chips: drop shadow, fill, border, then the soft inner light. */
@Composable
fun Raised(
    shape: androidx.compose.ui.graphics.Shape,
    fill: Color,
    modifier: Modifier = Modifier,
    border: Color? = null,
    borderWidth: Dp = 2.dp,
    drop: Shadow,
    inner: Array<Shadow>,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .outerShadow(shape, drop)
            .clip(shape)
            .background(fill)
            .innerShadow(shape, *inner)
            .then(if (border != null) Modifier.border(borderWidth, border, shape) else Modifier),
        contentAlignment = Alignment.Center,
    ) { content() }
}

private val AccentInner = arrayOf(Shadow(5f, 5f, 10f, Color(0x59FFFFFF)), Shadow(-5f, -7f, 14f, Color(0x21292124)))
private val CardInner = arrayOf(Shadow(6f, 6f, 12f, Color(0xD9FFFFFF)), Shadow(-6f, -8f, 16f, Color(0x0D292124)))

/** Heart2/Button Primary: flat pink, white 32px bold label, no shadow. [enabled] false dims it (sent, waiting). */
@Composable
fun PrimaryButton(label: String, modifier: Modifier = Modifier.width(360.dp), enabled: Boolean = true, onClick: (() -> Unit)? = null) {
    Box(
        modifier
            .height(88.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(ButtonShape)
            .background(Heart.colors.pinkAccent)
            .tap(onClick.takeIf { enabled }),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = HeartType.Headline, color = Heart.colors.textOnAccent, maxLines = 1, modifier = Modifier.padding(horizontal = 40.dp)) }
}

/** Heart2/Button Secondary: flat white with a 2px hairline. */
@Composable
fun SecondaryButton(label: String, modifier: Modifier = Modifier.width(360.dp), onClick: (() -> Unit)? = null) {
    val c = Heart.colors
    Box(
        modifier
            .height(88.dp)
            .clip(ButtonShape)
            .background(c.bgCard)
            .border(2.dp, c.line, ButtonShape)
            .tap(onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = HeartType.Headline, color = c.textPrimary, maxLines = 1, modifier = Modifier.padding(horizontal = 40.dp)) }
}

/** A text-only action, deep pink unless [color] is given (끊기, 바꾸기, 글자로만 쓰기): no box, a touch height of 64. */
@Composable
fun TextAction(
    label: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = HeartType.BodyStrong,
    color: Color = Heart.colors.pinkDeep,
    onClick: (() -> Unit)? = null,
) {
    Box(modifier.height(64.dp).tap(onClick), contentAlignment = Alignment.CenterStart) {
        Text(label, style = style.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = color, maxLines = 1)
    }
}

/** Heart2/Chip: a flat pill; selected is solid pink with white text. */
@Composable
fun HeartChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val c = Heart.colors
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier
            .clip(shape)
            .background(if (selected) c.pinkAccent else c.bgCard)
            .border(2.dp, if (selected) c.pinkAccent else c.pinkMid, shape)
            .tap(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, style = HeartType.BodyStrong, color = if (selected) c.textOnAccent else c.textPrimary, maxLines = 1,
            modifier = Modifier.padding(start = 24.dp, end = 26.dp, top = 14.dp, bottom = 14.dp),
        )
    }
}

/** Heart2/Field: a label over a flat white input box (typing only while parked). */
@Composable
fun HeartField(label: String, modifier: Modifier = Modifier.width(640.dp), value: @Composable () -> Unit) {
    val c = Heart.colors
    val shape = RoundedCornerShape(24.dp)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = HeartType.Label, color = c.textSecondary)
        Box(
            Modifier
                .fillMaxWidth()
                .height(84.dp)
                .clip(shape)
                .background(c.bgCard)
                .border(2.dp, c.line, shape)
                .padding(horizontal = 28.dp),
            contentAlignment = Alignment.CenterStart,
        ) { value() }
    }
}

/**
 * Heart2/Tab Underline: category filters as words, the picked one deep pink and bold with a pink bar under it
 * (6 px, as wide as the word). [count] follows the word in a lighter weight, as in 기록 and 상점.
 */
@Composable
fun UnderlineTabs(
    labels: List<String>,
    selected: Int,
    modifier: Modifier = Modifier,
    counts: List<String?> = emptyList(),
    gap: Dp = 36.dp,
    onSelect: ((Int) -> Unit)? = null,
) {
    val c = Heart.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.Bottom) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Column(
                Modifier.width(IntrinsicSize.Max).tap(onSelect?.let { { it(i) } }).padding(top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    Text(label, style = HeartType.noto(30f, if (on) 700 else 500, 1.2f), color = if (on) c.pinkDeep else c.textSecondary, maxLines = 1)
                    counts.getOrNull(i)?.let { Text(it, style = HeartType.noto(30f, 500, 1.2f), color = if (on) c.pinkDeep else c.textTertiary, maxLines = 1) }
                }
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(if (on) c.pinkAccent else Color.Transparent))
            }
        }
    }
}

@Composable
fun HeartField(label: String, value: String, modifier: Modifier = Modifier.width(640.dp)) =
    HeartField(label, modifier) { Text(value, style = HeartType.BodyStrong, color = Heart.colors.textPrimary) }

/** Heart2/Distance Badge: total distance both have driven at the same time, on the page itself (no pill). */
@Composable
fun DistanceBadge(km: String, modifier: Modifier = Modifier) {
    val c = Heart.colors
    Row(
        modifier.padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FigmaSvg("badge_road", Modifier.size(124.dp, 50.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
            Text("함께 달린 거리", style = HeartType.Label, color = c.textSecondary, modifier = Modifier.alignByBaseline())
            Text(km, style = HeartType.jua(30f), color = c.textPrimary, modifier = Modifier.alignByBaseline())
            Text("km", style = HeartType.BodyStrong, color = c.textSecondary, modifier = Modifier.alignByBaseline())
        }
    }
}
