package kr.heartblink.car.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import coil3.compose.AsyncImage
import kotlin.math.roundToInt

/** An SVG exported from Figma, kept in assets/figma. */
@Composable
fun FigmaSvg(name: String, modifier: Modifier = Modifier) {
    AsyncImage(
        model = "file:///android_asset/figma/$name.svg",
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = modifier,
    )
}

/**
 * A frame inside a [Layered] box as fractions of that box (left, top, right, bottom), the way Figma's
 * exported code writes `inset`. As a bleed it is how far the drawing reaches past its frame, as fractions
 * of the frame (strokes that stick out).
 */
data class Inset(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    companion object {
        val Zero = Inset(0f, 0f, 0f, 0f)

        /** Figma percentages, in CSS order (top, right, bottom, left). */
        fun pct(top: Float, right: Float, bottom: Float, left: Float) = Inset(left / 100f, top / 100f, right / 100f, bottom / 100f)

        fun pct(vertical: Float, horizontal: Float) = pct(vertical, horizontal, vertical, horizontal)

        fun pct(all: Float) = pct(all, all, all, all)
    }
}

class LayerSpec(val inset: Inset, val bleed: Inset, val content: @Composable () -> Unit)

class LayeredScope {
    internal val layers = mutableListOf<LayerSpec>()

    /** An SVG piece at [inset] of the parent, drawn [bleed] past its frame (bleed values are positive outward). */
    fun svg(name: String, inset: Inset, bleed: Inset = Inset.Zero, modifier: Modifier = Modifier) {
        layers += LayerSpec(inset, bleed) { FigmaSvg(name, modifier.fillMaxSize()) }
    }

    fun layer(inset: Inset, content: @Composable () -> Unit) {
        layers += LayerSpec(inset, Inset.Zero, content)
    }
}

/** A box whose pieces are placed by fractions of its own size, so the whole drawing scales together. */
@Composable
fun Layered(modifier: Modifier = Modifier, build: LayeredScope.() -> Unit) {
    val scope = LayeredScope().apply(build)
    Layout(
        content = { scope.layers.forEach { Box { it.content() } } },
        modifier = modifier,
    ) { measurables, constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val placed = measurables.mapIndexed { i, m ->
            val spec = scope.layers[i]
            val fw = w * (1f - spec.inset.left - spec.inset.right)
            val fh = h * (1f - spec.inset.top - spec.inset.bottom)
            val x = spec.inset.left * w - spec.bleed.left * fw
            val y = spec.inset.top * h - spec.bleed.top * fh
            val pw = (fw * (1f + spec.bleed.left + spec.bleed.right)).roundToInt().coerceAtLeast(0)
            val ph = (fh * (1f + spec.bleed.top + spec.bleed.bottom)).roundToInt().coerceAtLeast(0)
            Triple(m.measure(Constraints.fixed(pw, ph)), x.roundToInt(), y.roundToInt())
        }
        layout(w, h) { placed.forEach { (p, x, y) -> p.place(x, y) } }
    }
}

/** Draws nothing unless [visible], while keeping its place; used for blink layers. */
fun Modifier.shownIf(visible: Boolean) = drawWithContent { if (visible) drawContent() }
