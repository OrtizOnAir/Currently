package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import java.util.Locale

// Cool Slate & Ice Magazine Light Palette
val NewsprintBackground = Color(0xFFEEF2F6)          // Cool ice-slate background canvas
val NewsprintSurface = Color(0xFFFFFFFF)             // Crisp white magazine tile surface
val NewsprintSurfaceElevated = Color(0xFFF1F5F9)     // Cool pale slate callout surface
val PrinterInkBlack = Color(0xFF0F172A)              // Deep slate-navy ink
val PrinterInkCharcoal = Color(0xFF334155)           // Cool slate subhead text
val PrinterInkMuted = Color(0xFF64748B)              // Cool muted steel gray
val EditorialCrimson = Color(0xFF0284C7)             // Cool Holo Sky/Cyan primary accent
val EditorialCrimsonLight = Color(0xFFE0F2FE)
val OxfordPressNavy = Color(0xFF0F172A)              // Deep Midnight Slate Action Bar
val AntiqueBrass = Color(0xFF0D9488)                 // Cool Teal accent (replaces warm brass)
val HairlineRuleLight = Color(0xFFCBD5E1)            // Cool 1dp slate divider

// Cool Midnight Slate Dark Edition Palette
val DarkPressBackground = Color(0xFF0B1120)
val DarkPressSurface = Color(0xFF131C2E)
val DarkPressSurfaceElevated = Color(0xFF1E293B)
val IvoryInk = Color(0xFFF1F5F9)
val IvoryInkSecondary = Color(0xFF94A3B8)
val IvoryInkMuted = Color(0xFF64748B)
val CrimsonAccentDark = Color(0xFF38BDF8)            // Cool Ice Cyan highlight
val BrassAccentDark = Color(0xFF2DD4BF)              // Cool Aqua-Teal highlight
val HairlineRuleDark = Color(0xFF26334D)

// Cool Spectrum Bar & Section Edition Accents (Cyan, Cobalt, Indigo, Teal, Violet, Steel)
val CurrentsBlue = Color(0xFF0284C7)                 // Cool Sky Cyan
val CurrentsRed = Color(0xFF2563EB)                  // Cool Cobalt Blue
val CurrentsYellow = Color(0xFF38BDF8)               // Cool Ice Blue (used for active icon highlights)
val CurrentsGreen = Color(0xFF0D9488)                // Cool Deep Teal
val CurrentsPurple = Color(0xFF6366F1)               // Cool Indigo-Violet
val CurrentsIndigo = Color(0xFF4F46E5)               // Cool Royal Indigo
val CurrentsSteel = Color(0xFF0891B2)                // Cool Cerulean
val CurrentsActionBarDark = Color(0xFF0F172A)        // Cool Deep Slate-Navy Action Bar

fun currentsSectionColor(category: String): Color {
    val norm = category.lowercase(Locale.US)
    return when {
        norm.contains("tech") -> CurrentsBlue        // Sky Cyan (#0284C7)
        norm.contains("politic") -> CurrentsIndigo   // Royal Indigo (#4F46E5)
        norm.contains("health") || norm.contains("science") -> CurrentsGreen // Cool Teal (#0D9488)
        norm.contains("world") || norm.contains("wire") -> CurrentsRed       // Cobalt Blue (#2563EB)
        norm.contains("sport") -> CurrentsSteel      // Cerulean (#0891B2)
        norm.contains("entertain") || norm.contains("culture") -> CurrentsPurple // Indigo-Violet (#6366F1)
        else -> CurrentsBlue
    }
}
