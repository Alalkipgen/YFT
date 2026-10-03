package com.alal.yft.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Corner radii from the brief: cards 20-24dp, Promptbox 28dp, pills, 28dp sheet tops. */
object YftShapes {
    val card = RoundedCornerShape(20.dp)
    val cardLarge = RoundedCornerShape(24.dp)
    val promptbox = RoundedCornerShape(28.dp)
    val pill = CircleShape
    val sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val thumbnail = RoundedCornerShape(16.dp)
    val thumbnailSmall = RoundedCornerShape(12.dp)
    val badge = RoundedCornerShape(8.dp)
}

val YftMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
