package test.smartlinksoft

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import test.smartlinksoft.ui.theme.IridA
import test.smartlinksoft.ui.theme.Title

/**
 * Tiny marionette. Joints are circles, limbs are lines.
 * bpm bends the arms; spo2 lifts the head. Null values hold a neutral pose.
 */
@Composable
fun StickFigure(
    bpm: Int?,
    spo2: Int?,
    modifier: Modifier = Modifier.size(120.dp),
    color: Color = Title,
    joint: Color = IridA,
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val headR = w * 0.08f
        val neck = h * 0.18f
        val hip = h * 0.55f
        val shoulder = h * 0.28f

        // Head bob from spo2 (higher = higher)
        val lift = ((spo2 ?: 97) - 90).coerceIn(0, 10) * (h * 0.005f)
        val head = Offset(cx, neck - lift)

        // Arm swing from bpm
        val swing = ((bpm ?: 70) - 60).coerceIn(-20, 40) * 0.4f
        val armLen = w * 0.28f
        val legLen = h * 0.28f

        fun joint(c: Offset) {
            drawCircle(joint, radius = headR * 0.45f, center = c)
        }
        fun limb(a: Offset, b: Offset) {
            drawLine(color, a, b, strokeWidth = 3f, cap = StrokeCap.Round)
        }

        // Head
        drawCircle(color, radius = headR, center = head, style = Stroke(2.5f))
        joint(head)

        // Spine
        val shoulders = Offset(cx, shoulder)
        val hips = Offset(cx, hip)
        limb(Offset(cx, head.y + headR), shoulders)
        limb(shoulders, hips)
        joint(shoulders)
        joint(hips)

        // Arms
        val leftHand = Offset(cx - armLen, shoulder + armLen * 0.4f + swing)
        val rightHand = Offset(cx + armLen, shoulder + armLen * 0.4f - swing)
        limb(shoulders, leftHand)
        limb(shoulders, rightHand)
        joint(leftHand)
        joint(rightHand)

        // Legs
        val leftFoot = Offset(cx - legLen * 0.5f, hip + legLen)
        val rightFoot = Offset(cx + legLen * 0.5f, hip + legLen)
        limb(hips, leftFoot)
        limb(hips, rightFoot)
        joint(leftFoot)
        joint(rightFoot)
    }
}
