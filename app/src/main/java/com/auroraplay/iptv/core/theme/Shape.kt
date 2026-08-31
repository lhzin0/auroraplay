package com.auroraplay.iptv.core.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val AuroraShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

object AuroraRadius {
    val Card = 14.dp
    val CardLarge = 20.dp
    val Chip = 100.dp
    val Sheet = 24.dp
}
