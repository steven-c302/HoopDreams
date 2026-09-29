package com.partyos.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme
import com.partyos.tv.R

/**
 * "The Studio": a late-night game show. The stage is dark so the props (cream cards, gold bulbs,
 * neon signs) pop. Games nest their own palette inside the studio frame.
 */
object Party {
    // Stage
    val Ink = Color(0xFF0B0716)
    val Night = Color(0xFF160D2B)
    val Plum = Color(0xFF2A1454)
    val Card = Color(0xFF1F1440)
    val Line = Color(0xFF3A2A6A)
    val Text = Color(0xFFF6F1FF)
    val Muted = Color(0xFFA99BD0)
    val Velvet = Color(0xFF3B0A2A)
    val VelvetHi = Color(0xFF7A1446)

    // Props and lights
    val Cream = Color(0xFFFFF4D6)
    val CreamShade = Color(0xFFE9D9B0)
    val Gold = Color(0xFFFFD23F)
    val BulbOff = Color(0xFF5C4A1E)
    val Pink = Color(0xFFFF4D8D)
    val Orange = Color(0xFFFF8A3D)
    val Mint = Color(0xFF3DDC97)
    val Sky = Color(0xFF2EC4F1)
    val Red = Color(0xFFFF5A5A)

    // Bluff Battle identity: poker-table green and brass.
    val Felt = Color(0xFF0E5A3A)
    val FeltDeep = Color(0xFF06301F)
    val Brass = Color(0xFFD9A441)

    val Display = FontFamily(Font(R.font.bungee))
    /** Marquee lettering for logos and the biggest slams only. */
    val Marquee = FontFamily(Font(R.font.bungee_shade))
    val Body = FontFamily(
        listOf(500, 700, 800, 900).map { w ->
            Font(R.font.rubik, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
        },
    )
}

private val typography = Typography(
    displayLarge = TextStyle(fontFamily = Party.Display, fontSize = 64.sp, lineHeight = 70.sp),
    displayMedium = TextStyle(fontFamily = Party.Display, fontSize = 44.sp, lineHeight = 50.sp),
    displaySmall = TextStyle(fontFamily = Party.Display, fontSize = 36.sp, lineHeight = 42.sp),
    headlineLarge = TextStyle(fontFamily = Party.Display, fontSize = 32.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = Party.Body, fontWeight = FontWeight.Black, fontSize = 26.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontFamily = Party.Body, fontWeight = FontWeight.Black, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = Party.Body, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = Party.Body, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontFamily = Party.Body, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Party.Body, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = Party.Body, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, letterSpacing = 2.sp),
    labelMedium = TextStyle(fontFamily = Party.Body, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, letterSpacing = 2.sp),
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
