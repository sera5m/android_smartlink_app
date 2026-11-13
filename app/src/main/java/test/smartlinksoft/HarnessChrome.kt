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
import androidx.compose.runtime.getValue
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
    // 16px cell, 4px dot. Reads as silkscreen instead of noise.
    val dot = remember { tile(16, 16) { x, y -> if (x < 4 && y < 4) 0x998B949E.toInt() else 0 } }
    Canvas(modifier) {
        val nc = drawContext.canvas.nativeCanvas
        nc.drawRect(
            0f, 0f, size.width, size.height,
            android.graphics.Paint().apply {
                shader = BitmapShader(dot, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            },
        )

        val slant = android.graphics.Paint().apply {
            color = 0x558B949E.toInt()
            strokeWidth = 1.5f
            style = android.graphics.Paint.Style.STROKE
            isAntiAlias = false
        }
        val step = 72f
        var x = -size.height
        while (x < size.width + size.height) {
            nc.drawLine(x, 0f, x + size.height * 0.55f, size.height, slant)
            x += step
        }

        val ring = android.graphics.Paint().apply {
            color = 0x668B949E.toInt()
            strokeWidth = 1.5f
            style = android.graphics.Paint.Style.STROKE
            isAntiAlias = false
        }
        val fill = android.graphics.Paint().apply {
            color = 0x338B949E.toInt()
            style = android.graphics.Paint.Style.FILL
            isAntiAlias = false
        }
        val cols = (size.width / 96f).toInt().coerceAtLeast(1)
        val rows = (size.height / 140f).toInt().coerceAtLeast(1)
        for (r in 0..rows) {
            for (c in 0..cols) {
                val cx = 48f + c * 96f
                val cy = 70f + r * 140f + if (c % 2 == 0) 0f else 36f
                nc.drawCircle(cx, cy, 7f, ring)
                nc.drawCircle(cx, cy, 2f, fill)
            }
        }
    }
}

/** Black panel with a 16px grid. Important blocks sit on this, over the field. */
@Composable
fun GridPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = remember { ChamferShape(10.dp) }
    Box(
        modifier
            .clip(shape)
            .drawBehind {
                drawRect(B0)
                val step = 16.dp.toPx()
                val paint = android.graphics.Paint().apply {
                    color = 0xFF161616.toInt()
                    strokeWidth = 1f
                    isAntiAlias = false
                }
                val nc = drawContext.canvas.nativeCanvas
                var x = 0f
                while (x <= size.width) {
                    nc.drawLine(x, 0f, x, size.height, paint)
                    x += step
                }
                var y = 0f
                while (y <= size.height) {
                    nc.drawLine(0f, y, size.width, y, paint)
                    y += step
                }
            }
            .padding(12.dp),
    ) { content() }
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
