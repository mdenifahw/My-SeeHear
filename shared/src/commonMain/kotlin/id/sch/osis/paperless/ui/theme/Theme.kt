package id.sch.osis.paperless.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.sch.osis.paperless.domain.OrgCategory
import id.sch.osis.paperless.resources.Res
import id.sch.osis.paperless.resources.plusjakartasans_bold
import id.sch.osis.paperless.resources.plusjakartasans_extrabold
import id.sch.osis.paperless.resources.plusjakartasans_medium
import id.sch.osis.paperless.resources.plusjakartasans_regular
import id.sch.osis.paperless.resources.plusjakartasans_semibold
import org.jetbrains.compose.resources.Font

/**
 * Sistem warna "Modern Academic & Youth Productivity" — nuansa putih abu-abu & langit biru SMA.
 */
object OsisColors {
    val Primary = Color(0xFF8DB2FA)        // Soft Cornflower Blue
    val PrimaryDark = Color(0xFF4A7CE0)    // Link, ikon aktif, hover
    val PrimarySoft = Color(0xFFEAF1FE)    // Container lembut
    val Background = Color(0xFFF4F7FC)     // Cool Paper White
    val Surface = Color(0xFFFFFFFF)        // Kartu bersih
    val SurfaceMuted = Color(0xFFF8FAFE)
    val Border = Color(0xFFDCE6F7)         // Garis kartu halus
    val TextPrimary = Color(0xFF1E293B)    // Slate Navy
    val TextSecondary = Color(0xFF64748B)  // Slate Muted

    // Semantik
    val HighlightLeader = Color(0xFFFDE047)       // Kuning Stabilo lembut
    val HighlightLeaderBg = Color(0xFFFEF9C3)
    val HighlightParticipant = Color(0xFFA7F3D0)  // Mint lembut
    val HighlightParticipantBg = Color(0xFFD1FAE5)
    val Success = Color(0xFF22C55E)
    val SuccessSoft = Color(0xFFDCFCE7)
    val SuccessDark = Color(0xFF15803D)
    val Danger = Color(0xFFEF4444)
    val DangerSoft = Color(0xFFFEE2E2)
    val DangerDark = Color(0xFFB91C1C)
    val Warning = Color(0xFFF59E0B)
    val WarningSoft = Color(0xFFFEF3C7)
    val WarningDark = Color(0xFFB45309)

    // Badge pastel kategori organisasi
    fun categoryColors(category: OrgCategory): Pair<Color, Color> = when (category) {
        OrgCategory.PIMPINAN -> Color(0xFFE0EBFF) to Color(0xFF1D4ED8)
        OrgCategory.SEKBID -> Color(0xFFE0F2FE) to Color(0xFF0369A1)
        OrgCategory.OSIS_FRAKSI -> Color(0xFFEDE9FE) to Color(0xFF6D28D9)
        OrgCategory.EKSKUL -> Color(0xFFDCFCE7) to Color(0xFF15803D)
        OrgCategory.MPK -> Color(0xFFFFEDD5) to Color(0xFFC2410C)
    }
}

private val LightColorScheme = lightColorScheme(
    primary = OsisColors.PrimaryDark,
    onPrimary = Color.White,
    primaryContainer = OsisColors.PrimarySoft,
    onPrimaryContainer = OsisColors.TextPrimary,
    secondary = OsisColors.Primary,
    onSecondary = OsisColors.TextPrimary,
    secondaryContainer = OsisColors.SurfaceMuted,
    onSecondaryContainer = OsisColors.TextPrimary,
    background = OsisColors.Background,
    onBackground = OsisColors.TextPrimary,
    surface = OsisColors.Surface,
    onSurface = OsisColors.TextPrimary,
    surfaceVariant = OsisColors.SurfaceMuted,
    onSurfaceVariant = OsisColors.TextSecondary,
    outline = OsisColors.Border,
    outlineVariant = OsisColors.Border,
    error = OsisColors.Danger,
    onError = Color.White,
    errorContainer = OsisColors.DangerSoft,
    onErrorContainer = OsisColors.DangerDark,
)

private val OsisShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(22.dp),
)

@Composable
fun plusJakartaFontFamily(): FontFamily = FontFamily(
    Font(Res.font.plusjakartasans_regular, FontWeight.Normal),
    Font(Res.font.plusjakartasans_medium, FontWeight.Medium),
    Font(Res.font.plusjakartasans_semibold, FontWeight.SemiBold),
    Font(Res.font.plusjakartasans_bold, FontWeight.Bold),
    Font(Res.font.plusjakartasans_extrabold, FontWeight.ExtraBold),
)

@Composable
fun OsisPaperlessTheme(content: @Composable () -> Unit) {
    val family = plusJakartaFontFamily()
    val typography = Typography(
        headlineMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
        headlineSmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 25.sp),
        titleLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 23.sp),
        titleMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 21.sp),
        titleSmall = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp),
        bodyLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
        bodyMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 13.5.sp, lineHeight = 20.sp),
        bodySmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp),
        labelLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp),
        labelMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 11.5.sp, lineHeight = 16.sp),
        labelSmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 10.5.sp, lineHeight = 14.sp),
    )

    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = typography,
        shapes = OsisShapes,
        content = content,
    )
}
