package com.miohue.notepad.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandIn
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.miohue.notepad.R
import com.miohue.notepad.data.AiConfig
import com.miohue.notepad.data.LlmClient

/**
 * AI 助手悬浮面板（完全对齐 Kori AIOverlay）。
 *
 * 用 `BoxWithConstraints(fillMaxSize, contentAlignment = BottomStart)` 覆盖整个编辑器，
 * 内容**悬浮在底部**、不把正文顶上去：
 * - 底部一条横向可滚动的小 chip（摘要/润色/缩短/扩写/改写），点 chip 直接对当前正文执行。
 * - 点「生成」圆钮展开自定义指令输入框，背景加 scrim 遮罩、点外部关闭。
 * - 结果在底部浮层里展示，带「应用」按钮回填正文。
 *
 * 离线工具（统计/待办/排版/大小写/序号）已挪到顶栏「工具箱」菜单（见 OfflineToolsMenu）。
 */
@Composable
fun AiAssistantPanel(
    text: String,
    // 对齐 Kori AIAssist：true=当前无选中文本（selection collapsed），false=选中了文本
    isTextSelectionCollapsed: Boolean,
    onApply: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var result by remember { mutableStateOf<String?>(null) }
    var resultTitle by remember { mutableStateOf("") }
    // 对齐 Kori：prompt 为 TextFieldState（跨配置变更保留），notepad 用 rememberSaveable 等效
    var customPrompt by rememberSaveable { mutableStateOf("") }
    var inputMode by rememberSaveable { mutableStateOf(false) }
    // 对齐 Kori AIAssist：showChips 控制润色 chips 行显隐（点圆圈在选中文本时切换）
    var showChips by rememberSaveable { mutableStateOf(true) }
    // generating 与协程生命周期绑定，不跨配置保存（避免旋转后卡在生成态）
    var generating by remember { mutableStateOf(false) }

    val aiEnabled = remember { AiConfig.isEnabled(context) }
    val aiConfigured = remember { AiConfig.isConfigured(context) }
    // 对齐 Kori AIOverlay：品牌三色渐变描边（primary→tertiary→secondary），提升面板辨识度
    val scheme = MaterialTheme.colorScheme
    val brandBorderBrush = Brush.verticalGradient(
        colors = listOf(scheme.primary, scheme.tertiary, scheme.secondary)
    )

    fun runAi(title: String, systemPrompt: String, userContent: String) {
        if (userContent.isBlank()) {
            resultTitle = title
            result = context.getString(R.string.ai_058)
            return
        }
        resultTitle = title
        result = null
        generating = true
        scope.launch {
            val r = LlmClient.chat(context, systemPrompt, userContent)
            result = when (r) {
                is LlmClient.Result.Ok -> r.text
                is LlmClient.Result.Err -> context.getString(R.string.ai_027, r.message)
            }
            generating = false
        }
    }

    val spSummarize = context.getString(R.string.sp_001)
    val spPolish = context.getString(R.string.sp_002)
    val spShorten = context.getString(R.string.sp_005)
    val spElaborate = context.getString(R.string.sp_006)
    val spRewrite = context.getString(R.string.sp_007)

    val strGenerate = context.getString(R.string.ai_056)
    val strEmpty = context.getString(R.string.ai_058)
    val strSend = context.getString(R.string.ai_042)
    val strSummarize = context.getString(R.string.ai_055)
    val strPolish = context.getString(R.string.ai_051)
    val strShorten = context.getString(R.string.ai_052)
    val strElaborate = context.getString(R.string.ai_053)
    val strRewrite = context.getString(R.string.ai_054)

    // 对齐 Kori AIAssistEvent.Generate：自定义指令发送（发送按钮与 Ctrl+Enter 共用）
    fun sendCustomPrompt() {
        if (customPrompt.isNotBlank() && !generating) {
            val p = customPrompt
            customPrompt = ""
            inputMode = false
            // 正文空时用指令本身作为输入内容
            runAi(strGenerate, p, if (text.isBlank()) p else text)
        }
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomStart
    ) {
// 输入模式遮罩：点击外部关闭（对齐 Kori AIAssist inputMode scrim + BackHandler）
        // 对齐 Kori：scrim 颜色随 inputMode 渐变（scrim 0.32f ↔ Transparent）
        val backgroundColor by animateColorAsState(
            if (inputMode) MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)
            else Color.Transparent
        )
        if (inputMode) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(generating) {
                        detectTapGestures {
                            if (generating) return@detectTapGestures
                            inputMode = false
                        }
                    }
            ) {
                drawRect(backgroundColor)
            }
            BackHandler { inputMode = false }
        }

        // 未配置提示（notepad 保留：未启用/未配置 AI 时显示提示条并退出）
        if (!aiEnabled || !aiConfigured) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.ai_025),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                    }
                }
            }
            return@BoxWithConstraints
        }

        // 修复圆钮悬空：面板挂在 Scaffold content 内（padding 已含工具栏+导航栏高度），
        // 再叠 navigationBars padding 会双重抬高约一个导航栏高度——去掉，只保留刘海与 ime
        val aiModifier = Modifier
            .displayCutoutPadding()
            .windowInsetsPadding(WindowInsets.ime.only(WindowInsetsSides.Bottom))

        // 结果区（notepad 额外功能：AI 生成结果悬浮展示，bottom 48 让出操作栏）
        AnimatedVisibility(visible = result != null, enter = fadeIn(), exit = fadeOut()) {
            Surface(
                modifier = aiModifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .padding(bottom = 48.dp),
                shape = RoundedCornerShape(14.dp),
                tonalElevation = 2.dp,
                border = BorderStroke(width = 1.dp, brush = brandBorderBrush)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            resultTitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { result = null }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        result ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 120.dp)
                            .verticalScroll(rememberScrollState())
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { onApply(result!!) },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Done, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.ai_019))
                        }
                        if (generating) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.ai_022), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        // chips 行（对齐 Kori：BoxWithConstraints 直接子项，bottom=48.dp 悬浮，horizontalScroll）
        // Kori AIAssist：if (!isTextSelectionCollapsed && showChips) 显示润色 chips
        // 顺序对齐 Kori：proofread/shorten/elaborate/rewrite/summarize，spacedBy(4.dp) + 首尾 Spacer(6.dp)
        if (!isTextSelectionCollapsed && showChips) {
            Row(
                modifier = aiModifier
                    .padding(bottom = 48.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.width(6.dp))
                AiChip(strPolish) { runAi(strPolish, spPolish, text) }
                AiChip(strShorten) { runAi(strShorten, spShorten, text) }
                AiChip(strElaborate) { runAi(strElaborate, spElaborate, text) }
                AiChip(strRewrite) { runAi(strRewrite, spRewrite, text) }
                AiChip(strSummarize) { runAi(strSummarize, spSummarize, text) }
                Spacer(Modifier.width(6.dp))
            }
        }

        // 输入框 Box + 生成按钮（对齐 Kori：同一个 Box，contentAlignment=BottomStart，Box 始终显示）
        // Kori：Box(padding(vertical=16/4, horizontal=16/8).dropShadow.border.wrapContentSize.background.innerShadow, BottomStart)
        // notepad 用标准 API 等效：外层 Box 渐变 background + 1dp padding 渐变描边 + 内层 surfaceContainerLowest
        // 输入框宽度：对齐 Kori widthModifier（>=1200dp(XL)→0.25f、>=840dp(L)→0.33f、>=600dp(EXPANDED)→0.5f，否则 fillMaxWidth）
        val inputWidthModifier =
            if (maxWidth >= 1200.dp) Modifier.fillMaxWidth(0.25f)
            else if (maxWidth >= 840.dp) Modifier.fillMaxWidth(0.33f)
            else if (maxWidth >= 600.dp) Modifier.fillMaxWidth(0.5f)
            else Modifier.fillMaxWidth()
        // 对齐 Kori：容器 padding 随 inputMode 动画（16/16 ↔ 4/8）
        val verticalPadding by animateDpAsState(if (inputMode) 16.dp else 4.dp)
        val horizontalPadding by animateDpAsState(if (inputMode) 16.dp else 8.dp)
        Box(
            modifier = aiModifier
                .padding(vertical = verticalPadding, horizontal = horizontalPadding)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    brush = Brush.verticalGradient(
                        listOf(scheme.primary, scheme.tertiary, scheme.secondary)
                    ),
                    shape = RoundedCornerShape(28.dp)
                )
                .padding(1.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    shape = RoundedCornerShape(28.dp)
                ),
            contentAlignment = Alignment.BottomStart
        ) {
            // 输入框：仅 inputMode 时显示（对齐 Kori AnimatedVisibility fadeIn+expandIn(BottomStart)/fadeOut+shrinkOut）
            AnimatedVisibility(
                visible = inputMode,
                enter = fadeIn() + expandIn(expandFrom = Alignment.BottomStart),
                exit = fadeOut() + shrinkOut(shrinkTowards = Alignment.BottomStart)
            ) {
                // 对齐 Kori：FocusRequester + 延时 300ms 自动聚焦；Ctrl+Enter 发送
                val focusRequester = remember { FocusRequester() }
                OutlinedTextField(
                    value = customPrompt,
                    onValueChange = { customPrompt = it },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    placeholder = { Text(stringResource(R.string.ai_057), style = MaterialTheme.typography.bodyMedium) },
                    singleLine = false,
                    maxLines = 3,
                    readOnly = generating,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    shape = RoundedCornerShape(28.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        disabledBorderColor = Color.Transparent,
                    ),
                    modifier = inputWidthModifier
                        .focusRequester(focusRequester)
                        .onPreviewKeyEvent {
                            if (it.isCtrlPressed && it.key == Key.Enter && it.type == KeyEventType.KeyUp) {
                                if (inputMode && !generating) {
                                    sendCustomPrompt()
                                    true
                                } else {
                                    false
                                }
                            } else false
                        },
                    // 对齐 Kori：trailingIcon 用 Spacer(48.dp) 留空，发送由右下角独立生成按钮承担
                    trailingIcon = { Spacer(Modifier.size(48.dp)) }
                )
                LaunchedEffect(Unit) {
                    delay(300L)
                    focusRequester.requestFocus()
                }
            }
            // 生成按钮：align(BottomEnd)，padding = if(inputMode) 8.dp else 0.dp（对齐 Kori Crossfade）
            // Box 直接子项（始终显示，不随输入框显隐）
            val buttonPadding by animateDpAsState(if (inputMode) 8.dp else 0.dp)
            Crossfade(
                generating,
                modifier = Modifier
                    .padding(bottom = buttonPadding, end = buttonPadding)
                    .align(Alignment.BottomEnd)
            ) {
                if (it) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(4.dp).size(40.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    FilledIconButton(
                        // 对齐 Kori AIAssist FilledIconButton onClick：
                        // 未选中文本→切换输入框/发送；选中文本→切换润色 chips 显隐
                        onClick = {
                            if (isTextSelectionCollapsed) {
                                if (inputMode) {
                                    sendCustomPrompt()
                                } else {
                                    inputMode = true
                                }
                            } else {
                                showChips = !showChips
                            }
                        },
                        modifier = Modifier.padding(4.dp).size(40.dp)
                    ) {
                        Icon(Icons.Default.GeneratingTokens, null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        // 对齐 Kori：生成中全屏 scrim 遮罩（选中文本时不遮挡，避免盖住 chips）
        AnimatedVisibility(
            visible = generating && !isTextSelectionCollapsed,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            LoadingScrim()
        }
    }
}

/**
 * 对齐 Kori LoadingScrim：生成中全屏遮罩 + 居中加载指示。
 * notepad 无 material3 expressive LoadingIndicator，用 CircularProgressIndicator(48.dp) 近似。
 */
@Composable
private fun LoadingScrim(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
            .displayCutoutPadding(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(48.dp), strokeWidth = 3.dp)
    }
}

@Composable
private fun AiChip(label: String, onClick: () -> Unit) {
    // 对齐 Kori AIAssistChip：三色渐变描边 + 按压弹簧缩放 + surfaceBright 半透明容器
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) .85f else 1f,
        animationSpec = if (isPressed)
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
        else
            spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioHighBouncy),
        visibilityThreshold = .000001f
    )
    val scheme = MaterialTheme.colorScheme
    val borderBrush = Brush.verticalGradient(
        colors = listOf(
            scheme.primary,
            scheme.tertiary,
            scheme.secondary
        )
    )
    AssistChip(
        modifier = Modifier.graphicsLayer {
            scaleY = scale
            scaleX = (1f - scale) + 1f
        },
        interactionSource = interactionSource,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        border = BorderStroke(width = 1.dp, brush = borderBrush),
        colors = AssistChipDefaults.assistChipColors(
            containerColor = scheme.surfaceBright.copy(alpha = 0.8f)
        )
    )
}

/**
 * 离线文本工具菜单（顶栏「工具箱」图标弹出）。
 *
 * 原先是 AI 面板里的「工具」区，用户觉得跟 AI 混在一起不好操作，挪到顶栏独立菜单。
 * 点选后通过 onResult 把结果回传，由调用方弹到底部结果条。
 */
data class OfflineTool(val title: String, val icon: ImageVector, val subtitle: String, val operation: () -> String)

@Composable
fun OfflineToolsMenu(
    expanded: Boolean,
    text: String,
    onDismiss: () -> Unit,
    onSelect: (title: String, result: String) -> Unit
) {
    val context = LocalContext.current
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        val tools = listOf(
            OfflineTool(stringResource(R.string.ai_003), Icons.Default.Summarize, stringResource(R.string.ai_004)) { summarize(context, text) },
            OfflineTool(stringResource(R.string.ai_007), Icons.Default.Checklist, stringResource(R.string.ai_008)) { extractTodos(context, text) },
            OfflineTool(stringResource(R.string.ai_009), Icons.Default.AutoFixHigh, stringResource(R.string.ai_010)) { formatText(text) },
            OfflineTool(stringResource(R.string.ai_011), Icons.Default.Title, stringResource(R.string.ai_012)) { capitalizeSentences(text) },
            OfflineTool(stringResource(R.string.ai_013), Icons.Default.TextIncrease, stringResource(R.string.ai_014)) { text.uppercase() },
            OfflineTool(stringResource(R.string.ai_015), Icons.Default.TextDecrease, stringResource(R.string.ai_016)) { text.lowercase() },
            OfflineTool(stringResource(R.string.ai_017), Icons.Default.FormatListNumbered, stringResource(R.string.ai_018)) { addLineNumbers(text) }
        )
        tools.forEach { tool ->
            DropdownMenuItem(
                text = { Text(tool.title) },
                leadingIcon = { Icon(tool.icon, null, modifier = Modifier.size(20.dp)) },
                onClick = { onSelect(tool.title, tool.operation()); onDismiss() }
            )
        }
    }
}

// ==================== 离线文本算法 ====================

/** 摘要：取前 N 个非空句子 / 前若干字符 */
private fun summarize(context: android.content.Context, text: String): String {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return context.getString(R.string.ai_020)
    val sentences = trimmed.split(Regex("(?<=[。！？!?.\\n])")).map { it.trim() }.filter { it.isNotEmpty() }
    if (sentences.isEmpty()) return trimmed.take(120)
    val take = if (sentences.size >= 3) 3 else sentences.size
    return sentences.take(take).joinToString("\n") + (if (sentences.size > take) context.getString(R.string.ai_res_002, sentences.size) else "")
}

/** 统计：字数 / 字符 / 行数 */
private fun stats(context: android.content.Context, text: String): String {
    val noSpaces = text.replace(Regex("\\s"), "")
    return context.getString(R.string.ai_res_003, text.length, noSpaces.length, text.split("\n").size)
}

/** 提取链接：本地正则抓出所有 http/https/www URL，每行一条 */
private fun extractLinks(text: String): String {
    val urls = Regex("""https?://[^\s<>"\']+|www\.[^\s<>"\']+""")
        .findAll(text)
        .map { it.value.trimEnd(',', '。', '；', '；', '，') }
        .distinct()
        .toList()
    return if (urls.isEmpty()) "（无链接）" else urls.joinToString("\n")
}

/** 待办抽取 */
private fun extractTodos(context: android.content.Context, text: String): String {
    val todos = text.split("\n").map { it.trim() }.filter { it.startsWith("- [") || it.startsWith("* [") }
    return if (todos.isEmpty()) context.getString(R.string.ai_021) else todos.joinToString("\n")
}

/** 智能排版：合并连续空行为单空行、清理行尾空格 */
private fun formatText(text: String): String {
    val result = mutableListOf<String>()
    var prevBlank = false
    for (line in text.split("\n").map { it.trimEnd() }) {
        val blank = line.isBlank()
        if (blank && prevBlank) continue
        result.add(line)
        prevBlank = blank
    }
    return result.joinToString("\n").trim()
}

/** 每句首字母大写 */
private fun capitalizeSentences(text: String): String {
    return text.replace(Regex("(^|[。！？!?.\\n]\\s*)([a-z])")) { m ->
        m.groupValues[1] + m.groupValues[2].uppercase()
    }
}

/** 行首加序号 */
private fun addLineNumbers(text: String): String {
    return text.split("\n").mapIndexed { i, line -> "${i + 1}. $line" }.joinToString("\n")
}