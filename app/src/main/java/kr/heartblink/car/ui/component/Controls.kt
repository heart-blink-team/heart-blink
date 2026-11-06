package kr.heartblink.car.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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

/** Heart/Button Primary: pink, white 32px bold label. */
@Composable
fun PrimaryButton(label: String, modifier: Modifier = Modifier.width(360.dp), onClick: (() -> Unit)? = null) {
    Raised(
        ButtonShape, Heart.colors.pinkAccent, modifier.height(88.dp).tap(onClick),
        drop = Shadow(0f, 10f, 22f, Color(0x24292124)), inner = AccentInner,
    ) { Text(label, style = HeartType.Headline, color = Heart.colors.textOnAccent, modifier = Modifier.padding(horizontal = 40.dp)) }
}

/** Heart/Button Secondary: white with a light border. */
@Composable
fun SecondaryButton(label: String, modifier: Modifier = Modifier.width(360.dp), onClick: (() -> Unit)? = null) {
    Raised(
        ButtonShape, Heart.colors.bgCard, modifier.height(88.dp).tap(onClick), border = Heart.colors.pinkMid,
        drop = Shadow(0f, 10f, 22f, Color(0x14292124)), inner = CardInner,
    ) { Text(label, style = HeartType.Headline, color = Heart.colors.textPrimary, modifier = Modifier.padding(horizontal = 40.dp)) }
}

/** Heart/Chip: a condition the message opens on. */
@Composable
fun HeartChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val c = Heart.colors
    Raised(
        RoundedCornerShape(999.dp), if (selected) c.pinkAccent else c.bgCard, modifier.tap(onClick),
        border = if (selected) c.pinkAccent else c.pinkMid,
        drop = Shadow(0f, 10f, 22f, if (selected) Color(0x24292124) else Color(0x14292124)),
        inner = if (selected) AccentInner else CardInner,
    ) {
        Text(
            label, style = HeartType.BodyStrong, color = if (selected) c.textOnAccent else c.textPrimary,
            // CSS draws the 2 px border outside the padding
            modifier = Modifier.padding(start = 26.dp, end = 28.dp, top = 16.dp, bottom = 16.dp),
        )
    }
}

/** Heart/Field: a label over a white input box (typing only while parked). */
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
                .innerShadow(shape, Shadow(-2f, -2f, 6f, Color(0xE6FFFFFF)), Shadow(3f, 4f, 8f, Color(0x0D292124)))
                .border(2.dp, c.line, shape)
                .padding(horizontal = 28.dp),
            contentAlignment = Alignment.CenterStart,
        ) { value() }
    }
}

@Composable
fun HeartField(label: String, value: String, modifier: Modifier = Modifier.width(640.dp)) =
    HeartField(label, modifier) { Text(value, style = HeartType.BodyStrong, color = Heart.colors.textPrimary) }

/** Heart/Distance Badge: total distance both have driven since they connected. */
@Composable
fun DistanceBadge(km: String, modifier: Modifier = Modifier) {
    val c = Heart.colors
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier
            .outerShadow(shape, Shadow(0f, 8f, 20f, Color(0x0F29211F)))
            .clip(shape)
            .background(c.bgCard)
            .border(2.dp, c.line, shape)
            .padding(start = 20.dp, end = 30.dp, top = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FigmaSvg("badge_road", Modifier.size(124.dp, 50.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
            Text("함께 달린 거리", style = HeartType.Label, color = c.textSecondary, modifier = Modifier.alignByBaseline())
            Text(km, style = HeartType.jua(40f), color = c.textPrimary, modifier = Modifier.alignByBaseline())
            Text("km", style = HeartType.BodyStrong, color = c.textSecondary, modifier = Modifier.alignByBaseline())
        }
    }
}
