package test.smartlinksoft

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Shader
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import test.smartlinksoft.ui.theme.B0
import test.smartlinksoft.ui.theme.B1
import test.smartlinksoft.ui.theme.IridA
import test.smartlinksoft.ui.theme.IridB
import test.smartlinksoft.ui.theme.IridC
import test.smartlinksoft.ui.theme.IridD
import test.smartlinksoft.ui.theme.Pixel
import test.smartlinksoft.ui.theme.Title
import test.smartlinksoft.ui.theme.Tm

/** Square with the corners cut. The old .port buttons read as trimmed, not rounded. */
class ChamferShape(private val cut: Dp = 8.dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val c = with(density) { cut.toPx() }.coerceAtMost(minOf(size.width, size.height) / 2f)
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(c, 0f)
            lineTo(w - c, 0f)
            lineTo(w, c)
            lineTo(w, h - c)
            lineTo(w - c, h)
            lineTo(c, h)
            lineTo(0f, h - c)
            lineTo(0f, c)
            close()
        }
        return Outline.Generic(path)
    }
}

private val IridColors = listOf(IridA, IridB, IridC, IridD, IridA)

@Composable
fun iridAngle(): Float {
    val t = rememberInfiniteTransition(label = "irid")
    val a by t.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
        label = "irid-angle",
    )
    return a
}

/**
 * The old .field: a 4px silkscreen dot matrix, a denser band on the diagonal,
 * and a few grey vein strokes. Drawn once into tiles so it does not churn.
 */
@Composable
fun DotField(modifier: Modifier = Modifier) {
    val dot = remember { tile(4, 4) { x, y -> if (x % 2 == 0 && y % 2 == 0) 0x668B949E.toInt() else 0 } }
    val dense = remember { tile(4, 4) { x, y -> if ((x + y) % 2 == 0) 0x558B949E.toInt() else 0 } }
    Canvas(modifier) {
        val nc = drawContext.canvas.nativeCanvas
        val paint = android.graphics.Paint().apply {
            shader = BitmapShader(dot, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        }
        nc.drawRect(0f, 0f, size.width, size.height, paint)

        // Diagonal bands, same idea as the old mask-image stripe.
        val band = android.graphics.Paint().apply {
            shader = BitmapShader(dense, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        }
        val stripe = 80f
        var x = -size.height
        while (x < size.width) {
            nc.save()
            nc.clipRect(x, 0f, x + stripe * 0.28f, size.height)
            nc.rotate(28f, size.width / 2f, size.height / 2f)
            nc.drawRect(-size.height, -size.height, size.width + size.height, size.height + size.height, band)
            nc.restore()
            x += stripe
        }

        // Abstract grey veins. Short right-angle runs, like the old SVG.
        val vein = android.graphics.Paint().apply {
            color = 0x668B949E.toInt()
            strokeWidth = 1f
            style = android.graphics.Paint.Style.STROKE
            isAntiAlias = false
        }
        val dark = android.graphics.Paint(vein).apply { color = 0x33050505 }
        val runs = listOf(
            floatArrayOf(0f, 0f, 0f, 18f, 22f, 18f, 22f, 40f),
            floatArrayOf(70f, 0f, 70f, 28f, 54f, 28f, 54f, 48f),
            floatArrayOf(140f, 0f, 140f, 12f, 168f, 12f, 168f, 36f),
            floatArrayOf(220f, 0f, 220f, 32f, 196f, 32f, 196f, 50f),
            floatArrayOf(310f, 0f, 310f, 16f, 340f, 16f, 340f, 42f),
        )
        var ox = 0f
        while (ox < size.width + 40f) {
            runs.forEachIndexed { i, r ->
                val shifted = FloatArray(r.size) { k -> if (k % 2 == 0) r[k] + ox else r[k] }
                nc.drawLines(shifted, if (i % 2 == 0) vein else dark)
            }
            ox += 360f
        }
    }
}

private fun tile(w: Int, h: Int, pixel: (Int, Int) -> Int): Bitmap {
    val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    for (y in 0 until h) for (x in 0 until w) b.setPixel(x, y, pixel(x, y))
    return b
}

/** Chamfered square. Rotating iridescent stroke, dark fill. */
@Composable
fun IridButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val angle = iridAngle()
    val shape = remember { ChamferShape(7.dp) }
    val fill = if (selected) B1 else B0
    Box(
        modifier
            .defaultMinSize(minWidth = 56.dp, minHeight = 44.dp)
            .clip(shape)
            .drawBehind {
                drawRect(fill)
                val cut = 7.dp.toPx()
                val w = size.width
                val h = size.height
                val path = android.graphics.Path().apply {
                    moveTo(cut, 0f)
                    lineTo(w - cut, 0f)
                    lineTo(w, cut)
                    lineTo(w, h - cut)
                    lineTo(w - cut, h)
                    lineTo(cut, h)
                    lineTo(0f, h - cut)
                    lineTo(0f, cut)
                    close()
                }
                val cx = w / 2f
                val cy = h / 2f
                drawContext.canvas.nativeCanvas.drawPath(
                    path,
                    android.graphics.Paint().apply {
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 1.6.dp.toPx()
                        isAntiAlias = false
                        shader = android.graphics.SweepGradient(
                            cx, cy,
                            intArrayOf(
                                IridA.toArgb(), IridB.toArgb(),
                                IridC.toArgb(), IridD.toArgb(), IridA.toArgb(),
                            ),
                            null,
                        ).apply {
                            setLocalMatrix(android.graphics.Matrix().apply {
                                postRotate(angle, cx, cy)
                            })
                        }
                    },
                )
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label.uppercase(),
            fontFamily = Pixel,
            fontWeight = FontWeight.Normal,
            fontSize = 9.sp,
            letterSpacing = 1.4.sp,
            color = if (selected) Title else Tm,
        )
    }
}
