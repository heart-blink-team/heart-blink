package kr.heartblink.car.ui.component

import android.graphics.BlurMaskFilter
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

/**
 * A Figma shadow: offset, blur and color in Figma pixels. Use the blur of Figma's effect panel, which is the
 * CSS box-shadow blur; Figma exports CSS drop-shadow() with half of it, so double those.
 */
data class Shadow(val x: Float, val y: Float, val blur: Float, val color: Color)

// CSS blur is twice the Gaussian sigma; Android's blur radius maps to sigma = 0.57735 * radius + 0.5.
private fun androidRadius(cssBlur: Float, density: Float): Float = ((cssBlur * density / 2f - 0.5f) / 0.57735f).coerceAtLeast(0.01f)

private fun DrawScope.shapePath(shape: Shape, size: Size, offset: Offset = Offset.Zero): Path {
    val path = Path()
    when (val o = shape.createOutline(size, layoutDirection, this)) {
        is Outline.Rectangle -> path.addRect(o.rect)
        is Outline.Rounded -> path.addRoundRect(o.roundRect)
        is Outline.Generic -> path.addPath(o.path)
    }
    if (offset != Offset.Zero) path.translate(offset)
    return path
}

/** Drop shadows outside [shape] (Figma drop shadow / CSS drop-shadow), drawn under the content. */
fun Modifier.outerShadow(shape: Shape, vararg shadows: Shadow) = drawBehind {
    val d = 1.dp.toPx()
    shadows.forEach { s ->
        drawIntoCanvas { canvas ->
            val paint = Paint()
            paint.asFrameworkPaint().apply {
                isAntiAlias = true
                color = android.graphics.Color.TRANSPARENT
                setShadowLayer(androidRadius(s.blur, d), s.x * d, s.y * d, s.color.toArgb())
            }
            canvas.drawPath(shapePath(shape, size), paint)
        }
    }
}

/** Inner shadows inside [shape] (Figma inner shadow), drawn over the content like Figma does. */
fun Modifier.innerShadow(shape: Shape, vararg shadows: Shadow) = drawWithContent {
    drawContent()
    val d = 1.dp.toPx()
    val inside = shapePath(shape, size)
    clipPath(inside) {
        shadows.forEach { s ->
            val pad = (s.blur + kotlin.math.abs(s.x) + kotlin.math.abs(s.y)) * d * 2f
            val big = Path().apply { addRect(Rect(-pad, -pad, size.width + pad, size.height + pad)) }
            val ring = Path.combine(PathOperation.Difference, big, shapePath(shape, size, Offset(s.x * d, s.y * d)))
            drawIntoCanvas { canvas ->
                val paint = Paint()
                paint.asFrameworkPaint().apply {
                    isAntiAlias = true
                    color = s.color.toArgb()
                    maskFilter = BlurMaskFilter(androidRadius(s.blur, d), BlurMaskFilter.Blur.NORMAL)
                }
                canvas.drawPath(ring, paint)
            }
        }
    }
}
