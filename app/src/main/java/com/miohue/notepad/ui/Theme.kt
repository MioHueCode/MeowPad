package com.miohue.notepad.ui

import android.content.Context
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.miohue.notepad.R
import com.miohue.notepad.ui.theme.DarkBlackColors
import com.miohue.notepad.ui.theme.DarkBlueColors
import com.miohue.notepad.ui.theme.DarkCyanColors
import com.miohue.notepad.ui.theme.DarkGreenColors
import com.miohue.notepad.ui.theme.DarkOrangeColors
import com.miohue.notepad.ui.theme.DarkRedColors
import com.miohue.notepad.ui.theme.LightBlackColors
import com.miohue.notepad.ui.theme.LightBlueColors
import com.miohue.notepad.ui.theme.LightCyanColors
import com.miohue.notepad.ui.theme.LightGreenColors
import com.miohue.notepad.ui.theme.LightOrangeColors
import com.miohue.notepad.ui.theme.LightRedColors

// ============================ 主题枚举（对齐 Kori AppTheme / AppColor） ============================

/** 深浅模式，取值与 Kori `AppTheme` 一致（0/1/2）。 */
enum class ThemeMode(private val value: Int) {
    SYSTEM(0),
    LIGHT(1),
    DARK(2);

    val isDark: Boolean
        get() = this == DARK

    companion object {
        fun fromInt(value: Int) = entries.firstOrNull { it.value == value } ?: SYSTEM
        fun ThemeMode.toInt() = value
    }
}

/** 深浅模式的本地化显示名（原 `ThemeMode.displayName` 硬编码中文，改为资源解析）。 */
@Composable
fun themeModeDisplayName(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> stringResource(R.string.set_013)
    ThemeMode.LIGHT -> stringResource(R.string.theme_001)
    ThemeMode.DARK -> stringResource(R.string.theme_002)
}

/** 配色方案，取值与 Kori `AppColor` 一致（0..7）。 */
enum class AppColor(private val value: Int) {
    DYNAMIC(0),
    PURPLE(1),
    BLUE(2),
    GREEN(3),
    ORANGE(4),
    RED(5),
    CYAN(6),
    BLACK(7);

    companion object {
        fun fromInt(value: Int) = entries.firstOrNull { it.value == value } ?: DYNAMIC
        fun AppColor.toInt() = value
    }
}

// 色彩过渡动画时长常量（毫秒），与 Kori 一致
private const val COLOR_ANIMATION_DURATION = 700

// 通过对象而不是变量来跟踪首次启动状态，避免多处状态不一致
private var isFirstLaunch = true

data class AppConfig(
    val darkMode: Boolean = false,
    val amoledMode: Boolean = false,
    val fontScale: Float = 1f
)

val LocalAppConfig = staticCompositionLocalOf { AppConfig() }

/** 使颜色变暗的辅助函数 */
private fun Color.darken(factor: Float): Color = lerp(this, Color.Black, factor)

/** 为颜色方案中的单个颜色创建动画 */
@Composable
private fun animateColor(targetValue: Color): Color =
    animateColorAsState(
        targetValue = targetValue,
        animationSpec = tween(COLOR_ANIMATION_DURATION)
    ).value

/** 为颜色方案创建平滑过渡动画（对齐 Kori `animateColorSchemeAsState`） */
@Composable
private fun animateColorSchemeAsState(targetColorScheme: ColorScheme): ColorScheme = ColorScheme(
    primary = animateColor(targetColorScheme.primary),
    onPrimary = animateColor(targetColorScheme.onPrimary),
    primaryContainer = animateColor(targetColorScheme.primaryContainer),
    onPrimaryContainer = animateColor(targetColorScheme.onPrimaryContainer),
    inversePrimary = animateColor(targetColorScheme.inversePrimary),
    secondary = animateColor(targetColorScheme.secondary),
    onSecondary = animateColor(targetColorScheme.onSecondary),
    secondaryContainer = animateColor(targetColorScheme.secondaryContainer),
    onSecondaryContainer = animateColor(targetColorScheme.onSecondaryContainer),
    tertiary = animateColor(targetColorScheme.tertiary),
    onTertiary = animateColor(targetColorScheme.onTertiary),
    tertiaryContainer = animateColor(targetColorScheme.tertiaryContainer),
    onTertiaryContainer = animateColor(targetColorScheme.onTertiaryContainer),
    background = animateColor(targetColorScheme.background),
    onBackground = animateColor(targetColorScheme.onBackground),
    surface = animateColor(targetColorScheme.surface),
    onSurface = animateColor(targetColorScheme.onSurface),
    surfaceVariant = animateColor(targetColorScheme.surfaceVariant),
    onSurfaceVariant = animateColor(targetColorScheme.onSurfaceVariant),
    surfaceTint = animateColor(targetColorScheme.surfaceTint),
    inverseSurface = animateColor(targetColorScheme.inverseSurface),
    inverseOnSurface = animateColor(targetColorScheme.inverseOnSurface),
    error = animateColor(targetColorScheme.error),
    onError = animateColor(targetColorScheme.onError),
    errorContainer = animateColor(targetColorScheme.errorContainer),
    onErrorContainer = animateColor(targetColorScheme.onErrorContainer),
    outline = animateColor(targetColorScheme.outline),
    outlineVariant = animateColor(targetColorScheme.outlineVariant),
    scrim = animateColor(targetColorScheme.scrim),
    surfaceBright = animateColor(targetColorScheme.surfaceBright),
    surfaceDim = animateColor(targetColorScheme.surfaceDim),
    surfaceContainer = animateColor(targetColorScheme.surfaceContainer),
    surfaceContainerHigh = animateColor(targetColorScheme.surfaceContainerHigh),
    surfaceContainerHighest = animateColor(targetColorScheme.surfaceContainerHighest),
    surfaceContainerLow = animateColor(targetColorScheme.surfaceContainerLow),
    surfaceContainerLowest = animateColor(targetColorScheme.surfaceContainerLowest)
)

/** 扩展函数：根据缩放因子调整排版中的所有字体大小（对齐 Kori）。 */
internal fun Typography.withScaledFontSizes(scale: Float): Typography {
    if (scale == 1f) return this
    return this.copy(
        displayLarge = displayLarge.scale(scale),
        displayMedium = displayMedium.scale(scale),
        displaySmall = displaySmall.scale(scale),
        headlineLarge = headlineLarge.scale(scale),
        headlineMedium = headlineMedium.scale(scale),
        headlineSmall = headlineSmall.scale(scale),
        titleLarge = titleLarge.scale(scale),
        titleMedium = titleMedium.scale(scale),
        titleSmall = titleSmall.scale(scale),
        bodyLarge = bodyLarge.scale(scale),
        bodyMedium = bodyMedium.scale(scale),
        bodySmall = bodySmall.scale(scale),
        labelLarge = labelLarge.scale(scale),
        labelMedium = labelMedium.scale(scale),
        labelSmall = labelSmall.scale(scale)
    )
}

private fun TextStyle.scale(factor: Float): TextStyle = this.copy(fontSize = fontSize * factor)

// ============================ 配色查找（对齐 Kori StylePane 的 dark/lightColorOptions） ============================

/** 深色配色表（顺序与 Kori 一致） */
val darkColorOptions: List<Pair<AppColor, ColorScheme>>
    get() = listOf(
        AppColor.PURPLE to darkColorScheme(),
        AppColor.BLUE to DarkBlueColors,
        AppColor.GREEN to DarkGreenColors,
        AppColor.ORANGE to DarkOrangeColors,
        AppColor.RED to DarkRedColors,
        AppColor.CYAN to DarkCyanColors,
        AppColor.BLACK to DarkBlackColors
    )

/** 浅色配色表（顺序与 Kori 一致；Kori 的 PURPLE 用 Material3 expressive 默认色，此处用 M3 默认色） */
val lightColorOptions: List<Pair<AppColor, ColorScheme>>
    get() = listOf(
        AppColor.PURPLE to lightColorScheme(),
        AppColor.BLUE to LightBlueColors,
        AppColor.GREEN to LightGreenColors,
        AppColor.ORANGE to LightOrangeColors,
        AppColor.RED to LightRedColors,
        AppColor.CYAN to LightCyanColors,
        AppColor.BLACK to LightBlackColors
    )

/** 按配色 id 取色（静态，无动画）。DYNAMIC 需要 Context，见 [buildColorScheme]。 */
fun staticColorScheme(color: AppColor, dark: Boolean): ColorScheme {
    val options = if (dark) darkColorOptions else lightColorOptions
    return options.firstOrNull { it.first == color }?.second ?: options.first().second
}

fun buildColorScheme(context: Context, color: AppColor, dark: Boolean): ColorScheme {
    if (color == AppColor.DYNAMIC) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        // Android 12 以下无动态取色，回退到 M3 默认（紫）
        return staticColorScheme(AppColor.PURPLE, dark)
    }
    return staticColorScheme(color, dark)
}

@Composable
fun isDark(mode: ThemeMode): Boolean =
    mode == ThemeMode.DARK || (mode == ThemeMode.SYSTEM && isSystemInDarkTheme())

/**
 * 应用主题（对齐 Kori `ExpressiveTheme`）：配色动画过渡 + AMOLED 压暗 + 字体缩放。
 */
@Composable
fun NotePadTheme(
    darkMode: Boolean,
    amoledMode: Boolean,
    color: AppColor,
    fontScale: Float = 1f,
    animateColors: Boolean = true,
    content: @Composable () -> Unit
) {
    val targetColorScheme = buildColorScheme(LocalContext.current, color, darkMode)

    // 首次启动，直接使用目标颜色方案；非首次且启用动画时使用动画过渡
    val colorScheme = if (isFirstLaunch || !animateColors) targetColorScheme
    else animateColorSchemeAsState(targetColorScheme)

    LaunchedEffect(Unit) { isFirstLaunch = false }

    // 仅在深色模式下处理 AMOLED 选项
    val finalColorScheme = if (darkMode) {
        val contentDimmingFactor by animateFloatAsState(
            targetValue = if (amoledMode) 0.1f else 0f,
            animationSpec = tween(durationMillis = COLOR_ANIMATION_DURATION),
            label = "ContentDimming"
        )
        val backgroundDimmingFactor by animateFloatAsState(
            targetValue = if (amoledMode) 1f else 0f,
            animationSpec = tween(durationMillis = COLOR_ANIMATION_DURATION),
            label = "BackgroundDimming"
        )
        remember(colorScheme, contentDimmingFactor, backgroundDimmingFactor) {
            colorScheme.copy(
                background = colorScheme.background.darken(backgroundDimmingFactor),
                surface = colorScheme.surface.darken(backgroundDimmingFactor),
                surfaceContainer = colorScheme.surfaceContainer.darken(backgroundDimmingFactor),
                surfaceVariant = colorScheme.surfaceVariant.darken(contentDimmingFactor),
                surfaceContainerLowest = colorScheme.surfaceContainerLowest.darken(contentDimmingFactor),
                surfaceContainerLow = colorScheme.surfaceContainerLow.darken(contentDimmingFactor),
                surfaceContainerHigh = colorScheme.surfaceContainerHigh.darken(contentDimmingFactor),
                surfaceContainerHighest = colorScheme.surfaceContainerHighest.darken(contentDimmingFactor),
                surfaceDim = colorScheme.surfaceDim.darken(contentDimmingFactor),
                surfaceBright = colorScheme.surfaceBright.darken(contentDimmingFactor),
                scrim = colorScheme.scrim.darken(contentDimmingFactor),
                inverseSurface = colorScheme.inverseSurface.darken(contentDimmingFactor),
                primaryContainer = colorScheme.primaryContainer.darken(contentDimmingFactor),
                secondaryContainer = colorScheme.secondaryContainer.darken(contentDimmingFactor),
                tertiaryContainer = colorScheme.tertiaryContainer.darken(contentDimmingFactor),
                errorContainer = colorScheme.errorContainer.darken(contentDimmingFactor)
            )
        }
    } else colorScheme

    val scaledTypography = remember(fontScale) { Typography().withScaledFontSizes(fontScale) }

    // 统一圆角体系：比 M3 默认更圆润，卡片/对话框/输入框视觉一致
    val appShapes = remember {
        Shapes(
            extraSmall = RoundedCornerShape(6.dp),
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(14.dp),
            large = RoundedCornerShape(20.dp),
            extraLarge = RoundedCornerShape(28.dp)
        )
    }

    CompositionLocalProvider(LocalAppConfig provides AppConfig(darkMode, amoledMode, fontScale)) {
        MaterialTheme(
            colorScheme = finalColorScheme,
            typography = scaledTypography,
            shapes = appShapes,
            content = content
        )
    }
}

/**
 * 猫咪皮肤：覆盖主色三角色 + 次要/第三色 + 表面微染，让皮肤切换有明显视觉效果。
 * 与当前配色 lerp 混色，保持可读性。skin: default(不处理) / calico / black / white / tabby / ginger。
 */
fun ColorScheme.withCatSkin(skin: String, dark: Boolean): ColorScheme {
    if (skin == "default") return this
    // 各肤色的主色（浅深两套）
    val accent = when (skin) {
        "calico" -> if (dark) Color(0xFFFFB27D) else Color(0xFFB9651F)
        "black"  -> if (dark) Color(0xFFEDE0CF) else Color(0xFF3A3229)
        "white"  -> if (dark) Color(0xFFFFD9C4) else Color(0xFF8C5A2B)
        "tabby"  -> if (dark) Color(0xFFE6C27A) else Color(0xFF9A6B1F)
        "ginger" -> if (dark) Color(0xFFFFB59C) else Color(0xFFB0501F)
        else -> return this
    }
    val container = when (skin) {
        "calico" -> if (dark) Color(0xFF5C2A0A) else Color(0xFFFFDCC3)
        "black"  -> if (dark) Color(0xFF4A4238) else Color(0xFFEFE5D8)
        "white"  -> if (dark) Color(0xFF6A3A1A) else Color(0xFFFFE0CC)
        "tabby"  -> if (dark) Color(0xFF4A3A0A) else Color(0xFFF7E6C2)
        "ginger" -> if (dark) Color(0xFF5C1F0A) else Color(0xFFFFD9C4)
        else -> return this
    }
    // 次色/第三色（与主色同族的较浅/较深变体）
    val secondary = when (skin) {
        "calico" -> if (dark) Color(0xFFB0805A) else Color(0xFF8C5A2B)
        "black"  -> if (dark) Color(0xFFB0A898) else Color(0xFF6A5A48)
        "white"  -> if (dark) Color(0xFFD0B098) else Color(0xFFA07050)
        "tabby"  -> if (dark) Color(0xFFC0A060) else Color(0xFF8C6A28)
        "ginger" -> if (dark) Color(0xFFB08060) else Color(0xFF9A5A30)
        else -> return this
    }
    // lerp 主色 0.55 倾向皮肤色，0.45 保留原配色；次色/第三色 0.4；表面微染 0.12
    return copy(
        primary = lerp(primary, accent, 0.55f),
        primaryContainer = lerp(primaryContainer, container, 0.55f),
        onPrimaryContainer = lerp(onPrimaryContainer, if (dark) Color(0xFFFFE9D6) else Color(0xFF3A1F0A), 0.55f),
        secondary = lerp(secondary, secondary, 0.40f),
        onSecondary = lerp(onSecondary, if (dark) Color(0xFFFFE9D6) else Color(0xFF3A1F0A), 0.40f),
        tertiary = lerp(tertiary, secondary, 0.35f),
        surfaceContainer = lerp(surfaceContainer, container, 0.12f),
        surfaceContainerHigh = lerp(surfaceContainerHigh, container, 0.10f)
    )
}

/**
 * 纸质风格：暖纸色调覆盖背景/表面系角色，营造纸张质感（无图片纹理）。
 * 浅色 #F6F1E5 系，深色 #191613 系。paperMode 为 true 时生效。
 */
fun ColorScheme.withPaperStyle(paperMode: Boolean, dark: Boolean): ColorScheme {
    if (!paperMode) return this
    val bg = if (dark) Color(0xFF191613) else Color(0xFFF6F1E5)
    val surf = if (dark) Color(0xFF211D19) else Color(0xFFF8F3E8)
    val onBg = if (dark) Color(0xFFE8E0D2) else Color(0xFF2E2820)
    return copy(
        background = bg,
        onBackground = onBg,
        surface = surf,
        onSurface = if (dark) Color(0xFFEDE5D7) else Color(0xFF332C24),
        surfaceContainerLowest = if (dark) Color(0xFF14110E) else Color(0xFFF3EDE0),
        surfaceContainerLow = if (dark) Color(0xFF1C1815) else Color(0xFFF5F0E4),
        surfaceContainer = if (dark) Color(0xFF211D19) else Color(0xFFF8F3E8),
        surfaceContainerHigh = if (dark) Color(0xFF26211D) else Color(0xFFFBF6EC),
        surfaceContainerHighest = if (dark) Color(0xFF2B2621) else Color(0xFFFDF9F0),
        surfaceVariant = if (dark) Color(0xFF2B2621) else Color(0xFFEDE6D8),
        onSurfaceVariant = if (dark) Color(0xFFCFC5B5) else Color(0xFF5A5248),
        surfaceDim = surf,
        surfaceBright = if (dark) Color(0xFF2E2823) else Color(0xFFFBF7EE),
        outline = if (dark) Color(0xFF8A8072) else Color(0xFF8A8074),
        outlineVariant = if (dark) Color(0xFF3A342C) else Color(0xFFD6CEC0)
    )
}
