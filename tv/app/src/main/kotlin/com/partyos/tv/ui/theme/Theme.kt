package com.partyos.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme
import com.partyos.tv.R

object Party {
    val Ink = Color(0xFF0B0716)
    val Night = Color(0xFF160D2B)
    val Plum = Color(0xFF2A1454)
    val Card = Color(0xFF1F1440)
    val Line = Color(0xFF3A2A6A)
    val Text = Color(0xFFF6F1FF)
    val Muted = Color(0xFFA99BD0)
    val Pink = Color(0xFFFF4D8D)
    val Orange = Color(0xFFFF8A3D)
    val Gold = Color(0xFFFFD23F)
    val Mint = Color(0xFF3DDC97)
    val Sky = Color(0xFF2EC4F1)
    val Red = Color(0xFFFF5A5A)

    // Bluff Battle identity: poker-table green and brass.
    val Felt = Color(0xFF0E5A3A)
    val FeltDeep = Color(0xFF06301F)
    val Brass = Color(0xFFD9A441)

    val Display = FontFamily(Font(R.font.bungee))
}

private val typography = Typography(
    displayLarge = TextStyle(fontFamily = Party.Display, fontSize = 64.sp, lineHeight = 70.sp),
    displayMedium = TextStyle(fontFamily = Party.Display, fontSize = 44.sp, lineHeight = 50.sp),
    headlineLarge = TextStyle(fontFamily = Party.Display, fontSize = 32.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Black, fontSize = 26.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 1.sp),
)

@Composable
fun PartyTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = darkColorScheme(
        primary = Party.Pink, onPrimary = Party.Ink, secondary = Party.Gold,
        background = Party.Ink, surface = Party.Card, onSurface = Party.Text, onBackground = Party.Text,
    ),
    typography = typography,
    content = content,
)
