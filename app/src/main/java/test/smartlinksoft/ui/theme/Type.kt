package test.smartlinksoft.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import test.smartlinksoft.R

val Pixel = FontFamily(
    Font(R.font.silkscreen_regular, FontWeight.Normal),
    Font(R.font.silkscreen_bold, FontWeight.Bold),
)

val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = Pixel,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.4.sp,
        color = Tx,
    ),
    bodyMedium = TextStyle(
        fontFamily = Pixel,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.4.sp,
        color = Tm,
    ),
    titleLarge = TextStyle(
        fontFamily = Pixel,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 22.sp,
        letterSpacing = 1.5.sp,
        color = Title,
    ),
    labelSmall = TextStyle(
        fontFamily = Pixel,
        fontWeight = FontWeight.Normal,
        fontSize = 9.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.2.sp,
        color = Tm,
    ),
)
