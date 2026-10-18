package com.miohue.notepad.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.content.SharedPreferences
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.miohue.notepad.data.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import androidx.compose.ui.res.stringResource
import com.miohue.notepad.R
import com.miohue.notepad.LocaleManager

/** 应用版本号（与 app/build.gradle.kts versionName 保持同步） */
const val APP_VERSION_NAME = "1.9.0"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    prefs: SharedPreferences,
    repo: NoteRepository,
    context: Context,
    mode: ThemeMode,
    appColor: AppColor,
    isAmoled: Boolean,
    fontSize: Float,
    skin: String,
    paperMode: Boolean,
    onMode: (ThemeMode) -> Unit,
    onColor: (AppColor) -> Unit,
    onAmoled: (Boolean) -> Unit,
    onFontSize: (Float) -> Unit,
    onSkin: (String) -> Unit,
    onPaperMode: (Boolean) -> Unit,
    onBackup: () -> Unit,
    onBack: () -> Unit
) {
    var pane by remember { mutableStateOf<String?>(null) }
    var showLangDialog by remember { mutableStateOf(false) }
    // 注意：这里必须用 LocalContext.current（即 Activity），不能用 applicationContext，
    // 否则 applyLanguage 里的 Activity.recreate() 会静默失效（app context 不是 Activity）。
    val context = LocalContext.current
    val ctx = context.applicationContext
    // 侧滑返回：弹出 pane 先收起，主页再返回列表（修复子页侧滑无响应 bug）
    BackHandler(enabled = true) {
        if (pane != null) pane = null else onBack()
    }
    Box(Modifier.fillMaxSize()) {
        Scaffold(containerColor = Color.Transparent, contentWindowInsets = WindowInsets(0, 0, 0, 0), topBar = {
            TopAppBar(title = { Text(stringResource(R.string.list_029)) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent))
        }) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
                // 外观
                ListPaneSection(items = listOf {
                    ListPaneItem(isSelected = false, title = stringResource(R.string.set_001),
                        description = if (paperMode) stringResource(R.string.set_002) else stringResource(R.string.edit_049), icon = Icons.Default.MenuBook, onClick = { onPaperMode(!paperMode) })
                    ListPaneItem(isSelected = false, title = stringResource(R.string.set_003),
                        description = skinDisplayName(skin), icon = Icons.Default.Pets, onClick = { pane = "skin" })
                    ListPaneItem(isSelected = pane == "theme", title = stringResource(R.string.sc_001),
                        description = stringResource(R.string.sc_002), icon = Icons.Default.Palette, onClick = { pane = "theme" })
                })
                // 编辑器
                ListPaneSection(items = listOf(
                    {
                        ListPaneItem(isSelected = pane == "editor", title = stringResource(R.string.set_004),
                            description = stringResource(R.string.set_005), icon = Icons.Default.Edit, onClick = { pane = "editor" })
                    },
                    {
                        ListPaneItem(isSelected = pane == "card", title = stringResource(R.string.set_006),
                            description = stringResource(R.string.set_007), icon = Icons.Default.GridView, onClick = { pane = "card" })
                    }
                ))
                // 语言：应用内直接切换（不再跳系统设置页——文案已全部本地化，切换后 recreate 立即生效）
                ListPaneSection(items = listOf {
                    ListPaneItem(isSelected = false, title = stringResource(R.string.set_008),
                        description = languageDisplayName(context, prefs), icon = Icons.Default.Language,
                        onClick = { showLangDialog = true })
                })
                // AI 模型：摘要 / 润色 / 翻译 / 续写（参考 Kori AiPane 的四要素：provider + baseUrl + key + model）
                ListPaneSection(items = listOf {
                    ListPaneItem(isSelected = pane == "ai", title = stringResource(R.string.ai_022),
                        description = stringResource(R.string.ai_023), icon = Icons.Default.AutoAwesome, onClick = { pane = "ai" })
                })
                // 数据
                ListPaneSection(items = listOf(
                    {
                        ListPaneItem(isSelected = pane == "data", title = stringResource(R.string.list_037),
                            description = stringResource(R.string.sc_003), icon = Icons.Default.Folder, onClick = { pane = "data" })
                    },
                    {
                        ListPaneItem(isSelected = pane == "backup", title = stringResource(R.string.set_009),
                            description = stringResource(R.string.set_010), icon = Icons.Default.Cloud, onClick = { pane = "backup" })
                    }
                ))
                // 关于
                ListPaneSection(items = listOf {
                    ListPaneItem(isSelected = pane == "about", title = stringResource(R.string.set_011),
                        description = "MeowPad  v$APP_VERSION_NAME", icon = Icons.Default.Info,
                        onClick = { pane = "about" })
                })
                Spacer(Modifier.height(24.dp))
            }
        }
        // ==================== 弹出 pane 层（scrim + 右侧滑入，对齐编辑页侧栏） ====================
        SettingsPaneOverlay(visible = pane == "theme", title = stringResource(R.string.sc_001), onDismiss = { pane = null }) {
            ThemePaneContent(mode, appColor, isAmoled, fontSize, skin, paperMode, onMode, onColor, onAmoled, onFontSize, onSkin, onPaperMode)
        }
        SettingsPaneOverlay(visible = pane == "skin", title = stringResource(R.string.set_003), onDismiss = { pane = null }) {
            SkinPaneContent(skin, onSkin)
        }
        SettingsPaneOverlay(visible = pane == "editor", title = stringResource(R.string.set_004), onDismiss = { pane = null }) {
            EditorPaneContent(prefs)
        }
        SettingsPaneOverlay(visible = pane == "card", title = stringResource(R.string.set_006), onDismiss = { pane = null }) {
            CardPaneContent(prefs)
        }
        SettingsPaneOverlay(visible = pane == "data", title = stringResource(R.string.list_037), onDismiss = { pane = null }) {
            DataPaneContent(prefs, repo, context, onBackup)
        }
        SettingsPaneOverlay(visible = pane == "backup", title = stringResource(R.string.set_009), onDismiss = { pane = null }) {
            BackupPaneContent(prefs, repo, context)
        }
        SettingsPaneOverlay(visible = pane == "ai", title = stringResource(R.string.ai_022), onDismiss = { pane = null }) {
            AiPaneContent(prefs)
        }
        SettingsPaneOverlay(visible = pane == "about", title = stringResource(R.string.set_011), onDismiss = { pane = null }) {
            AboutPaneContent(context, repo)
        }
        if (showLangDialog) {
            LanguageDialog(
                current = prefs.getString(LocaleManager.KEY_LANGUAGE, "system") ?: "system",
                onPick = { lang ->
                    showLangDialog = false
                    applyLanguage(context, lang)
                },
                onDismiss = { showLangDialog = false }
            )
        }
    }
}

private fun languageDisplayName(context: Context, prefs: SharedPreferences): String = when (prefs.getString(LocaleManager.KEY_LANGUAGE, "system") ?: "system") {
    "zh-CN" -> context.getString(R.string.set_012)
    "en" -> "English"
    else -> context.getString(R.string.set_013)
}

/**
 * 应用内切换语言：写入 prefs，并让新的 Configuration 生效。
 *
 * - Android 13+：`setApplicationLocales` 会由系统触发配置变更并重建 Activity，无需手动 recreate
 *   （重复 recreate 会闪两次）。
 * - 低版本：没有 system API，手动 `recreate()` 让 `attachBaseContext` 重新包装 locale。
 */
private fun applyLanguage(context: Context, language: String) {
    LocaleManager.setLanguage(context, language)
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
        (context as? android.app.Activity)?.recreate()
    }
}

/** 语言选择对话框：跟随系统 / 简体中文 / English。 */
@Composable
private fun LanguageDialog(current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val options = listOf(
        "system" to stringResource(R.string.set_013),
        "zh-CN" to stringResource(R.string.set_012),
        "en" to "English"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.set_008)) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(value) }
                            .padding(vertical = 12.dp)
                    ) {
                        RadioButton(selected = current == value, onClick = { onPick(value) })
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.memo_010)) } }
    )
}

// ============================ 主题 pane 内容（无 Scaffold，套入 SettingsPaneOverlay） ============================

@Composable
private fun ThemePaneContent(
    mode: ThemeMode, appColor: AppColor, isAmoled: Boolean, fontSize: Float,
    skin: String, paperMode: Boolean,
    onMode: (ThemeMode) -> Unit, onColor: (AppColor) -> Unit, onAmoled: (Boolean) -> Unit,
    onFontSize: (Float) -> Unit, onSkin: (String) -> Unit, onPaperMode: (Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            SettingsHeader(stringResource(R.string.set_014))
            Box(modifier = Modifier.padding(top = 8.dp)) {
                StyledPaletteImage()
            }
        }
        Spacer(Modifier.height(8.dp))
        PaletteColorRow(appColor, onColor)
        Spacer(Modifier.height(16.dp))
        SettingsHeader(stringResource(R.string.set_015))
        FontSizeSlider(fontSize, onFontSize)
        Spacer(Modifier.height(16.dp))
        SettingsHeader(stringResource(R.string.set_016))
        DarkModeRow(mode, onMode, isAmoled, onAmoled)
        Spacer(Modifier.height(16.dp))
        SettingsHeader(stringResource(R.string.set_003))
        SkinSelector(skin, onSkin)
        Spacer(Modifier.height(16.dp))
        SettingsHeader(stringResource(R.string.set_001))
        DetailPaneItem(
            title = stringResource(R.string.set_017),
            description = stringResource(R.string.set_018),
            icon = Icons.Default.MenuBook,
            trailingContent = {
                Switch(
                    checked = paperMode,
                    onCheckedChange = { checked ->
                        onPaperMode(checked)
                    }
                )
            }
        )
    }
}

// ============================ 编辑器 pane（对齐 Kori EditorPane：默认视图 + 行号） ============================

@Composable
private fun EditorPaneContent(prefs: SharedPreferences) {
    var defaultReading by remember { mutableStateOf(prefs.getBoolean("default_reading_view", false)) }
    var showLineNumbers by remember { mutableStateOf(prefs.getBoolean("show_line_numbers", true)) }
    val haptic = LocalHapticFeedback.current
    SettingsHeader(stringResource(R.string.set_019))
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            stringResource(R.string.set_020),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        SegmentedControlRow(
            labels = listOf(stringResource(R.string.set_021), stringResource(R.string.set_022)),
            selectedIndex = if (defaultReading) 1 else 0,
            onSelect = { i ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                defaultReading = i == 1
                prefs.edit().putBoolean("default_reading_view", i == 1).apply()
            }
        )
    }
    Spacer(Modifier.height(16.dp))
    SettingsHeader(stringResource(R.string.set_023))
    DetailPaneItem(
        title = stringResource(R.string.set_024),
        description = stringResource(R.string.set_025),
        icon = Icons.Default.FormatListNumbered,
        trailingContent = {
            Switch(
                checked = showLineNumbers,
                onCheckedChange = { checked ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showLineNumbers = checked
                    prefs.edit().putBoolean("show_line_numbers", checked).apply()
                }
            )
        }
    )
}

// ============================ 卡片 pane（对齐 Kori CardPane：实时预览 + 卡片大小） ============================

@Composable
private fun CardPaneContent(prefs: SharedPreferences) {
    var cardSize by remember { mutableStateOf(prefs.getInt("card_size", 0)) }
    val haptic = LocalHapticFeedback.current
    SettingsHeader(stringResource(R.string.set_026))
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SamplePreviewCard(
            title = stringResource(R.string.set_027),
            content = stringResource(R.string.set_028),
            isTodo = true,
            cardSize = cardSize
        )
        SamplePreviewCard(
            title = stringResource(R.string.set_029),
            content = stringResource(R.string.set_030),
            isTodo = false,
            cardSize = cardSize
        )
    }
    Spacer(Modifier.height(16.dp))
    SettingsHeader(stringResource(R.string.set_031))
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        SegmentedControlRow(
            labels = listOf(stringResource(R.string.repo_001), stringResource(R.string.set_032), stringResource(R.string.set_033)),
            selectedIndex = cardSize,
            onSelect = { i ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                cardSize = i
                prefs.edit().putInt("card_size", i).apply()
            }
        )
        Spacer(Modifier.height(8.dp))
        Text(
            when (cardSize) {
                1 -> stringResource(R.string.set_034)
                2 -> stringResource(R.string.set_035)
                else -> stringResource(R.string.set_036)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Spacer(Modifier.height(8.dp))
}

// ==================== 猫咪皮肤选择器 ====================

private val CAT_SKIN_KEYS = listOf(
    "default" to R.string.repo_001,
    "calico" to R.string.set_037,
    "black" to R.string.set_038,
    "white" to R.string.set_039,
    "tabby" to R.string.set_040,
    "ginger" to R.string.set_041
)

/** 皮肤本地化显示名（原 `skinDisplayName` 硬编码中文）。 */
@Composable
fun skinDisplayName(skin: String): String =
    stringResource(CAT_SKIN_KEYS.find { it.first == skin }?.second ?: R.string.repo_001)

@Composable
private fun SkinPaneContent(
    skin: String,
    onSkin: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        CAT_SKIN_KEYS.forEach { (id, res) ->
            val name = stringResource(res)
            ListItem(
                headlineContent = { Text(name) },
                leadingContent = {
                    Box(
                        Modifier
                            .size(24.dp)
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        when (id) {
                            "calico" -> Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFFFF6B6B))
                            "black" -> Icon(Icons.Default.Favorite, contentDescription = null, tint = Color.Black)
                            "white" -> Icon(Icons.Default.Favorite, contentDescription = null, tint = Color.White, modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape))
                            "tabby" -> Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFF8D6E63))
                            "ginger" -> Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFFFF9800))
                            else -> Icon(Icons.Default.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                trailingContent = {
                    if (skin == id) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSkin(id)
                    }
            )
        }
    }
}

@Composable
private fun SkinSelector(
    skin: String,
    onSkin: (String) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CAT_SKIN_KEYS.forEach { (id, res) ->
            val name = stringResource(res)
                val selected = skin == id
                Box(
                    Modifier
                        .clip(CircleShape)
                        .clickable { onSkin(id) }
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when (id) {
                        "calico" -> Icon(Icons.Default.Favorite, contentDescription = name, tint = Color(0xFFFF6B6B), modifier = Modifier.size(28.dp))
                        "black" -> Icon(Icons.Default.Favorite, contentDescription = name, tint = Color.Black, modifier = Modifier.size(28.dp))
                        "white" -> Icon(Icons.Default.Favorite, contentDescription = name, tint = Color.White, modifier = Modifier.size(28.dp).border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape))
                        "tabby" -> Icon(Icons.Default.Favorite, contentDescription = name, tint = Color(0xFF8D6E63), modifier = Modifier.size(28.dp))
                        "ginger" -> Icon(Icons.Default.Favorite, contentDescription = name, tint = Color(0xFFFF9800), modifier = Modifier.size(28.dp))
                        else -> Icon(Icons.Default.Favorite, contentDescription = name, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    }
                    if (selected) {
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SamplePreviewCard(title: String, content: String, isTodo: Boolean, cardSize: Int) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            Modifier.padding(if (cardSize == 2) 8.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isTodo) {
                    Icon(
                        Icons.Default.CheckBox, null, Modifier.size(16.dp), tint = scheme.primary
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "10-14 08:30",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant
                )
            }
            if (cardSize != 1 && content.isNotBlank()) {
                Text(
                    content,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = if (cardSize == 2) 1 else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsScreen(
    mode: ThemeMode, appColor: AppColor, isAmoled: Boolean, fontSize: Float,
    onMode: (ThemeMode) -> Unit, onColor: (AppColor) -> Unit, onAmoled: (Boolean) -> Unit,
    onFontSize: (Float) -> Unit, onBack: () -> Unit
) {
    Scaffold(containerColor = Color.Transparent, contentWindowInsets = WindowInsets(0, 0, 0, 0), topBar = {
        TopAppBar(windowInsets = WindowInsets(0, 0, 0, 0), title = { Text(stringResource(R.string.sc_001)) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
        }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent))
    }) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            // 配色预览卡
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                SettingsHeader(stringResource(R.string.set_014))
                Box(modifier = Modifier.padding(top = 8.dp)) {
                    StyledPaletteImage()
                }
            }
            Spacer(Modifier.height(8.dp))
            // 配色圆盘
            PaletteColorRow(appColor, onColor)
            Spacer(Modifier.height(16.dp))
            // 字体大小
            SettingsHeader(stringResource(R.string.set_015))
            FontSizeSlider(fontSize, onFontSize)
            Spacer(Modifier.height(16.dp))
            // 深色模式
            SettingsHeader(stringResource(R.string.set_016))
            DarkModeRow(mode, onMode, isAmoled, onAmoled)
        }
    }
}

@Composable
private fun StyledPaletteImage() = Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
    Box(modifier = Modifier.sizeIn(maxWidth = 480.dp).padding(horizontal = 16.dp)
        .clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(16.dp),
        contentAlignment = Alignment.Center) {
        PaletteImage()
    }
}

@Composable
private fun PaletteColorRow(selected: AppColor, onSelect: (AppColor) -> Unit) {
    val haptic = LocalHapticFeedback.current
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Spacer(modifier = Modifier.width(8.dp))
        // 显示浅色配色盘（用户选择后通过主题模式决定深浅）
        val options = remember { lightColorOptions }
        options.forEach { (c, scheme) ->
            SelectableColorPlatte(selected == c, scheme) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onSelect(c)
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
    }
}

@Composable
private fun PaletteImage() {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(modifier = Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)) {
            Text("A", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary)
        }
        Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary)) {}
        Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary)) {}
        Box(modifier = Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainer)) {
            Text(stringResource(R.string.set_042), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(modifier = Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primaryContainer)) {
            Text(stringResource(R.string.set_043), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun FontSizeSlider(fontSize: Float, onFontSize: (Float) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        var temp by remember { mutableStateOf(fontSize) }
        val pct = (fontSize * 100).toInt()
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FormatSize, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.set_015), style = MaterialTheme.typography.titleMedium)
            }
            Text("$pct%", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))
        Slider(value = temp, onValueChange = { temp = it }, valueRange = 0.85f..1.35f,
            onValueChangeFinished = { onFontSize(temp) })
    }
}

@Composable
private fun DarkModeRow(mode: ThemeMode, onMode: (ThemeMode) -> Unit, isAmoled: Boolean, onAmoled: (Boolean) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val modeOptions = listOf(
        ThemeMode.SYSTEM to Icons.Default.BrightnessAuto,
        ThemeMode.LIGHT to Icons.Default.LightMode,
        ThemeMode.DARK to Icons.Default.DarkMode
    )
    var showAmoledControl by remember { mutableStateOf(mode == ThemeMode.DARK) }
    val iconRotation by animateFloatAsState(targetValue = if (showAmoledControl) 90f else 0f, label = "rotationAnimation")
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        modeOptions.forEachIndexed { index, (m, icon) ->
            Row(modifier = Modifier.fillMaxWidth().height(48.dp)
                .selectable(selected = mode == m, onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onMode(m)
                    if (!m.isDark) showAmoledControl = false
                }, role = Role.RadioButton),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Text(themeModeDisplayName(m), style = MaterialTheme.typography.bodyLarge)
                }
                if (index == modeOptions.size - 1) {
                    IconButton(onClick = { showAmoledControl = !showAmoledControl }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.rotate(iconRotation))
                    }
                    VerticalDivider(Modifier.height(40.dp).padding(start = 2.dp, end = 16.dp))
                }
                RadioButton(selected = mode == m, onClick = null)
            }
            if (index < modeOptions.size - 1) Spacer(Modifier.height(4.dp))
        }
    }
    AnimatedVisibility(showAmoledControl) {
        Row(modifier = Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(modifier = Modifier.padding(start = 20.dp), checked = isAmoled,
                onCheckedChange = { onAmoled(it) })
            Text("AMOLED", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun SelectableColorPlatte(selected: Boolean, colorScheme: androidx.compose.material3.ColorScheme, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp)
            .size(56.dp),
        shape = CircleShape,
        color = colorScheme.primary
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (selected) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = colorScheme.onPrimary
                )
            }
        }
    }
}

// ============================ 数据页（Kori DataPane 结构） ============================

// ============================ 数据 pane 内容（无 Scaffold） ============================

@Composable
private fun DataPaneContent(prefs: SharedPreferences, repo: NoteRepository, context: Context, onBackup: () -> Unit) {
    var storagePath by remember { mutableStateOf(prefs.getString("storage_path", "") ?: "") }
    var showWarning by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val pickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            val treeId = android.provider.DocumentsContract.getTreeDocumentId(it)
            val realPath = "/storage/emulated/0/" + treeId.substringAfterLast(":")
            prefs.edit().putString("storage_path", realPath).apply()
            storagePath = realPath
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
            ListPaneItem(isSelected = false, title = stringResource(R.string.set_044), description = stringResource(R.string.set_045), icon = Icons.Default.Folder)
        } else {
            val hasPer = remember { android.os.Environment.isExternalStorageManager() }
            ListPaneItem(isSelected = hasPer, title = stringResource(R.string.set_046), description = stringResource(R.string.set_047),
                icon = if (hasPer) Icons.Default.Folder else Icons.Default.Warning,
                onClick = if (!hasPer) {
                    { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, android.net.Uri.parse("package:${context.packageName}"))) }
                } else null)
        }
        ListPaneItem(isSelected = false, title = stringResource(R.string.set_048), description = storagePath.ifBlank { "/storage/emulated/0/MeowPad" },
            icon = Icons.Default.Folder, onClick = { pickerLauncher.launch(Uri.parse("content://com.android.externalstorage.documents/tree/primary%3A")) })
        ListPaneItem(isSelected = false, title = stringResource(R.string.set_049), description = stringResource(R.string.set_050),
            icon = Icons.Default.Category,
            onClick = {
                statusText = context.getString(R.string.set_051)
                scope.launch {
                    withContext(Dispatchers.IO) { repo.migrateAttachmentsToRoot() }
                    statusText = context.getString(R.string.set_052)
                }
            })
        statusText?.let { t -> Text(t, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(16.dp)) }
        ListPaneItem(isSelected = false, title = stringResource(R.string.set_053), description = stringResource(R.string.set_054), icon = Icons.Default.DeleteForever,
            onClick = { showWarning = true })
    }
    if (showWarning) WarningDialog(stringResource(R.string.set_053), stringResource(R.string.set_055),
        { showWarning = false }, {
            showWarning = false
            repo.resetAll()
        })
}

// ============================ 备份 pane 内容（无 Scaffold） ============================

@Composable
private fun BackupPaneContent(prefs: SharedPreferences, repo: NoteRepository, context: Context) {
    var webdavUrl by remember { mutableStateOf(prefs.getString("webdav_url", "") ?: "") }
    var webdavUser by remember { mutableStateOf(prefs.getString("webdav_user", "") ?: "") }
    var webdavPass by remember { mutableStateOf(prefs.getString("webdav_pass", "") ?: "") }
    var status by remember { mutableStateOf<String?>(null) }
    var showRestorePicker by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsHeader(stringResource(R.string.set_056))
        ListPaneItem(isSelected = false, title = stringResource(R.string.set_057), description = stringResource(R.string.set_058), icon = Icons.Default.Download,
            onClick = {
                status = context.getString(R.string.set_059)
                scope.launch {
                    withContext(Dispatchers.IO) { repo.backup(context) }
                    status = context.getString(R.string.set_060)
                }
            })
        ListPaneItem(isSelected = false, title = stringResource(R.string.set_061), description = stringResource(R.string.set_062), icon = Icons.Default.Upload,
            onClick = { showRestorePicker = true })
        status?.let { Text(it, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(16.dp)) }
        SettingsHeader(stringResource(R.string.set_063))
        ListPaneItem(isSelected = false, title = stringResource(R.string.set_064), description = stringResource(R.string.set_065), icon = Icons.Default.Upload,
            onClick = {
                status = context.getString(R.string.set_066)
                scope.launch {
                    val result = repo.cloudBackup(context, webdavUrl, webdavUser, webdavPass)
                    status = if (result) context.getString(R.string.set_067) else context.getString(R.string.set_068)
                }
            })
        ListPaneItem(isSelected = false, title = stringResource(R.string.set_069), description = stringResource(R.string.set_070), icon = Icons.Default.Download,
            onClick = {
                status = context.getString(R.string.set_071)
                scope.launch {
                    val result = repo.cloudRestore(context, webdavUrl, webdavUser, webdavPass)
                    status = if (result) context.getString(R.string.set_072) else context.getString(R.string.web_012)
                }
            })
        OutlinedTextField(
            value = webdavUrl, onValueChange = { webdavUrl = it; prefs.edit().putString("webdav_url", webdavUrl).apply() },
            label = { Text("WebDAV URL") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
        OutlinedTextField(
            value = webdavUser, onValueChange = { webdavUser = it; prefs.edit().putString("webdav_user", webdavUser).apply() },
            label = { Text(stringResource(R.string.set_073)) }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
        OutlinedTextField(
            value = webdavPass, onValueChange = { webdavPass = it; prefs.edit().putString("webdav_pass", webdavPass).apply() },
            label = { Text(stringResource(R.string.pw_019)) }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
    }
    if (showRestorePicker) {
        FileBrowserPicker(dir = repo.backupDir(), onBack = { showRestorePicker = false }) { file ->
            showRestorePicker = false
            if (file.extension == "json") {
                status = context.getString(R.string.set_074)
                scope.launch {
                    val n = repo.restoreFromFile(file)
                    status = context.getString(R.string.set_res_001, n)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataSettingsScreen(prefs: SharedPreferences, repo: NoteRepository, context: Context, onBackup: () -> Unit, onBack: () -> Unit) {
    var storagePath by remember { mutableStateOf(prefs.getString("storage_path", "") ?: "") }
    var showWarning by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val pickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            val treeId = android.provider.DocumentsContract.getTreeDocumentId(it)
            val realPath = "/storage/emulated/0/" + treeId.substringAfterLast(":")
            prefs.edit().putString("storage_path", realPath).apply()
            storagePath = realPath
        }
    }

    Scaffold(containerColor = Color.Transparent, contentWindowInsets = WindowInsets(0, 0, 0, 0), topBar = {
        TopAppBar(windowInsets = WindowInsets(0, 0, 0, 0), title = { Text(stringResource(R.string.list_037)) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
        }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent))
    }) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            // 权限状态
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
                ListPaneItem(isSelected = false, title = stringResource(R.string.set_044), description = stringResource(R.string.set_045), icon = Icons.Default.Folder)
            } else {
                val hasPer = remember { android.os.Environment.isExternalStorageManager() }
                ListPaneItem(isSelected = hasPer, title = stringResource(R.string.set_046), description = stringResource(R.string.set_047),
                    icon = if (hasPer) Icons.Default.Folder else Icons.Default.Warning,
                    onClick = if (!hasPer) {
                        { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, android.net.Uri.parse("package:${context.packageName}"))) }
                    } else null)
            }
            // 根目录位置
            ListPaneItem(isSelected = false, title = stringResource(R.string.set_048), description = storagePath.ifBlank { "/storage/emulated/0/MeowPad" },
                icon = Icons.Default.Folder, onClick = { pickerLauncher.launch(Uri.parse("content://com.android.externalstorage.documents/tree/primary%3A")) })
            // 附件自动分类
            ListPaneItem(isSelected = false, title = stringResource(R.string.set_049), description = stringResource(R.string.set_050),
                icon = Icons.Default.Category,
                onClick = {
                    statusText = context.getString(R.string.set_051)
                    scope.launch {
                        withContext(Dispatchers.IO) { repo.migrateAttachmentsToRoot() }
                        statusText = context.getString(R.string.set_052)
                    }
                })
            statusText?.let { t -> Text(t, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(16.dp)) }
            // 备份
            ListPaneItem(isSelected = false, title = stringResource(R.string.set_009), description = stringResource(R.string.set_075), icon = Icons.Default.Cloud, onClick = onBackup)
            Spacer(Modifier.height(8.dp))
            // 重置
            ListPaneItem(isSelected = false, title = stringResource(R.string.set_053), description = stringResource(R.string.set_054), icon = Icons.Default.DeleteForever,
                onClick = { showWarning = true })
        }
    }
    if (showWarning) WarningDialog(stringResource(R.string.set_053), stringResource(R.string.set_055),
        { showWarning = false }, {
            showWarning = false
            repo.resetAll()
        })
}

@Composable
fun WarningDialog(title: String, message: String, onCancel: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onCancel, title = { Text(title) }, text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.list_022)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.memo_010)) } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserPicker(dir: File = File("/storage/emulated/0/MeowPad"), onBack: () -> Unit, onPick: (File) -> Unit) {
    var currentDir by remember { mutableStateOf(dir) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var files by remember { mutableStateOf(emptyArray<File>()) }
    var loading by remember { mutableStateOf(true) }

    // 异步加载目录文件列表，避免主线程 IO 卡顿
    LaunchedEffect(currentDir, reload) {
        loading = true
        errorMsg = null
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val f = currentDir
                if (!f.exists() || !f.isDirectory) {
                    emptyArray<File>()
                } else {
                    // 兼容 Android 10+ 限制，优先列出可读子项
                    try {
                        f.listFiles()?.filter { it.canRead() }?.toTypedArray() ?: emptyArray()
                    } catch (e: SecurityException) {
                        emptyArray<File>()
                    }
                }
            }
        }
        result.fold(
            onSuccess = { files = it; loading = false },
            onFailure = { error -> errorMsg = error.message; files = emptyArray(); loading = false }
        )
    }

    Scaffold(containerColor = Color.Transparent, contentWindowInsets = WindowInsets(0, 0, 0, 0), topBar = {
        TopAppBar(windowInsets = WindowInsets(0, 0, 0, 0), title = { Text(stringResource(R.string.set_076)) }, navigationIcon = {
            IconButton(onClick = {
                if (currentDir == dir) {
                    onBack()
                } else {
                    currentDir = currentDir.parentFile ?: dir
                }
            }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
            }
        }, actions = {
            IconButton(onClick = { reload++ }) { Icon(Icons.Default.Refresh, null) }
        }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent))
    }) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Text(stringResource(R.string.set_res_002, currentDir.absolutePath), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(16.dp))
            errorMsg?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }
            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(files.sortedWith(compareBy({ it.isDirectory }, { it.name }))) { f ->
                        Row(modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp).clickable {
                            if (f.isDirectory) {
                                currentDir = f
                                reload++ // 强制重新加载新目录
                            } else {
                                onPick(f)
                            }
                        }, verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(f.name, style = MaterialTheme.typography.bodyLarge)
                            if (f.isDirectory) Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

// ============================ 文件浏览器（浏览笔记数据目录） ============================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowser(dir: File, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var currentDir by remember(dir) { mutableStateOf(dir) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var files by remember { mutableStateOf(emptyArray<File>()) }
    var loading by remember { mutableStateOf(true) }

    // 异步加载目录文件列表，避免主线程 IO 卡顿
    LaunchedEffect(currentDir, reload) {
        loading = true
        errorMsg = null
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val f = currentDir
                if (!f.exists() || !f.isDirectory) {
                    emptyArray<File>()
                } else {
                    try {
                        f.listFiles()?.filter { it.canRead() }?.toTypedArray() ?: emptyArray()
                    } catch (e: SecurityException) {
                        emptyArray<File>()
                    }
                }
            }
        }
        result.fold(
            onSuccess = { files = it; loading = false },
            onFailure = { error -> errorMsg = error.message; files = emptyArray(); loading = false }
        )
    }

    Scaffold(containerColor = Color.Transparent, contentWindowInsets = WindowInsets(0, 0, 0, 0), topBar = {
        TopAppBar(windowInsets = WindowInsets(0, 0, 0, 0), title = { Text(stringResource(R.string.list_037)) }, navigationIcon = {
            IconButton(onClick = {
                if (currentDir == dir) {
                    onBack()
                } else {
                    currentDir = currentDir.parentFile ?: dir
                }
            }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
        }, actions = {
            IconButton(onClick = { reload++ }) { Icon(Icons.Default.Refresh, null) }
        }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent))
    }) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Text(stringResource(R.string.set_res_002, currentDir.absolutePath), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(16.dp))
            errorMsg?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }
            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(files.sortedWith(compareBy({ it.isDirectory }, { it.name }))) { f ->
                        Row(modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp).clickable {
                            if (f.isDirectory) {
                                currentDir = f
                                reload++ // 强制重新加载新目录
                            } else {
                                openFile(context, f.absolutePath)
                            }
                        }, verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    if (f.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                                    null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(8.dp))
                                Text(f.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                            }
                            if (f.isDirectory) Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

// ============================ 备份页（本地 + 云端） ============================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupSettingsScreen(prefs: SharedPreferences, repo: NoteRepository, context: Context, onBack: () -> Unit) {
    var webdavUrl by remember { mutableStateOf(prefs.getString("webdav_url", "") ?: "") }
    var webdavUser by remember { mutableStateOf(prefs.getString("webdav_user", "") ?: "") }
    var webdavPass by remember { mutableStateOf(prefs.getString("webdav_pass", "") ?: "") }
    var status by remember { mutableStateOf<String?>(null) }
    var showRestorePicker by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(containerColor = Color.Transparent, contentWindowInsets = WindowInsets(0, 0, 0, 0), topBar = {
        TopAppBar(windowInsets = WindowInsets(0, 0, 0, 0), title = { Text(stringResource(R.string.set_009)) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
        }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent))
    }) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            SettingsHeader(stringResource(R.string.set_056))
            ListPaneItem(isSelected = false, title = stringResource(R.string.set_057), description = stringResource(R.string.set_058), icon = Icons.Default.Download,
                onClick = {
                    status = context.getString(R.string.set_059)
                    scope.launch {
                        withContext(Dispatchers.IO) { repo.backup(context) }
                        status = context.getString(R.string.set_060)
                    }
                })
            ListPaneItem(isSelected = false, title = stringResource(R.string.set_061), description = stringResource(R.string.set_062), icon = Icons.Default.Upload,
                onClick = { showRestorePicker = true })
            status?.let { Text(it, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(16.dp)) }
            SettingsHeader(stringResource(R.string.set_063))
            ListPaneItem(isSelected = false, title = stringResource(R.string.set_077), description = stringResource(R.string.set_078), icon = Icons.Default.Cloud)
            ListPaneItem(isSelected = false, title = stringResource(R.string.set_064), description = stringResource(R.string.set_065), icon = Icons.Default.Upload,
                onClick = {
                    status = context.getString(R.string.set_066)
                    scope.launch {
                        val result = repo.cloudBackup(context, webdavUrl, webdavUser, webdavPass)
                        status = if (result) context.getString(R.string.set_067) else context.getString(R.string.set_068)
                    }
                })
            ListPaneItem(isSelected = false, title = stringResource(R.string.set_069), description = stringResource(R.string.set_070), icon = Icons.Default.Download,
                onClick = {
                    status = context.getString(R.string.set_071)
                    scope.launch {
                        val result = repo.cloudRestore(context, webdavUrl, webdavUser, webdavPass)
                        status = if (result) context.getString(R.string.set_072) else context.getString(R.string.web_012)
                    }
                })
            // WebDAV 配置输入
            OutlinedTextField(
                value = webdavUrl, onValueChange = { webdavUrl = it; prefs.edit().putString("webdav_url", webdavUrl).apply() },
                label = { Text("WebDAV URL") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
            OutlinedTextField(
                value = webdavUser, onValueChange = { webdavUser = it; prefs.edit().putString("webdav_user", webdavUser).apply() },
                label = { Text(stringResource(R.string.set_073)) }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
            OutlinedTextField(
                value = webdavPass, onValueChange = { webdavPass = it; prefs.edit().putString("webdav_pass", webdavPass).apply() },
                label = { Text(stringResource(R.string.pw_019)) }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
        }
    }
    if (showRestorePicker) {
        FileBrowserPicker(dir = repo.backupDir(), onBack = { showRestorePicker = false }) { file ->
            showRestorePicker = false
            if (file.extension == "json") {
                status = context.getString(R.string.set_074)
                scope.launch {
                    val n = repo.restoreFromFile(file)
                    status = context.getString(R.string.set_res_001, n)
                }
            }
        }
    }
}


/** 关于面板：应用信息 / 运行环境 / 技术栈 / 数据存储 / 开源协议 / 致谢 */
@Composable
private fun AboutPaneContent(context: Context, repo: NoteRepository) {
    val pkgInfo = remember {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
    }
    val versionName = pkgInfo?.versionName ?: APP_VERSION_NAME
    val appInfo = remember { context.applicationInfo }
    val isDebug = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    val rootPath = remember { repo.rootDir().absolutePath }
    // APK 文件的修改时间约等于构建时间
    val buildTime = remember {
        runCatching {
            val f = java.io.File(appInfo.sourceDir)
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(f.lastModified()))
        }.getOrDefault("-")
    }
    val abis = remember { Build.SUPPORTED_ABIS.joinToString(", ") }

    fun copyText(text: String) {
        runCatching {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.setPrimaryClip(ClipData.newPlainText("MeowPad", text))
            android.widget.Toast.makeText(
                context, context.getString(R.string.set_094), android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun openUrl(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    // ---------- 应用信息 ----------
    SettingsHeader(stringResource(R.string.set_079))
    Column(Modifier.padding(horizontal = 16.dp)) {
        DetailPaneItem(
            title = stringResource(R.string.set_080),
            description = "MeowPad  v$versionName",
            icon = Icons.Default.Info,
            onClick = { copyText(versionName) }
        )
        DetailPaneItem(
            title = stringResource(R.string.set_081),
            description = context.packageName,
            icon = Icons.Default.Android,
            onClick = { copyText(context.packageName) }
        )
        DetailPaneItem(
            title = stringResource(R.string.set_082),
            description = if (isDebug) {
                stringResource(R.string.set_096)
            } else {
                stringResource(R.string.set_097)
            },
            icon = Icons.Default.Build
        )
        DetailPaneItem(
            title = stringResource(R.string.set_095),
            description = buildTime,
            icon = Icons.Default.Schedule,
            onClick = { copyText(buildTime) }
        )
    }

    // ---------- 运行环境 ----------
    SettingsHeader(stringResource(R.string.set_083))
    Column(Modifier.padding(horizontal = 16.dp)) {
        DetailPaneItem(
            title = stringResource(R.string.set_084),
            description = "Android ${Build.VERSION.RELEASE}  (API ${Build.VERSION.SDK_INT})",
            icon = Icons.Default.PhoneAndroid
        )
        DetailPaneItem(
            title = stringResource(R.string.set_085),
            description = "${Build.MANUFACTURER} ${Build.MODEL}",
            icon = Icons.Default.Smartphone,
            onClick = { copyText("${Build.MANUFACTURER} ${Build.MODEL}") }
        )
        DetailPaneItem(
            title = stringResource(R.string.set_086),
            description = abis,
            icon = Icons.Default.Memory,
            onClick = { copyText(abis) }
        )
    }

    // ---------- 技术栈 ----------
    SettingsHeader(stringResource(R.string.set_087))
    Column(Modifier.padding(horizontal = 16.dp)) {
        DetailPaneItem(
            title = "Kotlin + Jetpack Compose",
            description = "UI / 协程 / 状态管理",
            icon = Icons.Default.Code
        )
        DetailPaneItem(
            title = "Media3 ExoPlayer",
            description = "本地音视频解码与播放",
            icon = Icons.Default.PlayCircleOutline
        )
        DetailPaneItem(
            title = "kmark",
            description = "Markdown 解析（JetBrains IntelliJ Platform，AGPL-3.0）",
            icon = Icons.Default.TextSnippet
        )
        DetailPaneItem(
            title = "OkHttp + Coil",
            description = "网络请求 / 图片加载",
            icon = Icons.Default.CloudQueue
        )
    }

    // ---------- 数据存储 ----------
    SettingsHeader(stringResource(R.string.set_088))
    Column(Modifier.padding(horizontal = 16.dp)) {
        DetailPaneItem(
            title = stringResource(R.string.set_089),
            description = rootPath,
            icon = Icons.Default.Folder,
            onClick = { copyText(rootPath) }
        )
        Text(
            text = stringResource(R.string.set_090),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 8.dp)
        )
        DetailPaneItem(
            title = stringResource(R.string.set_099),
            description = null,
            icon = Icons.Default.Lock
        )
    }

    // ---------- 开源协议 ----------
    SettingsHeader(stringResource(R.string.set_091))
    Column(Modifier.padding(horizontal = 16.dp)) {
        DetailPaneItem(
            title = "GNU AGPL-3.0",
            description = stringResource(R.string.set_100),
            icon = Icons.Default.Gavel,
            onClick = { openUrl("https://github.com/MioHueCode/MeowPad/blob/master/LICENSE") }
        )
        DetailPaneItem(
            title = stringResource(R.string.set_092),
            description = "github.com/MioHueCode/MeowPad",
            icon = Icons.Default.Link,
            onClick = { openUrl("https://github.com/MioHueCode/MeowPad") }
        )
    }

    // ---------- 致谢 ----------
    SettingsHeader(stringResource(R.string.set_093))
    Column(Modifier.padding(horizontal = 16.dp)) {
        DetailPaneItem(
            title = "Kori",
            description = "编辑器交互与视觉设计参考",
            icon = Icons.Default.FavoriteBorder,
            onClick = { openUrl("https://github.com/YangDai2003/Kori") }
        )
        DetailPaneItem(
            title = "JetBrains IntelliJ Platform",
            description = "kmark Markdown 解析器",
            icon = Icons.Default.MenuBook,
            onClick = { openUrl("https://github.com/JetBrains/intellij-community") }
        )
    }
    Spacer(Modifier.height(24.dp))
}
