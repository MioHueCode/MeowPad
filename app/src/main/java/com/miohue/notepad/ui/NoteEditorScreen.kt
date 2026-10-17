package com.miohue.notepad.ui

import android.content.Intent
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.FilterAltOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SwapHorizontalCircle
import androidx.compose.material.icons.outlined.LocationSearching
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.runtime.key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.miohue.notepad.R
import com.miohue.notepad.ReminderReceiver
import com.miohue.notepad.data.*
import java.io.File
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    repo: NoteRepository,
    id: String,
    onBack: () -> Unit,
    onSavedToDetail: (String) -> Unit,
    onSavedPop: () -> Unit,
    onOpenUrl: (String) -> Unit = {}
) {
    // id 形如 "new"（旧入口）或 "new:text|todo|code"（FAB 类型选择新建）
    val isNew = id.startsWith("new")
    val newType = if (isNew) id.substringAfter("new:", "text") else "text"
    val original = remember(isNew, id) { if (isNew) Note.new() else repo.get(id) }
    val folders = remember { repo.folders() }
    val scheme = MaterialTheme.colorScheme
    val context = androidx.compose.ui.platform.LocalContext.current

    if (!isNew && original == null) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.edit_001)) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.web_002)) }
                    }
                )
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.edit_002)) }
        }
        return
    }

    var title by remember(isNew, id) { mutableStateOf(original?.title ?: "") }
    // 正文用 TextFieldValue：编程模式需要光标位置做跟随滚动与快捷插入
    var contentValue by remember(isNew, id) { mutableStateOf(TextFieldValue(original?.content ?: "")) }
    val content = contentValue.text
    // undo/redo 历史栈（对齐 Kori KTextEditor 的 undo/redo：内容快照 + 栈指针）
    val history = remember(isNew, id) { mutableListOf<String>() }
    val redoStack = remember(isNew, id) { mutableListOf<String>() }
    var canUndo by remember { mutableStateOf(false) }
    var canRedo by remember { mutableStateOf(false) }
    // 正文输入去抖入栈：监听 content 变化，停顿 600ms 后快照入栈（避免逐键爆炸）
    var lastSnapshot by remember { mutableStateOf(original?.content ?: "") }
    var typing by remember { mutableStateOf(false) }
    LaunchedEffect(content) {
        if (content != lastSnapshot) {
            typing = true
            kotlinx.coroutines.delay(600)
            if (content != lastSnapshot) {
                history.add(lastSnapshot)
                if (history.size > 100) history.removeAt(0)
                redoStack.clear()
                lastSnapshot = content
                canUndo = history.isNotEmpty()
                canRedo = false
            }
        }
    }
    fun pushHistorySnapshot() {
        // 程序化修改（搜索替换/快捷插入/附件插入）前压栈
        if (content != lastSnapshot) {
            history.add(lastSnapshot)
            if (history.size > 100) history.removeAt(0)
            redoStack.clear()
            lastSnapshot = content
            canUndo = history.isNotEmpty()
            canRedo = false
        }
    }
    fun performUndo() {
        if (history.isEmpty()) return
        val old = contentValue.text
        val prev = history.removeAt(history.size - 1)
        redoStack.add(old)
        contentValue = TextFieldValue(prev, TextRange(prev.length))
        lastSnapshot = prev
        canUndo = history.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }
    fun performRedo() {
        if (redoStack.isEmpty()) return
        val old = contentValue.text
        val next = redoStack.removeAt(redoStack.size - 1)
        history.add(old)
        contentValue = TextFieldValue(next, TextRange(next.length))
        lastSnapshot = next
        canUndo = history.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }
    // 设置项读取（默认视图/行号开关）
    val prefs = remember { context.getSharedPreferences("notepad_prefs", android.content.Context.MODE_PRIVATE) }
    // 编程模式（对齐 Kori 编辑器：等宽字体 + 行号槽 + 竖分隔线 + 快捷插入），含代码块的内容自动进入
    // 修复真机反馈「代码语法高亮不生效」：type=code 的笔记重开时恢复代码模式（此前 type 只存 text/todo，重开丢失高亮）
    var codeMode by remember(isNew, id) {
        mutableStateOf(
            original?.type == "code" ||
                (!isNew && original?.content?.contains("```") == true) ||
                newType == "code"
        )
    }
    // 行号显示开关（设置「编辑器」里的 show_line_numbers，仅编程模式生效）
    var showLineNumbers by remember { mutableStateOf(prefs.getBoolean("show_line_numbers", true)) }
    val bodyScroll = rememberScrollState()
    var bodyLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var bodyViewport by remember { mutableStateOf(0) }
    // 阅读模式（对齐 Kori isReadView：右上角与编程模式并排切换，阅读时隐藏底部操作栏）
    // 初始化读设置里的「默认视图」（default_reading_view）
    var readingMode by remember(isNew, id) {
        mutableStateOf(!isNew && prefs.getBoolean("default_reading_view", false))
    }
    val focusManager = LocalFocusManager.current
    // 搜索替换（对齐 Kori rememberFindAndReplaceState + FindAndReplaceField：顶栏 Search 钮 + 标题区 AnimatedContent 切换）
    var isSearching by remember { mutableStateOf(false) }
    var searchWord by remember { mutableStateOf("") }
    var replaceWord by remember { mutableStateOf("") }
    var matchIndex by remember { mutableStateOf(0) }
    val findFocusRequester = remember { FocusRequester() }
    // 匹配位置实时派生（searchWord/content 变化即重算；文本量级下 O(n·m) 可接受）
    val matches by remember { derivedStateOf {
        val list = mutableListOf<Int>()
        if (searchWord.isNotEmpty()) {
            var i = 0
            while (true) {
                i = contentValue.text.indexOf(searchWord, i)
                if (i < 0) break
                list.add(i); i += searchWord.length
            }
        }
        list
    } }
    // 跳转上一处/下一处：循环 + selection 选中匹配文本（等效 Kori scrollDirection NEXT/PREVIOUS）
    fun navigateMatch(step: Int) {
        if (matches.isEmpty()) return
        matchIndex = ((matchIndex + step) % matches.size + matches.size) % matches.size
        val pos = matches[matchIndex]
        contentValue = contentValue.copy(selection = TextRange(pos, pos + searchWord.length))
    }
    // 替换当前匹配后自动跳下一处（等效 Kori ReplaceType.CURRENT）
    fun replaceCurrent() {
        if (matches.isEmpty()) return
        val idx = matchIndex.coerceIn(0, matches.size - 1)
        val pos = matches[idx]
        val text = contentValue.text
        val newText = text.replaceRange(pos, pos + searchWord.length, replaceWord)
        contentValue = TextFieldValue(newText, TextRange(pos + replaceWord.length))
        matchIndex = idx.coerceAtMost((matches.size - 2).coerceAtLeast(0))
        navigateMatch(+1)
    }
    // 全部替换（等效 Kori ReplaceType.ALL）
    fun replaceAllMatches() {
        if (searchWord.isEmpty()) return
        val newText = contentValue.text.replace(searchWord, replaceWord)
        contentValue = TextFieldValue(newText, TextRange(newText.length))
        matchIndex = 0
    }
    // Todo.txt 模式（对齐 Kori NoteType.TODO：清单视图 + 源码编辑切换 + 语法着色）
    var todoMode by remember(isNew, id) { mutableStateOf(original?.type == "todo" || newType == "todo") }
    var tags by remember(isNew, id) { mutableStateOf(original?.tags?.joinToString(",") ?: "") }
    var color by remember(isNew, id) { mutableStateOf(original?.color ?: 0xFF1976D2.toInt()) }
    var folderId by remember(isNew, id) { mutableStateOf(original?.folderId ?: "") }
    var favorite by remember(isNew, id) { mutableStateOf(original?.favorite ?: false) }
    var pinned by remember(isNew, id) { mutableStateOf(original?.pinned ?: false) }
    var images by remember(isNew, id) { mutableStateOf(original?.images ?: emptyList()) }
    var videos by remember(isNew, id) { mutableStateOf(original?.videos ?: emptyList()) }
    var files by remember(isNew, id) { mutableStateOf(original?.files ?: emptyList()) }
    var audios by remember(isNew, id) { mutableStateOf(original?.audios ?: emptyList()) }
    // 文件夹选择对话框（对齐 Kori NoteScreen 顶栏 FilledTonalButton → FoldersDialog）
    var showFolderDialog by remember { mutableStateOf(false) }
    // AI 助手面板：对齐 Kori showAI（联网 && AI启用 && 非绘图 → 面板常驻左下角，无手动唤醒按钮）。
    // notepad 对应：AI 已启用 && 编辑模式 && 非搜索；点面板右上角 X 手动收回，切阅读/搜索模式自动重新弹出
    val aiEnabledPref = remember { AiConfig.isEnabled(context) }
    var aiManuallyHidden by remember(readingMode, isSearching) { mutableStateOf(false) }
    val showAiAssistant = aiEnabledPref && !readingMode && !isSearching && !aiManuallyHidden
    // 离线工具箱菜单 + 结果
    var showToolsMenu by remember { mutableStateOf(false) }
    var offlineToolResult by remember { mutableStateOf<String?>(null) }
    var offlineToolTitle by remember { mutableStateOf("") }
    // 右侧概览侧栏（对齐 Kori NoteSideSheet：置顶/收藏/删除/类型切换/分享 + 创建/更新时间/统计/附件/标签）
    var isSideSheetOpen by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var showAudioDialog by remember { mutableStateOf(false) }

    // 分享对话框（对齐 Kori ShareDialog：文本分享 / 文件分享）
    if (showShareDialog) {
        AlertDialog(
            onDismissRequest = { showShareDialog = false },
            title = { Text(stringResource(R.string.edit_046)) },
            text = { Text("分享方式") },
            confirmButton = {
                TextButton(onClick = {
                    runCatching {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, title)
                            putExtra(Intent.EXTRA_TEXT, "$title\n$content")
                        }
                        context.startActivity(Intent.createChooser(send, context.getString(R.string.edit_046)))
                    }.onFailure { android.widget.Toast.makeText(context, context.getString(R.string.edit_047), android.widget.Toast.LENGTH_SHORT).show() }
                    showShareDialog = false
                }) { Text("文本") }
            },
            dismissButton = {
                TextButton(onClick = {
                    runCatching {
                        val fileName = title.replace("[^a-zA-Z0-9._-]".toRegex(), "_").ifBlank { "note" } + ".txt"
                        val file = java.io.File(context.cacheDir, fileName)
                        file.writeText("$title\n$content", Charsets.UTF_8)
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context, "${context.packageName}.fileprovider", file
                        )
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(send, context.getString(R.string.edit_046)))
                    }.onFailure { android.widget.Toast.makeText(context, context.getString(R.string.edit_047), android.widget.Toast.LENGTH_SHORT).show() }
                    showShareDialog = false
                }) { Text("文件") }
            }
        )
    }

    // 录音状态
    var isRecording by remember { mutableStateOf(false) }
    val mediaRecorder = remember { mutableStateOf<android.media.MediaRecorder?>(null) }
    var currentRecording by remember { mutableStateOf<String?>(null) }

    // 附件按「自动分类」落盘：根目录/images|files|audios（不可写时自动回退内部目录）
    fun storageDir(defaultSub: String): File = repo.storageDir(defaultSub)

    fun toast(msg: String) =
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()

    // 在光标处插入片段（编程模式快捷操作）；cursorBack 为光标相对插入末尾的回退量
    fun insertAtCursor(snippet: String, cursorBack: Int = 0) {
        val text = contentValue.text
        val start = contentValue.selection.min.coerceIn(0, text.length)
        val end = contentValue.selection.max.coerceIn(0, text.length)
        val newText = text.substring(0, start) + snippet + text.substring(end)
        contentValue = TextFieldValue(newText, TextRange(start + snippet.length - cursorBack))
    }

    // 对齐 Kori EditorMutators.addInNewLine：光标处插入附件标记，
    // 光标不在行首时先补换行，插入后补换行，保证附件独占一行
    fun insertAttachmentMarker(marker: String) {
        val text = contentValue.text
        val cursor = contentValue.selection.end.coerceIn(0, text.length)
        val lineStart = text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)).let { if (it == -1) 0 else it + 1 }
        val prefix = if (cursor > lineStart) "\n" else ""
        val snippet = prefix + marker + "\n"
        val newText = text.substring(0, cursor) + snippet + text.substring(cursor)
        contentValue = TextFieldValue(newText, TextRange(cursor + snippet.length))
    }

    // 对齐 Kori 附件双向同步：删除附件时同时从正文移除内联标记（删附件也删代码）
    // 匹配 `![name](path)` / `[name](/local/path)` / `<audio src="path"...>` / `<video src="path"...>` 所在整行
    fun removeAttachmentMarker(path: String) {
        val text = contentValue.text
        val escaped = kotlin.text.Regex.escape(path)
        val linePattern = Regex(
            "(?m)^[ \\t]*(?:!\\[[^\\]]*]\\($escaped\\)" +
                "|\\[[^\\]]+]\\($escaped\\)" +
                "|<audio src=\"$escaped\"[^>]*>[^<]*</audio>" +
                "|<video src=\"$escaped\"[^>]*>[^<]*</video>)[ \\t]*\\r?\\n?"
        )
        val newText = linePattern.replace(text, "")
        if (newText != text) {
            contentValue = TextFieldValue(newText, TextRange(newText.length.coerceIn(0, 0)))
        }
    }

    // 光标所在行切换完成标记（Todo 源码编辑快捷键，对齐 Kori toggleLineStart）
    fun toggleTodoDoneAtCursor() {
        val text = contentValue.text
        val cursor = contentValue.selection.end.coerceIn(0, text.length)
        val lineStart = text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)).let { if (it == -1) 0 else it + 1 }
        val lineEnd = text.indexOf('\n', lineStart).let { if (it == -1) text.length else it }
        val line = text.substring(lineStart, lineEnd)
        val newLine = if (line.startsWith("x ") || line.startsWith("X ")) line.substring(2) else "x " + line
        val newText = text.substring(0, lineStart) + newLine + text.substring(lineEnd)
        contentValue = TextFieldValue(newText, TextRange((lineStart + newLine.length).coerceAtMost(newText.length)))
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        uris.forEach { uri ->
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            val ext = mime.substringAfter("/", "jpg").substringBefore("+").ifBlank { "jpg" }
            val name = queryName(context, uri) ?: "image_${System.currentTimeMillis()}.$ext"
            val dest = repo.importUri(uri, name, mime)
            if (dest != null) {
                images = images + dest.absolutePath
                // 对齐 Kori addImageLinks：在光标处插入 markdown 图片标记（文字中夹附件）
                insertAttachmentMarker("![${dest.name}](${dest.absolutePath})")
            } else {
                toast(context.getString(R.string.edit_res_001, repo.rootDir().absolutePath))
            }
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val name = queryName(context, uri) ?: context.getString(R.string.edit_003)
            val dest = repo.importUri(uri, name, mime)
            if (dest != null) {
                files = files + Attachment(dest.absolutePath, name, AttachmentType.FILE)
                // 对齐 Kori：在光标处插入文件链接标记
                insertAttachmentMarker("[${dest.name}](${dest.absolutePath})")
            } else {
                toast(context.getString(R.string.edit_res_002, repo.rootDir().absolutePath))
            }
        }
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val mime = context.contentResolver.getType(uri) ?: "audio/mpeg"
            val ext = mime.substringAfter("/", "mp3").substringBefore("+").ifBlank { "mp3" }
            val dest = repo.importUri(uri, "audio_${System.currentTimeMillis()}.$ext", mime)
            if (dest != null) {
                audios = audios + dest.absolutePath
                // 对齐 Kori addAudioLink：在光标处插入音频标记
                insertAttachmentMarker("<audio src=\"${dest.absolutePath}\" controls>${dest.name}</audio>")
            } else {
                toast(context.getString(R.string.edit_res_003, repo.rootDir().absolutePath))
            }
        }
    }
    // 视频附件（与图片同一逻辑：GetContent 选择 → 自动分类落盘 videos/ → 记录路径）
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val mime = context.contentResolver.getType(uri) ?: "video/mp4"
            val ext = mime.substringAfter("/", "mp4").substringBefore("+").ifBlank { "mp4" }
            val dest = repo.importUri(uri, "video_${System.currentTimeMillis()}.$ext", mime)
            if (dest != null) {
                videos = videos + dest.absolutePath
                // 对齐 Kori addVideoLink：在光标处插入视频标记
                insertAttachmentMarker("<video src=\"${dest.absolutePath}\" controls>${dest.name}</video>")
            } else {
                toast(context.getString(R.string.edit_res_004, repo.rootDir().absolutePath))
            }
        }
    }

    // 麦克风运行时权限：Android 6+ 直接构造 MediaRecorder 会抛异常，需先申请
    val recordAudioPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) toast(context.getString(R.string.edit_092))
    }

    fun doSave() {
        val base = original ?: Note.new()
        val updated = base.copy(
            title = title.trim(),
            content = content,
            tags = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() },
            color = color,
            pinned = pinned,
            folderId = folderId,
            favorite = favorite,
            images = images,
            videos = videos,
            files = files,
            audios = audios,
            // 对齐 Kori NoteType：代码模式持久化为 type=code，重开时恢复语法高亮（修复真机反馈高亮不生效）
            type = if (todoMode) "todo" else if (codeMode) "code" else "text",
            updatedAt = System.currentTimeMillis()
        )
        repo.save(updated)
        if (isNew) onSavedToDetail(updated.id) else onSavedPop()
    }

    // 对齐 Kori 自动保存语义：返回即保存（新建且内容全空则直接放弃不落盘）
    fun saveAndBack() {
        if (isNew && title.isBlank() && content.isBlank() &&
            images.isEmpty() && videos.isEmpty() && files.isEmpty() && audios.isEmpty()
        ) {
            onBack()
        } else {
            doSave()
        }
    }

    // 系统返回键同样走保存语义（后组合的 BackHandler 优先于 NoteApp 全局）；侧栏打开时先关侧栏（对齐 Kori PredictiveBackHandler）
    androidx.activity.compose.BackHandler {
        if (isSideSheetOpen) isSideSheetOpen = false
        else saveAndBack()
    }

    Scaffold(

        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        // 对齐 Kori EditorScaffold：Scaffold(modifier.imePadding())，键盘不打断底部操作排
        modifier = Modifier.imePadding(),
        containerColor = scheme.background,
        // 对齐 Kori AdaptiveActionRowLayout：可滚动时 surfaceContainerLow ↔ surface 动画背景，
        // AnimatedVisibility(!isReadView)，按笔记类型切换 PlainText / Todo / Code 操作排
        bottomBar = {
            val showElevation = !readingMode && codeMode && bodyScroll.canScrollForward
            val barColor by animateColorAsState(
                targetValue = if (showElevation) MaterialTheme.colorScheme.surfaceContainerLow
                else MaterialTheme.colorScheme.surface,
                label = "ActionRowColorAnimation"
            )
            // 对齐 Kori AdaptiveActionRow：Section 色随 showElevation 在 surface ↔ surfaceContainerLow 间动画
            val sectionColor by animateColorAsState(
                targetValue = if (showElevation) MaterialTheme.colorScheme.surface
                else MaterialTheme.colorScheme.surfaceContainerLow,
                label = "ActionSectionColorAnimation"
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(barColor)
                    .navigationBarsPadding()
            ) {
                AnimatedVisibility(visible = !readingMode) {
                    when {
                        // Todo 源码编辑排（对齐 Kori TodoTextActionRow：优先级/项目/上下文/完成 + 日期）
                        todoMode -> EditorActionRow {
                            ActionSection(sectionColor) {
                                ActionButton(Icons.Default.TextFields, stringResource(R.string.edit_004)) { insertAtCursor("(A) ") }
                                ActionButton(Icons.Default.Add, stringResource(R.string.edit_005)) { insertAtCursor("+") }
                                ActionButton(Icons.Default.AlternateEmail, stringResource(R.string.edit_006)) { insertAtCursor("@") }
                                ActionButton(Icons.Default.CheckBox, stringResource(R.string.edit_007)) { toggleTodoDoneAtCursor() }
                            }
                            ActionSection(sectionColor) {
                                ActionButton(Icons.Default.Event, stringResource(R.string.edit_008)) {
                                    val d = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                                    insertAtCursor("$d ")
                                }
                            }
                            // 撤回/恢复（对齐 Kori 编辑器底部常驻 undo/redo，按可用性置灰）
                            ActionSection(sectionColor) {
                                ActionButton(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.edit_088), enabled = canUndo) { performUndo() }
                                ActionButton(Icons.AutoMirrored.Filled.Redo, stringResource(R.string.edit_089), enabled = canRedo) { performRedo() }
                            }
                        }
                        // 编程模式快捷插入排（分组式，对齐 Kori ActionRow 分组结构 + 更多常用符号）
                        codeMode -> EditorActionRow {
                            ActionSection(sectionColor) {
                                ActionButton(Icons.Default.Code, stringResource(R.string.edit_009)) { insertAtCursor("```\n\n```", 4) }
                                ActionButton(Icons.Default.FormatIndentIncrease, stringResource(R.string.edit_010)) { insertAtCursor("    ") }
                                ActionButton(Icons.Default.Notes, stringResource(R.string.edit_011)) { insertAtCursor("// ") }
                                ActionButton(Icons.Default.FormatQuote, stringResource(R.string.edit_012)) { insertAtCursor("> ") }
                            }
                            ActionSection(sectionColor) {
                                ActionButton(Icons.Default.FormatListBulleted, stringResource(R.string.edit_013)) { insertAtCursor("\n- ") }
                                ActionButton(Icons.Default.FormatListNumbered, stringResource(R.string.edit_014)) { insertAtCursor("\n1. ") }
                                ActionButton(Icons.Default.HorizontalRule, stringResource(R.string.edit_015)) { insertAtCursor("\n---\n") }
                            }
                            ActionSection(sectionColor) {
                                ActionButton(Icons.Default.CheckBox, stringResource(R.string.edit_016)) { insertAtCursor("\n- [ ] ") }
                                ActionButton(Icons.Default.Link, stringResource(R.string.edit_017)) { insertAtCursor("[]()", 1) }
                                ActionButton(Icons.Default.Title, stringResource(R.string.edit_018)) { insertAtCursor("\n## ") }
                                ActionButton(Icons.Default.FormatBold, stringResource(R.string.edit_019)) { insertAtCursor("****", 2) }
                                ActionButton(Icons.Default.FormatItalic, stringResource(R.string.edit_020)) { insertAtCursor("**", 1) }
                            }
                            ActionSection(sectionColor) {
                                ActionButton(Icons.Default.Functions, stringResource(R.string.edit_021)) { insertAtCursor("<>", 1) }
                                ActionButton(Icons.Default.DataObject, stringResource(R.string.edit_022)) { insertAtCursor("{}", 1) }
                                ActionButton(Icons.Default.Terminal, stringResource(R.string.edit_023)) { insertAtCursor("()", 1) }
                                ActionButton(Icons.Default.DataArray, stringResource(R.string.edit_024)) { insertAtCursor("[]", 1) }
                            }
                            // 撤回/恢复（对齐 Kori 编辑器底部常驻 undo/redo，按可用性置灰）
                            ActionSection(sectionColor) {
                                ActionButton(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.edit_088), enabled = canUndo) { performUndo() }
                                ActionButton(Icons.AutoMirrored.Filled.Redo, stringResource(R.string.edit_089), enabled = canRedo) { performRedo() }
                            }
                        }
                        // 普通文本排（对齐 Kori PlainTextActionRow 分组结构 + notepad 附件/链接/定时）
                        else -> EditorActionRow {
                            ActionSection(sectionColor) {
                                ActionButton(Icons.Default.Image, stringResource(R.string.edit_025)) { imagePicker.launch("image/*") }
                                ActionButton(Icons.Default.Videocam, stringResource(R.string.edit_026)) { videoPicker.launch("video/*") }
                                ActionButton(Icons.Default.AttachFile, stringResource(R.string.edit_027)) { filePicker.launch("*/*") }
                                ActionButton(Icons.Default.MusicNote, stringResource(R.string.repo_002)) { showAudioDialog = true }
                            }
                            ActionSection(sectionColor) {
                                ActionButton(Icons.Default.Link, stringResource(R.string.edit_028)) { showLinkDialog = true }
                                ActionButton(Icons.Default.Event, stringResource(R.string.edit_029)) {
                                    openSystemCalendar(context, title.ifBlank { context.getString(R.string.edit_030) }, content)
                                }
                            }
                            // 撤回/恢复（对齐 Kori 编辑器底部常驻 undo/redo，按可用性置灰）
                            ActionSection(sectionColor) {
                                ActionButton(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.edit_088), enabled = canUndo) { performUndo() }
                                ActionButton(Icons.AutoMirrored.Filled.Redo, stringResource(R.string.edit_089), enabled = canRedo) { performRedo() }
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 顶栏：从 Scaffold topBar slot 挪入 content（修复侧栏被 topBar slot 覆盖的重叠 bug）
            TopAppBar(
                // 对齐 Kori NoteScreen：title = 文件夹选择 FilledTonalButton（sizeIn maxWidth 160 + basicMarquee 滚动长名）
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(
                            modifier = Modifier.sizeIn(maxWidth = 160.dp),
                            onClick = { showFolderDialog = true },
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Icon(Icons.Default.FolderOpen, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                folderNameOf(folders, folderId),
                                maxLines = 1,
                                modifier = Modifier.basicMarquee()
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = { saveAndBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.web_002)) }
                },
                actions = {
                    // 对齐 Kori：搜索替换钮（编辑态显示，isSearching 切换 Search/SearchOff）
                    if (!readingMode) {
                        IconButton(onClick = {
                            isSearching = !isSearching
                            if (!isSearching) { searchWord = ""; replaceWord = ""; matchIndex = 0 }
                        }) {
                            Icon(
                                if (isSearching) Icons.Default.SearchOff else Icons.Default.Search,
                                contentDescription = stringResource(R.string.edit_031)
                            )
                        }
                    }
                    // undo/redo 撤回恢复（已移至底部操作排，见 bottomBar）
                    // （AI 唤醒按钮已删：对齐 Kori，AI 面板常驻左下角自动弹出，见 content 内挂载点）
                    // 离线工具箱（统计/待办/排版/大小写/序号）
                    Box {
                        IconButton(onClick = { showToolsMenu = true }) {
                            Icon(Icons.Default.Handyman, contentDescription = stringResource(R.string.ai_060))
                        }
                        OfflineToolsMenu(
                            expanded = showToolsMenu,
                            text = content,
                            onDismiss = { showToolsMenu = false },
                            onSelect = { title, res ->
                                offlineToolTitle = title
                                offlineToolResult = res
                            }
                        )
                    }
                    // 阅读/编辑切换钮：图标与文字都表示「当前模式」（不是「点击后切到哪」），点击切换
                    TextButton(
                        onClick = {
                            readingMode = !readingMode
                            if (readingMode) focusManager.clearFocus()
                        }
                    ) {
                        Icon(
                            if (readingMode) Icons.AutoMirrored.Filled.MenuBook else Icons.Default.EditNote,
                            contentDescription = if (readingMode) stringResource(R.string.edit_091) else stringResource(R.string.edit_090),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (readingMode) stringResource(R.string.edit_091) else stringResource(R.string.edit_090),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    IconButton(onClick = { isSideSheetOpen = true }) {
                        Icon(Icons.Default.VerticalSplit, contentDescription = stringResource(R.string.edit_034))
                    }
                }
            )
            // 标题输入框（常驻；阅读态标题为空时隐藏，对齐 Kori if (!isReadView || title 非空)）
            if (!readingMode || title.isNotEmpty()) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    BasicTextField(
                        value = title,
                        onValueChange = { title = it },
                        textStyle = MaterialTheme.typography.headlineSmall.copy(
                            color = scheme.onSurface,
                            fontWeight = FontWeight.Medium
                        ),
                        cursorBrush = SolidColor(scheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = androidx.compose.ui.text.input.ImeAction.Done
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onDone = { focusManager.clearFocus() }
                        ),
                        decorationBox = { inner ->
                            if (title.isEmpty()) {
                                Text(
                                    stringResource(R.string.edit_018),
                                    color = scheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Medium)
                                )
                            }
                            inner()
                        }
                    )
                }
            }
            // 搜索替换浮层（AnimatedVisibility 浮在正文上方，不挤占标题/文件夹按钮，对齐 Kori 搜索替换浮层）
            AnimatedVisibility(
                visible = isSearching,
                enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { -it / 4 },
                exit = fadeOut(tween(180))
            ) {
                FindAndReplaceField(
                    searchWord = searchWord, onSearchWord = { searchWord = it; matchIndex = 0 },
                    replaceWord = replaceWord, onReplaceWord = { replaceWord = it },
                    positionText = if (searchWord.isBlank()) "" else "${matches.size.takeIf { n -> n > 0 && matchIndex < n }?.let { n -> matchIndex + 1 } ?: 0}/${matches.size}",
                    findFocusRequester = findFocusRequester,
                    onPrevious = { navigateMatch(-1) },
                    onNext = { navigateMatch(+1) },
                    onReplaceCurrent = { replaceCurrent() },
                    onReplaceAll = { replaceAllMatches() }
                )
            }

            // 正文：阅读视图（对齐 Kori viewer）；编程模式对齐 Kori——等宽字体 + 行号槽 + 竖分隔线 + 光标跟随滚动
            if (readingMode && todoMode) {
                // Todo 清单视图（对齐 Kori TodoViewer：滑动切完成/删除 + 优先级圆点 + 过滤 chips）
                TodoViewerView(
                    content = content,
                    onContentChange = { contentValue = TextFieldValue(it, TextRange(it.length)) },
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            } else if (codeMode) {
                val density = androidx.compose.ui.platform.LocalDensity.current
                val monoStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = scheme.onSurface,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    lineHeight = 22.sp
                )
                // 编辑态语法高亮（等效 Kori MarkdownTransformation：正则分色，只改样式不改文本）
                val codeHighlightTransformation = remember(scheme) { CodeHighlightTransformation(scheme) }
                // 光标跟随滚动（对齐 Kori scrollState 联动）
                LaunchedEffect(contentValue.selection, bodyLayout, bodyViewport) {
                    val lr = bodyLayout ?: return@LaunchedEffect
                    val cursor = contentValue.selection.end.coerceIn(0, contentValue.text.length)
                    val line = lr.getLineForOffset(cursor)
                    val top = lr.getLineTop(line)
                    val bottom = lr.getLineBottom(line)
                    if (top < bodyScroll.value) {
                        bodyScroll.scrollTo(top.coerceAtLeast(0f).toInt())
                    } else if (bodyViewport > 0 && bottom > bodyScroll.value + bodyViewport) {
                        bodyScroll.scrollTo((bottom - bodyViewport).coerceAtLeast(0f).toInt())
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .onSizeChanged { bodyViewport = it.height }
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                    ) {
                        // 行号槽（固定不随内容滚动，占布局宽度，Canvas 平移对齐正文）
                        if (showLineNumbers) {
                            EditorLineNumbers(
                                bodyLayout = bodyLayout,
                                cursorOffset = contentValue.selection.end,
                                scrollProvider = { bodyScroll.value },
                                modifier = Modifier
                                    .padding(top = 8.dp),
                                monoStyle = monoStyle,
                                scheme = scheme,
                                showLineNumbers = showLineNumbers
                            )
                        }
                        // 正文区
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            BasicTextField(
                                value = contentValue,
                                onValueChange = { contentValue = it },
                                readOnly = readingMode,
                                textStyle = monoStyle,
                                cursorBrush = SolidColor(scheme.primary),
                                visualTransformation = codeHighlightTransformation,
                                onTextLayout = { bodyLayout = it },
                                // 不使用 verticalScroll：它把子项高度约束改成无限，会让输入框塌成一行，
                                // 下方区域点击落空、无法聚焦。改为 fillMaxSize 填满容器，长文本由输入框内部滚动。
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(start = 10.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                                decorationBox = { inner ->
                                    if (content.isEmpty()) {
                                        Text(stringResource(R.string.edit_035), color = scheme.onSurfaceVariant, style = monoStyle)
                                    }
                                    inner()
                                }
                            )
                        }
                    }
                }
            } else {
                // 对齐 Kori AdaptiveEditorViewer：阅读/编辑双态 AnimatedContent 方向性滑动（进阅读从右滑入，回编辑反向）
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    val density = androidx.compose.ui.platform.LocalDensity.current
                    // 光标跟随滚动（对齐 Kori scrollState 联动，编辑态生效）
                    LaunchedEffect(contentValue.selection, bodyLayout, bodyViewport) {
                        val lr = bodyLayout ?: return@LaunchedEffect
                        val cursor = contentValue.selection.end.coerceIn(0, contentValue.text.length)
                        val line = lr.getLineForOffset(cursor)
                        val top = lr.getLineTop(line)
                        val bottom = lr.getLineBottom(line)
                        if (top < bodyScroll.value) {
                            bodyScroll.scrollTo(top.coerceAtLeast(0f).toInt())
                        } else if (bodyViewport > 0 && bottom > bodyScroll.value + bodyViewport) {
                            bodyScroll.scrollTo((bottom - bodyViewport).coerceAtLeast(0f).toInt())
                        }
                    }
                    AnimatedContent(
                        targetState = readingMode,
                        transitionSpec = {
                            if (targetState) {
                                // 进阅读：编辑滑出左侧，阅读从右滑入（像翻到预览页）
                                (fadeIn(tween(260)) + slideInHorizontally(tween(260)) { it / 4 }) togetherWith
                                    (fadeOut(tween(200)) + slideOutHorizontally(tween(200)) { -it / 6 })
                            } else {
                                // 回编辑：反向滑动
                                (fadeIn(tween(260)) + slideInHorizontally(tween(260)) { -it / 4 }) togetherWith
                                    (fadeOut(tween(200)) + slideOutHorizontally(tween(200)) { it / 6 })
                            }
                        },
                        label = "read_edit_switch"
                    ) { reading ->
                        if (reading) {
                            // 阅读模式：正文用 ClickableText 渲染（URL 自动高亮可点击），下方附附件预览
                            Column(
                                Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                // 链接预览 (点击链接后的内联 WebView 预览, 不跳外部浏览器)
                                val linkPreviewUrl = remember { mutableStateOf<String?>(null) }
                                if (content.isEmpty()) {
                                    Text(
                                        stringResource(R.string.edit_036),
                                        color = scheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.padding(start = 16.dp, end = 16.dp)
                                    )
                                } else {
                                    // 对齐 Kori：正文按内联附件标记分段渲染（文本段保留 URL 高亮，媒体块内联显示）
                                    val segments = remember(content) { parseInlineAttachments(content) }
                                    segments.forEach { seg ->
                                        when (seg) {
                                            is InlineSegment.Text -> {
                                                // 代码模式下用语法高亮渲染，否则保留 URL 高亮
                                                val linkText = remember(seg.text, codeMode) {
                                                    if (codeMode) buildCodeHighlightAnnotatedString(seg.text, scheme)
                                                    else buildLinkAnnotatedString(seg.text, scheme.primary)
                                                }
                                                ClickableText(
                                                    text = linkText,
                                                    style = MaterialTheme.typography.bodyLarge.copy(
                                                        color = scheme.onSurface,
                                                        lineHeight = 26.sp
                                                    ),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        // 阅读模式正文加宽左右边距，避免文字整体贴左（对齐 Kori 书本式排版）
                                                        .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
                                                    onClick = { offset ->
                                                        linkText
                                                            .getLinkAnnotations(offset, offset)
                                                            .firstOrNull()?.let { annotation ->
                                                                if (annotation is LinkAnnotation.Url) {
                                                                    linkPreviewUrl.value = annotation.url
                                                                }
                                                            }
                                                    }
                                                )
                                            }
                                            is InlineSegment.Image -> AsyncImage(
                                                model = File(seg.path),
                                                contentDescription = seg.name,
                                                contentScale = ContentScale.FillWidth,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                            )
                                            is InlineSegment.Video -> InlineVideoPlayer(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                                videoPath = seg.path,
                                                fileName = seg.name,
                                                scheme = scheme
                                            )
                                            is InlineSegment.Audio -> Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                                                InlineAudioRow(path = seg.path, name = seg.name, scheme = scheme)
                                            }
                                            is InlineSegment.FileLink -> Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { openFile(context, seg.path) }
                                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                            ) {
                                                Icon(Icons.Default.InsertDriveFile, null, tint = scheme.primary, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(8.dp))
                                                Text(seg.name, maxLines = 1, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface, modifier = Modifier.weight(1f))
                                                Icon(Icons.Default.OpenInNew, stringResource(R.string.web_010), tint = scheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                    // 内联链接预览
                                    AnimatedVisibility(visible = linkPreviewUrl.value != null) {
                                        val url = linkPreviewUrl.value
                                        if (url != null) {
                                            LinkPreviewCard(
                                                url = url,
                                                scheme = scheme,
                                                onDismiss = { linkPreviewUrl.value = null },
                                                onOpenBrowser = {
                                                    // 统一走内置浏览器（不再跳外部浏览器）
                                                    onOpenUrl(url)
                                                }
                                            )
                                        }
                                    }
                                }
                                // 阅读模式附件查看：图片内联大图 + 视频内联播放 + 文件点开
                                // （已被正文内联标记引用的附件不再重复展示）
                                val referenced = remember(content) { extractReferencedPaths(content) }
                                ReadModeAttachments(
                                    images = images.filter { it !in referenced },
                                    videos = videos.filter { it !in referenced },
                                    files = files.filter { it.path !in referenced },
                                    audios = audios.filter { it !in referenced },
                                    onOpenFile = { path -> openFile(context, path) }
                                )
                            }
                        } else {
                            // 编辑模式：行号槽（固定不随内容滚动，占布局宽度）+ 可滚动正文区
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .onSizeChanged {
                                        bodyViewport = it.height
                                    }
                            ) {
                                Row(
                                    Modifier
                                        .fillMaxSize()
                                ) {
                                    // 行号槽（固定不随内容滚动，Canvas 平移对齐正文）
                                    if (showLineNumbers) {
                                        EditorLineNumbers(
                                            bodyLayout = bodyLayout,
                                            cursorOffset = contentValue.selection.end,
                                            scrollProvider = { bodyScroll.value },
                                            modifier = Modifier
                                                .padding(top = 16.dp),
                                            monoStyle = MaterialTheme.typography.bodyLarge.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 14.sp,
                                                lineHeight = 26.sp
                                            ),
                                            scheme = scheme,
                                            showLineNumbers = showLineNumbers
                                        )
                                    }
                                    // 正文区
                                    Column(
                                        Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                    ) {
                                        BasicTextField(
                                            value = contentValue,
                                            onValueChange = { contentValue = it },
                                            readOnly = false,
                                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                                color = scheme.onSurface,
                                                lineHeight = 26.sp
                                            ),
                                            // 对齐 Kori：普通模式接入 Markdown 可视化变换（分色标注，不改文本）
                                            visualTransformation = remember { MarkdownTransformation() },
                                            cursorBrush = SolidColor(scheme.primary),
                                            onTextLayout = { bodyLayout = it },
                                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                                capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
                                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
                                                imeAction = androidx.compose.ui.text.input.ImeAction.None
                                            ),
                                            // 同上：去掉 verticalScroll，输入框铺满整块区域，点任意位置都能聚焦输入
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
                                            decorationBox = { inner ->
                                                if (content.isEmpty()) {
                                                    Text(
                                                        stringResource(R.string.edit_036),
                                                        color = scheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                        style = MaterialTheme.typography.bodyLarge
                                                    )
                                                }
                                                inner()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

        }
        // ==================== AI 助手面板（覆盖层：必须放在 Column 之外） ====================
        // 事故根因：面板曾作为 Column 的子项，而它的根布局是 BoxWithConstraints(fillMaxSize)。
        // Column 先测量非 weight 子项 → 面板吃掉全部剩余高度 → 正文 Box(weight(1f)) 被压成 0 高，
        // 于是「标题能写、正文看不见也点不到、点击毫无反应」。移到 Column 外即不再参与高度分配。
        AnimatedVisibility(
            visible = showAiAssistant,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            AiAssistantPanel(
                text = content,
                isTextSelectionCollapsed = contentValue.selection.collapsed,
                onApply = { newText ->
                    // 对齐 Kori：应用结果只回填正文，面板常驻不关闭
                    contentValue = TextFieldValue(newText, TextRange(newText.length))
                },
                // 面板右上角 X = 手动收回（切阅读/搜索模式自动重新弹出）
                onDismiss = { aiManuallyHidden = true }
            )
        }
    }

    // 文件夹选择对话框（1:1 对齐 Kori FoldersDialog：上下分隔线 + FolderOpen/Folder 图标 + RadioButton trailing + 取消/确认）
    if (showFolderDialog) {
        var selectedFolderId by remember { mutableStateOf(folderId) }
        AlertDialog(
            onDismissRequest = { showFolderDialog = false },
            title = { Text(stringResource(R.string.edit_037)) },
            text = {
                Column {
                    HorizontalDivider(Modifier.fillMaxWidth())
                    // 对齐 Kori FoldersDialog：首项「全部笔记」= 移出文件夹（folderId 置空）
                    val allSelected = selectedFolderId.isBlank()
                    ListItem(
                        modifier = Modifier.clickable { selectedFolderId = "" },
                        leadingContent = {
                            Icon(
                                imageVector = if (allSelected) Icons.Default.FolderOpen else Icons.Default.Folder,
                                tint = MaterialTheme.colorScheme.primary,
                                contentDescription = null
                            )
                        },
                        headlineContent = { Text(stringResource(R.string.list_030)) },
                        trailingContent = { RadioButton(selected = allSelected, onClick = null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    folders.forEach { f ->
                        val isSelected = selectedFolderId == f.id
                        ListItem(
                            modifier = Modifier.clickable { selectedFolderId = f.id },
                            leadingContent = {
                                Icon(
                                    imageVector = if (isSelected) Icons.Default.FolderOpen else Icons.Default.Folder,
                                    tint = MaterialTheme.colorScheme.primary,
                                    contentDescription = null
                                )
                            },
                            headlineContent = { Text(folderDisplayName(f), maxLines = 1) },
                            trailingContent = { RadioButton(selected = isSelected, onClick = null) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                    HorizontalDivider(Modifier.fillMaxWidth())
                }
            },
            dismissButton = { TextButton(onClick = { showFolderDialog = false }) { Text(stringResource(R.string.memo_010)) } },
            confirmButton = {
                TextButton(onClick = {
                    folderId = selectedFolderId
                    showFolderDialog = false
                }) { Text(stringResource(R.string.edit_038)) }
            }
        )
    }

    // ==================== 右侧概览侧栏（1:1 对齐 Kori NoteSideSheet） ====================
    // scrim 淡入 + 面板从右滑入；左 48dp 操作栏（modalContainerColor 0.9）+ 右 320dp 概览（0.95、左圆角、shadow 2）
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = isSideSheetOpen,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(scheme.scrim.copy(alpha = 0.32f))
                    .clickable { isSideSheetOpen = false }
            )
        }
        AnimatedVisibility(
            visible = isSideSheetOpen,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val drawerWidth = minOf(320.dp, maxWidth - 96.dp)
                val haptic = LocalHapticFeedback.current
                Row(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    // 左侧操作栏（对齐 Kori actionContent：置顶/收藏/删除/类型切换/编程模式/分享）
                    Column(
                        Modifier
                            .padding(top = 16.dp)
                            .background(
                                color = DrawerDefaults.modalContainerColor,
                                shape = MaterialTheme.shapes.extraLarge.copy(
                                    topEnd = CornerSize(0.dp),
                                    bottomEnd = CornerSize(0.dp)
                                )
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        IconToggleButton(
                            checked = pinned,
                            onCheckedChange = {
                                haptic.performHapticFeedback(
                                    if (it) HapticFeedbackType.LongPress else HapticFeedbackType.LongPress
                                )
                                pinned = it
                            }
                        ) {
                            Icon(
                                imageVector = if (pinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                                contentDescription = stringResource(R.string.edit_039),
                                tint = if (pinned) MaterialTheme.colorScheme.primary else LocalContentColor.current
                            )
                        }
                        IconToggleButton(
                            checked = favorite,
                            onCheckedChange = {
                                haptic.performHapticFeedback(
                                    if (it) HapticFeedbackType.LongPress else HapticFeedbackType.LongPress
                                )
                                favorite = it
                            }
                        ) {
                            Icon(
                                imageVector = if (favorite) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = stringResource(R.string.edit_040),
                                tint = if (favorite) MaterialTheme.colorScheme.primary else LocalContentColor.current
                            )
                        }
                        IconButton(
                            onClick = {
                                repo.deleteToTrash(id)
                                toast(context.getString(R.string.edit_041))
                                isSideSheetOpen = false
                                onBack()
                            },
                            enabled = !isNew
                        ) {
                            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.memo_006))
                        }
                        // 类型切换（对齐 Kori NoteTypeDialog：文本/待办/代码 三选一）
                        // 用一个分段切换替代原来的「text/todo 互换 + code 开关」两个独立图标，
                        // 让三种编辑模式一目了然。
                        NoteTypeSegment(
                            current = when {
                                codeMode -> 2
                                todoMode -> 1
                                else -> 0
                            },
                            onSelect = { which ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                when (which) {
                                    0 -> { todoMode = false; codeMode = false }
                                    1 -> { todoMode = true; codeMode = false }
                                    else -> { todoMode = false; codeMode = true }
                                }
                                toast(
                                    when (which) {
                                        0 -> context.getString(R.string.edit_043)
                                        1 -> context.getString(R.string.edit_042)
                                        else -> context.getString(R.string.edit_045)
                                    }
                                )
                            }
                        )
                        IconButton(onClick = { showShareDialog = true }) {
                            Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.web_008))
                        }
                    }
                    // 右侧概览主体（对齐 Kori drawerContent）
                    Surface(
                        color = DrawerDefaults.modalContainerColor,
                        shape = MaterialTheme.shapes.large.copy(
                            topEnd = CornerSize(0.dp),
                            bottomEnd = CornerSize(0.dp)
                        ),
                        shadowElevation = 2.dp,
                        tonalElevation = DrawerDefaults.ModalDrawerElevation,
                        modifier = Modifier.width(drawerWidth).fillMaxHeight()
                    ) {
                        Column(
                            Modifier
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                        ) {
                            // 顶部：「概览」titleLarge Bold + 关闭钮（对齐 Kori 顶部操作行）
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    modifier = Modifier.padding(start = 12.dp),
                                    text = stringResource(R.string.edit_048),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(onClick = { isSideSheetOpen = false }) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.edit_049))
                                }
                            }
                            // 统计明细（对齐 Kori NoteSideSheetItem：key：value，Bold onSurfaceVariant）
                            SelectionContainer {
                                Column(Modifier.padding(start = 16.dp, end = 12.dp)) {
                                    val timeFmt = remember {
                                        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                                    }
                                    NoteSideSheetItem(
                                        stringResource(R.string.edit_050),
                                        timeFmt.format(java.util.Date(original?.createdAt ?: System.currentTimeMillis()))
                                    )
                                    NoteSideSheetItem(
                                        stringResource(R.string.edit_051),
                                        timeFmt.format(java.util.Date(original?.updatedAt ?: System.currentTimeMillis()))
                                    )
                                    NoteSideSheetItem(stringResource(R.string.edit_052), content.length.toString())
                                    NoteSideSheetItem(stringResource(R.string.edit_053), content.lines().size.toString())
                                    if (todoMode) {
                                        val (undone, done) = remember(content) { parseTodo(content) }
                                        NoteSideSheetItem(stringResource(R.string.edit_054), undone.size.toString())
                                        NoteSideSheetItem(stringResource(R.string.edit_055), done.size.toString())
                                    }
                                }
                            }
                            // 附件区（正文区已移除，入口在底部操作排）
                            HorizontalDivider(Modifier.padding(top = 8.dp))
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(end = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    modifier = Modifier.padding(start = 12.dp),
                                    text = stringResource(R.string.edit_056),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    stringResource(R.string.edit_res_005, images.size + videos.size + files.size + audios.size),
                                    modifier = Modifier.padding(end = 12.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (images.isEmpty() && videos.isEmpty() && files.isEmpty() && audios.isEmpty()) {
                                Text(
                                    stringResource(R.string.edit_057),
                                    modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (images.isNotEmpty()) {
                                Text(
                                    stringResource(R.string.edit_058),
                                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(images) { path ->
                                        Box {
                                            AsyncImage(
                                                model = File(path),
                                                contentDescription = stringResource(R.string.edit_058),
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp))
                                            )
                                            IconButton(
                                                onClick = { images = images - path; removeAttachmentMarker(path) },
                                                modifier = Modifier.align(Alignment.TopEnd).size(24.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Close, contentDescription = stringResource(R.string.edit_059),
                                                    tint = Color.White, modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            if (videos.isNotEmpty()) {
                                Text(
                                    stringResource(R.string.edit_060),
                                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Column(Modifier.padding(start = 16.dp, end = 12.dp)) {
                                    videos.forEach { path ->
                                        key(path) {
                                            InlineVideoPlayer(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                                videoPath = path,
                                                fileName = path.substringAfterLast('/'),
                                                scheme = scheme,
                                                onRemove = { videos = videos - path; removeAttachmentMarker(path) }
                                            )
                                        }
                                    }
                                }
                            }
                            if (files.isNotEmpty()) {
                                Text(
                                    stringResource(R.string.edit_061),
                                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Column(Modifier.padding(start = 16.dp, end = 12.dp)) {
                                    files.forEach { f ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                        ) {
                                            Icon(Icons.Default.InsertDriveFile, null, tint = scheme.primary)
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                f.name, modifier = Modifier.weight(1f), maxLines = 1,
                                                style = MaterialTheme.typography.bodySmall, color = scheme.onSurface
                                            )
                                            IconButton(onClick = { files = files - f; removeAttachmentMarker(f.path) }) {
                                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.edit_059), modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                            if (audios.isNotEmpty()) {
                                Text(
                                    stringResource(R.string.repo_002),
                                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Column(Modifier.padding(start = 16.dp, end = 12.dp, bottom = 8.dp)) {
                                    audios.forEach { path ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                        ) {
                                            Icon(Icons.Default.MusicNote, null, tint = scheme.primary)
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                path.substringAfterLast('/'),
                                                modifier = Modifier.weight(1f), maxLines = 1,
                                                style = MaterialTheme.typography.bodySmall, color = scheme.onSurface
                                            )
                                            IconButton(onClick = { audios = audios - path; removeAttachmentMarker(path) }) {
                                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.edit_059), modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                            // 标签区（从正文区移入侧栏）
                            HorizontalDivider(Modifier.padding(top = 8.dp))
                            Text(
                                modifier = Modifier.padding(start = 12.dp, top = 8.dp),
                                text = stringResource(R.string.edit_062),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                            OutlinedTextField(
                                value = tags,
                                onValueChange = { tags = it },
                                placeholder = { Text(stringResource(R.string.edit_063)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 12.dp, bottom = 16.dp),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = scheme.onSurface)
                            )
                        }
                    }
                }
            }
        }
        }
    }

    // 插入链接对话框
    if (showLinkDialog) {
        var linkUrl by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showLinkDialog = false },
            title = { Text(stringResource(R.string.edit_028)) },
            text = {
                OutlinedTextField(value = linkUrl, onValueChange = { linkUrl = it }, label = { Text(stringResource(R.string.edit_064)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(onClick = {
                    if (linkUrl.isNotBlank()) {
                        // 直接插入纯 URL，不加括号
                        val newContent = if (content.isNotBlank()) "$content\n$linkUrl" else linkUrl
                        contentValue = TextFieldValue(newContent, TextRange(newContent.length))
                    }
                    showLinkDialog = false
                }) { Text(stringResource(R.string.edit_065)) }
            },
            dismissButton = { TextButton(onClick = { showLinkDialog = false }) { Text(stringResource(R.string.memo_010)) } }
        )
    }

    // 音频对话框：录制 / 导入
    if (showAudioDialog) {
        AlertDialog(
            onDismissRequest = { showAudioDialog = false },
            title = { Text(stringResource(R.string.repo_002)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isRecording) {
                        Text(stringResource(R.string.edit_066), style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            if (isRecording) {
                                // 停止录音
                                runCatching {
                                    mediaRecorder.value?.stop()
                                    mediaRecorder.value?.release()
                                    mediaRecorder.value = null
                                }
                                currentRecording?.let { audios = audios + it }
                                currentRecording = null
                                isRecording = false
                                showAudioDialog = false
                                android.widget.Toast.makeText(context, context.getString(R.string.edit_067), android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                // 开始录音：先确认麦克风权限（未授权则弹系统请求）
                                val hasMic = androidx.core.content.ContextCompat.checkSelfPermission(
                                    context, android.Manifest.permission.RECORD_AUDIO
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                if (!hasMic) {
                                    recordAudioPermLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                } else {
                                    val dest = File(storageDir("audios"), "${System.currentTimeMillis()}.m4a")
                                    dest.parentFile?.mkdirs()
                                    runCatching {
                                        val mr = android.media.MediaRecorder().apply {
                                            setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
                                            setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
                                            setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
                                            setOutputFile(dest.absolutePath)
                                            prepare()
                                            start()
                                        }
                                        mediaRecorder.value = mr
                                        currentRecording = dest.absolutePath
                                        isRecording = true
                                    }.onFailure {
                                        android.widget.Toast.makeText(context, context.getString(R.string.edit_res_006, it.message ?: ""), android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }) {
                            Icon(if (isRecording) Icons.Default.Stop else Icons.Default.Mic, null)
                            Spacer(Modifier.width(4.dp))
                            Text(if (isRecording) stringResource(R.string.edit_068) else stringResource(R.string.edit_069))
                        }
                        Button(onClick = {
                            showAudioDialog = false
                            audioPicker.launch("audio/*")
                        }) {
                            Icon(Icons.Default.Upload, null)
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.edit_070))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAudioDialog = false }) { Text(stringResource(R.string.edit_071)) } }
        )
    }

    // （AI 助手面板已改挂上方 Scaffold content 内，对齐 Kori：z 序低于 bottomBar 工具栏，不再遮挡上传图片等按钮）

    // ==================== 离线工具箱结果浮层（底部悬浮，对齐 Kori） ====================
    if (offlineToolResult != null) {
        Box(Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 12.dp, vertical = 80.dp)
                    .clip(RoundedCornerShape(14.dp)),
                tonalElevation = 2.dp
            ) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        offlineToolTitle,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { offlineToolResult = null }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    offlineToolResult ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 160.dp)
                        .verticalScroll(rememberScrollState())
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        contentValue = TextFieldValue(offlineToolResult!!, TextRange(offlineToolResult!!.length))
                        offlineToolResult = null
                    },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Done, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.ai_019))
                }
            }
        }
        } // 关闭 Box（离线结果浮层悬浮容器）
    }
}

/** 用系统日历添加事件（对齐 OpenNote：直接调 ACTION_INSERT，用户自选时间） */
fun openSystemCalendar(context: android.content.Context, title: String, content: String) {
    runCatching {
        val intent = android.content.Intent(android.content.Intent.ACTION_INSERT).apply {
            data = android.provider.CalendarContract.Events.CONTENT_URI
            putExtra(android.provider.CalendarContract.Events.TITLE, title)
            putExtra(android.provider.CalendarContract.Events.DESCRIPTION, content)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }.onFailure {
        android.widget.Toast.makeText(context, context.getString(R.string.edit_072), android.widget.Toast.LENGTH_SHORT).show()
    }
}

/** 用 AlarmManager 设置定时任务（指定时间戳触发） */
fun scheduleReminder(context: android.content.Context, title: String, content: String, triggerAt: Long) {
    runCatching {
        val alarmManager = context.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = android.content.Intent(context, ReminderReceiver::class.java).apply {
            putExtra("title", title)
            putExtra("content", content)
        }
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            context,
            triggerAt.toInt(),
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
    }
}

/** Kori AdaptiveActionRow 风格：48dp 横向滚动操作条，内容为分组 Surface + 图标按钮；
 *  原 AI 让位 startPadding 已移除：AI 圆钮现固定悬浮于工具栏上方（不重叠），
 *  对齐改为 CenterStart 使超宽内容初始滚动位置贴左（按钮组整体左移） */
@Composable
private fun EditorActionRow(
    content: @Composable RowScope.() -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth().height(48.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Spacer(Modifier.width(4.dp))
            content()
            Spacer(Modifier.width(4.dp))
        }
    }
}

/** Kori ActionRowSection：分组圆角 Surface（色随 showElevation 动画，上下留 4dp） */
@Composable
private fun RowScope.ActionSection(
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .padding(vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        color = color
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) { content() }
    }
}

/** Kori ActionButton：方形图标按钮（40x40，点击区域铺满格） */
@Composable
private fun ActionButton(
    icon: ImageVector,
    description: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .aspectRatio(1f)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon, contentDescription = description,
            tint = if (enabled) tint else MaterialTheme.colorScheme.outlineVariant
        )
    }
}

/** Kori NoteSideSheetItem：「key：」加粗 onSurfaceVariant + value 常规，bodyLarge */
@Composable
private fun NoteSideSheetItem(key: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = buildAnnotatedString {
                withStyle(
                    SpanStyle(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) { append("$key：") }
                withStyle(
                    SpanStyle(
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) { append(value) }
            },
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

// ==================== 附件内联（对齐 Kori：文字中夹附件，阅读态按标记渲染媒体块） ====================

/** 正文内联附件标记的解析结果：文本段或媒体块。 */
private sealed class InlineSegment {
    data class Text(val text: String) : InlineSegment()
    data class Image(val path: String, val name: String) : InlineSegment()
    data class Video(val path: String, val name: String) : InlineSegment()
    data class Audio(val path: String, val name: String) : InlineSegment()
    data class FileLink(val path: String, val name: String) : InlineSegment()
}

/**
 * 解析正文中的内联附件标记（与 Kori EditorMutators 插入语法一致）：
 * - 图片 `![name](/path)`；视频 `<video src="/path" controls>name</video>`；音频 `<audio src="/path" controls>name</audio>`
 * - 文件 `[name](/local/path)`（仅本地绝对路径，http 链接不匹配，保持 URL 高亮逻辑）
 */
private val inlineAttachmentRegex = Regex(
    """!\[([^\]]*)]\(([^)]+)\)""" +                      // 1=name 2=path（图片）
        """|<video src="([^"]+)"[^>]*>([^<]*)</video>""" + // 3=path 4=name（视频）
        """|<audio src="([^"]+)"[^>]*>([^<]*)</audio>""" + // 5=path 6=name（音频）
        """|\[([^\]]+)]\((/[^)]+)\)"""                     // 7=name 8=path（本地文件链接）
)

private fun parseInlineAttachments(content: String): List<InlineSegment> {
    val segments = mutableListOf<InlineSegment>()
    var last = 0
    inlineAttachmentRegex.findAll(content).forEach { m ->
        val before = content.substring(last, m.range.first).trim('\n')
        if (before.isNotBlank()) segments += InlineSegment.Text(before)
        val g = m.groupValues
        when {
            g[2].isNotEmpty() -> segments += InlineSegment.Image(g[2], g[1].ifBlank { g[2].substringAfterLast('/') })
            g[3].isNotEmpty() -> segments += InlineSegment.Video(g[3], g[4].ifBlank { g[3].substringAfterLast('/') })
            g[5].isNotEmpty() -> segments += InlineSegment.Audio(g[5], g[6].ifBlank { g[5].substringAfterLast('/') })
            g[8].isNotEmpty() -> segments += InlineSegment.FileLink(g[8], g[7])
        }
        last = m.range.last + 1
    }
    val tail = content.substring(last).trim('\n')
    if (tail.isNotBlank()) segments += InlineSegment.Text(tail)
    return segments
}

/** 从正文中提取已被内联标记引用的附件路径集合（附件区去重用）。 */
private fun extractReferencedPaths(content: String): Set<String> =
    inlineAttachmentRegex.findAll(content).mapNotNull { m ->
        val g = m.groupValues
        when {
            g[2].isNotEmpty() -> g[2]
            g[3].isNotEmpty() -> g[3]
            g[5].isNotEmpty() -> g[5]
            g[8].isNotEmpty() -> g[8]
            else -> null
        }
    }.toSet()

/** 阅读态内联音频行（MediaPlayer 点击播放/暂停，与附件区共用逻辑）。 */
@Composable
private fun InlineAudioRow(path: String, name: String, scheme: androidx.compose.material3.ColorScheme) {
    val ctx = LocalContext.current
    var playingPath by remember { mutableStateOf<String?>(null) }
    val player = remember { android.media.MediaPlayer() }
    DisposableEffect(Unit) {
        onDispose { runCatching { player.release() } }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (playingPath == path) {
                    runCatching { player.pause() }
                    playingPath = null
                } else {
                    runCatching {
                        player.reset()
                        player.setDataSource(path)
                        player.prepare()
                        player.start()
                        playingPath = path
                    }.onFailure {
                                            android.widget.Toast.makeText(ctx, ctx.getString(R.string.edit_073), android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                        ) {
                            Icon(Icons.Default.VolumeUp, null, tint = scheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(name, maxLines = 1, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface, modifier = Modifier.weight(1f))
                            Text(
                                if (playingPath == path) "⏸" else "▶",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (playingPath == path) scheme.primary else scheme.onSurfaceVariant
                            )
                        }
                    }

// ==================== 链接高亮（阅读模式 URL 识别）与附件查看 ====================

/** 识别正文中的 URL / 邮箱，生成带 LinkAnnotation 的 AnnotatedString（阅读模式点击跳转）。 */
private val urlRegex = Regex("""(https?://[^\s，。；、！？「」『』（）【】""'<>]+|www\.[^\s，。；、！？「」『』（）【】""'<>]+)""")

private fun buildLinkAnnotatedString(text: String, linkColor: Color): androidx.compose.ui.text.AnnotatedString =
    buildAnnotatedString {
        append(text)
        urlRegex.findAll(text).forEach { m ->
            var url = m.value
            if (url.startsWith("www.")) url = "https://$url"
            addLink(
                LinkAnnotation.Url(
                    url = url,
                    styles = TextLinkStyles(
                        style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
                    )
                ),
                start = m.range.first,
                end = m.range.last + 1
            )
        }
    }

// ==================== 代码语法高亮（阅读模式 codeMode：关键字/字符串/注释/数字 分色） ====================
private val codeKeywordRegex = Regex(
    "\\b(private|public|protected|internal|fun|val|var|class|object|interface|when|if|else|for|while|return|import|package|try|catch|finally|throw|new|null|true|false|this|super|abstract|open|override|companion|data|sealed|enum|typealias|init|constructor|do|break|continue|let|run|apply|also|with|suspend|inline|reified|const|lateinit|in|out|is|as)\\b"
)
private val codeStringRegex = Regex("\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'")
private val codeCommentRegex = Regex("//[^\\n]*|/\\*[\\s\\S]*?\\*/")
private val codeNumberRegex = Regex("\\b\\d+(\\.\\d+)?\\b")

/** 代码语法高亮：注释(tertiary)、字符串(tertiary)、关键字(primary)、数字(secondary)。 */
private fun buildCodeHighlightAnnotatedString(
    text: String,
    scheme: androidx.compose.material3.ColorScheme
): androidx.compose.ui.text.AnnotatedString = buildAnnotatedString {
    append(text)
    // 注释（优先，避免关键字/字符串命中注释内）
    codeCommentRegex.findAll(text).forEach { m ->
        addStyle(SpanStyle(color = scheme.tertiary), m.range.first, m.range.last + 1)
    }
    // 字符串
    codeStringRegex.findAll(text).forEach { m ->
        addStyle(SpanStyle(color = scheme.tertiary), m.range.first, m.range.last + 1)
    }
    // 关键字
    codeKeywordRegex.findAll(text).forEach { m ->
        addStyle(SpanStyle(color = scheme.primary), m.range.first, m.range.last + 1)
    }
    // 数字
    codeNumberRegex.findAll(text).forEach { m ->
        addStyle(SpanStyle(color = scheme.secondary), m.range.first, m.range.last + 1)
    }
}

/**
 * 编辑态代码语法高亮（等效 Kori MarkdownTransformation 的 OutputTransformation）：
 * BasicTextField 旧 API 用 VisualTransformation 包装 buildCodeHighlightAnnotatedString，
 * 只改样式不改文本，OffsetMapping.Identity 保证光标/选区偏移正确。
 */
private class CodeHighlightTransformation(
    private val scheme: androidx.compose.material3.ColorScheme
) : VisualTransformation {
    override fun filter(text: androidx.compose.ui.text.AnnotatedString): TransformedText =
        TransformedText(
            buildCodeHighlightAnnotatedString(text.text, scheme),
            OffsetMapping.Identity
        )
}

/** 阅读模式附件查看：图片缩略图 + 视频/文件/音频点击用系统应用打开。 */
@Composable
private fun ReadModeAttachments(
    images: List<String>,
    videos: List<String>,
    files: List<Attachment>,
    audios: List<String>,
    onOpenFile: (String) -> Unit
) {
    if (images.isEmpty() && videos.isEmpty() && files.isEmpty() && audios.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(
            stringResource(R.string.edit_003),
            style = MaterialTheme.typography.labelLarge,
            color = scheme.primary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )
        // 图片：内联大图（阅读态预览，不跳外部）
        if (images.isNotEmpty()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                images.forEach { path ->
                    AsyncImage(
                        model = File(path),
                        contentDescription = stringResource(R.string.edit_058),
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        // 视频：内联播放器（220dp 圆角 Card + 文件名标签），点击播放/暂停/重试
        if (videos.isNotEmpty()) {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                videos.forEach { path ->
                    key(path) {
                        InlineVideoPlayer(
                            modifier = Modifier.fillMaxWidth(),
                            videoPath = path,
                            fileName = path.substringAfterLast('/'),
                            scheme = scheme
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        // 音频：内联播放（MediaPlayer 点击播放/暂停，不跳外部）
        if (audios.isNotEmpty()) {
            val ctx = LocalContext.current
            var playingPath by remember { mutableStateOf<String?>(null) }
            val player = remember { android.media.MediaPlayer() }
            DisposableEffect(Unit) {
                onDispose { runCatching { player.release() } }
            }
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                audios.forEach { path ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (playingPath == path) {
                                    runCatching { player.pause() }
                                    playingPath = null
                                } else {
                                    runCatching {
                                        player.reset()
                                        player.setDataSource(path)
                                        player.prepare()
                                        player.start()
                                        playingPath = path
                                    }.onFailure {
                                        android.widget.Toast.makeText(ctx, ctx.getString(R.string.edit_073), android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        Icon(
                            if (playingPath == path) Icons.Default.Pause else Icons.Default.PlayArrow,
                            null,
                            tint = scheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            path.substringAfterLast('/'),
                            maxLines = 1,
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        // 文件：点开跳外部（文档类无法内联，唯一需要外部跳转的类型）
        if (files.isNotEmpty()) {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                files.forEach { f ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenFile(f.path) }
                            .padding(vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.InsertDriveFile, null, tint = scheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(f.name, maxLines = 1, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.OpenInNew, stringResource(R.string.web_010), tint = scheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ==================== 内联视频播放器 (对齐 Kori：WebView + HTML <video controls>) ====================

private fun formatMs(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
private fun InlineVideoPlayer(
    modifier: Modifier = Modifier,
    videoPath: String,
    fileName: String,
    scheme: androidx.compose.material3.ColorScheme,
    onRemove: (() -> Unit)? = null
) {
    // 视频播放实现要点（这是让 WebView 能真正播起来的关键）：
    //  WebView 的 <video> 由 Chromium 媒体栈驱动，必须走 HTTP Range(206) 分段请求，
    //  否则读不到元数据、无法 seek，表现就是“点了没反应/黑屏”。
    //  androidx WebViewAssetLoader 的 PathHandler.handle(path) 拿不到请求头，无法实现 Range，
    //  因此这里不用 AssetLoader，改为在 WebViewClient.shouldInterceptRequest 里
    //  读取 requestHeaders 的 Range 头，自行返回 206 局部响应。
    val ctx = LocalContext.current
    val videoFile = remember(videoPath) { File(videoPath) }
    var failed by remember(videoPath) { mutableStateOf(!videoFile.exists()) }
    val domain = "appassets.androidplatform.net"

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
            .aspectRatio(16f / 9f)
    ) {
        if (!failed) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { vctx ->
                    WebView(vctx).apply {
                        setBackgroundColor(android.graphics.Color.BLACK)
                        isVerticalScrollBarEnabled = false
                        isHorizontalScrollBarEnabled = false
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            allowFileAccess = true
                            allowContentAccess = true
                            mediaPlaybackRequiresUserGesture = false
                            useWideViewPort = true
                            loadWithOverviewMode = false
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView,
                                request: android.webkit.WebResourceRequest
                            ): android.webkit.WebResourceResponse? {
                                val u = request.url ?: return null
                                if (u.host != domain) return null
                                val p = u.path ?: return null
                                val prefix = "/media/"
                                if (!p.startsWith(prefix)) return null
                                val name = p.removePrefix(prefix)
                                if (name.isEmpty() || name.contains("..")) return null
                                val f = File(videoFile.parentFile, name)
                                if (!f.isFile) return null
                                return serveFileWithRange(f, request.requestHeaders)
                            }

                            override fun onReceivedError(
                                view: WebView,
                                request: android.webkit.WebResourceRequest,
                                error: android.webkit.WebResourceError
                            ) {
                                // 只处理主文档级错误，子资源错误不应整块判死
                                if (request.isForMainFrame) failed = true
                            }
                        }
                    }
                },
                update = { wv ->
                    // 只在路径变化时重载，避免每次重组都重新 loadData 打断播放
                    val tag = wv.tag as? String
                    if (tag != videoPath) {
                        wv.tag = videoPath
                        val html = """
                            <!DOCTYPE html><html><head>
                            <meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
                            <style>
                              html,body{margin:0;padding:0;background:#000;height:100%;width:100%;overflow:hidden;}
                              video{width:100%;height:100%;object-fit:contain;display:block;background:#000;outline:none;}
                            </style></head><body>
                            <video src="https://$domain/media/${videoFile.name}"
                                   controls playsinline webkit-playsinline preload="metadata"></video>
                            </body></html>
                        """.trimIndent()
                        wv.loadDataWithBaseURL("https://$domain/", html, "text/html", "UTF-8", null)
                    }
                }
            )
        } else {
            // 文件缺失：给外开入口，避免一块黑屏无从下手
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(Icons.Default.Movie, null, tint = Color.White, modifier = Modifier.size(28.dp))
                Spacer(Modifier.height(6.dp))
                Text(fileName, color = Color.White, style = MaterialTheme.typography.labelMedium, maxLines = 2)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { openFile(ctx, videoPath) }) {
                    Text(stringResource(R.string.edit_001), color = Color.White)
                }
            }
        }

        // 文件名角标（不拦截触摸）
        Text(
            fileName,
            maxLines = 1,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.85f),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp)
                .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp)
        )
        // 编辑态移除按钮
        if (onRemove != null) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(28.dp)
                    .background(Color.Black.copy(alpha = 0.35f), CircleShape)
            ) {
                Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/**
 * 返回支持 HTTP Range 的 WebResourceResponse。
 * Chromium 媒体栈依赖 206 分段响应：首次通常请求 bytes=0-，之后按需 seek。
 * 不支持 Range 时表现为视频黑屏/点播放无反应。
 */
private fun serveFileWithRange(
    f: File,
    headers: Map<String, String>?
): android.webkit.WebResourceResponse {
    val total = f.length()
    val mime = guessMime(f.name)
    val range = headers?.entries?.firstOrNull { it.key.equals("Range", ignoreCase = true) }?.value
    val baseHeaders = mutableMapOf(
        "Accept-Ranges" to "bytes",
        "Cache-Control" to "no-store"
    )
    try {
        if (range != null && range.startsWith("bytes=")) {
            val spec = range.removePrefix("bytes=").trim()
            val parts = spec.split("-", limit = 2)
            var start = parts.getOrNull(0)?.trim()?.toLongOrNull() ?: 0L
            var end = parts.getOrNull(1)?.trim()?.toLongOrNull() ?: (total - 1)
            if (start < 0) start = 0
            if (end >= total) end = total - 1
            if (start > end || start >= total) {
                // 416 Range Not Satisfiable
                baseHeaders["Content-Range"] = "bytes */$total"
                return android.webkit.WebResourceResponse(
                    mime, null, 416, "Range Not Satisfiable", baseHeaders, java.io.ByteArrayInputStream(ByteArray(0))
                )
            }
            val len = end - start + 1
            val fis = java.io.FileInputStream(f)
            var skipped = 0L
            while (skipped < start) {
                val s = fis.skip(start - skipped)
                if (s <= 0) break
                skipped += s
            }
            val body = java.io.ByteArrayOutputStream(len.toInt().coerceAtMost(8 * 1024 * 1024))
            val buf = ByteArray(64 * 1024)
            var remaining = len
            while (remaining > 0) {
                val want = minOf(remaining, buf.size.toLong()).toInt()
                val n = fis.read(buf, 0, want)
                if (n <= 0) break
                body.write(buf, 0, n)
                remaining -= n
            }
            fis.close()
            baseHeaders["Content-Range"] = "bytes $start-$end/$total"
            baseHeaders["Content-Length"] = len.toString()
            return android.webkit.WebResourceResponse(
                mime, null, 206, "Partial Content", baseHeaders, java.io.ByteArrayInputStream(body.toByteArray())
            )
        }
        // 无 Range：返回整文件
        baseHeaders["Content-Length"] = total.toString()
        return android.webkit.WebResourceResponse(
            mime, null, 200, "OK", baseHeaders, java.io.FileInputStream(f)
        )
    } catch (t: Throwable) {
        return android.webkit.WebResourceResponse(
            mime, null, 500, "Internal Error", baseHeaders, java.io.ByteArrayInputStream(ByteArray(0))
        )
    }
}

// 按扩展名推断 MIME（AssetLoader 供给的视频需要正确类型，否则 <video> 不识别）
private fun guessMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
    "mp4", "m4v" -> "video/mp4"
    "webm" -> "video/webm"
    "mkv" -> "video/x-matroska"
    "mov" -> "video/quicktime"
    "avi" -> "video/x-msvideo"
    "3gp" -> "video/3gpp"
    "ts" -> "video/mp2t"
    "flv" -> "video/x-flv"
    "m4a" -> "audio/mp4"
    "mp3" -> "audio/mpeg"
    else -> "application/octet-stream"
}

// ==================== 链接预览卡片 (阅读模式内联 WebView , 不跳外部浏览器) ====================

@Composable
private fun LinkPreviewCard(
    url: String,
    scheme: androidx.compose.material3.ColorScheme,
    onDismiss: () -> Unit,
    onOpenBrowser: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // WebView 预览 (高度 180dp)
            AndroidView(
                factory = { vctx ->
                    WebView(vctx).apply {
                        settings.javaScriptEnabled = false
                        settings.builtInZoomControls = false
                        settings.displayZoomControls = false
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.domStorageEnabled = false
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        webViewClient = WebViewClient()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                update = { view -> view.loadUrl(url) }
            )
            // URL 栏 + 操作按钮
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    url,
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onOpenBrowser) { Text(stringResource(R.string.web_001)) }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.edit_049), tint = scheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ==================== EditorLineNumbers (Kori EditorLineNumbers.kt 复刻: Canvas 覆盖层) ====================

/**
 * Kori 风格的行号绘制器：Canvas 覆盖层, 右对齐等宽, 随正文滚动平移 (scrollProvider).
 * 当前行 alpha 1.0, 其余 0.5。宽度按最大行号位数自适应。
 */
@Composable
private fun EditorLineNumbers(
    bodyLayout: TextLayoutResult?,
    cursorOffset: Int,
    scrollProvider: () -> Int,
    modifier: Modifier = Modifier,
    monoStyle: TextStyle = MaterialTheme.typography.bodyLarge.copy(
        fontFamily = FontFamily.Monospace,
        fontSize = 14.sp,
        lineHeight = 22.sp
    ),
    scheme: androidx.compose.material3.ColorScheme = MaterialTheme.colorScheme,
    showLineNumbers: Boolean = true
) {
    if (!showLineNumbers) return

    val density = androidx.compose.ui.platform.LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val text = bodyLayout?.layoutInput?.text?.text ?: ""

    // 逻辑行位置 (line start index -> line top position)，仅行首为 '\n' 的逻辑行
    val actualLinePositions = remember(bodyLayout) {
        val lr = bodyLayout ?: return@remember emptyList<Pair<Int, Float>>()
        if (lr.lineCount == 0) return@remember emptyList()
        buildList {
            for (i in 0 until lr.lineCount) {
                val start = lr.getLineStart(i)
                if (i == 0 || (start > 0 && text[start - 1] == '\n')) {
                    add(i to lr.getLineTop(i))
                }
            }
        }
    }

    // 宽度自适应：最大逻辑行数的位数 +1 位，用 "8" 测宽（同步测量一次，避免 derivedStateOf 内嵌 remember 导致不更新）
    val requiredWidth = remember(actualLinePositions.size, monoStyle, density) {
        val maxLineNumber = maxOf(1, actualLinePositions.size)
        val maxDigitsText = "8".repeat(maxLineNumber.toString().length + 1)
        with(density) {
            textMeasurer.measure(maxDigitsText, monoStyle, maxLines = 1).size.width.toDp()
        }
    }

    Canvas(
        modifier
            .width(requiredWidth)
            .padding(horizontal = 4.dp)
            .clipToBounds()
            .fillMaxHeight()
    ) {
        if (actualLinePositions.isEmpty()) return@Canvas
        val scrollOffset = scrollProvider().toFloat()
        val cursorLine = bodyLayout?.getLineForOffset(cursorOffset.coerceIn(0, text.length)) ?: -1

        // 二分定位视口首行
        val firstVisibleIndex = actualLinePositions
            .binarySearchBy(scrollOffset) { it.second }
            .let { if (it < 0) -(it + 1) else it }
            .coerceAtMost(actualLinePositions.lastIndex)
            .let { (it - 1).coerceAtLeast(0) }

        for (index in firstVisibleIndex until actualLinePositions.size) {
            val (lineIndex, top) = actualLinePositions[index]
            val lineY = top - scrollOffset
            if (lineY > size.height) break

            val lineNumberText = (index + 1).toString()
            val layout = textMeasurer.measure(lineNumberText, monoStyle, maxLines = 1)
            val lineHeight = layout.size.height
            if (lineY + lineHeight > 0) {
                drawText(
                    textLayoutResult = layout,
                    color = scheme.onSurfaceVariant,
                    alpha = if (lineIndex == cursorLine) 1.0f else 0.5f,
                    topLeft = Offset(
                        x = size.width - layout.size.width, // 右对齐
                        y = lineY
                    )
                )
            }
        }
    }
}


// ==================== Todo 清单视图（1:1 复刻 Kori TodoViewer / TodoCard / TodoFormat） ====================

/** Kori TodoItem：解析后的单条任务。 */
private data class TodoItem(
    val content: String,
    val isDone: Boolean,
    val priority: Char?,        // A~Z
    val creationDate: String?,  // yyyy-MM-dd
    val completionDate: String?,
    val range: IntRange         // 原文本中的范围
)

/** Kori TodoFormat：Todo.txt 语法正则与配色。 */
private object TodoFormat {
    val doneRegex = Regex("""^x\s""")
    val priorityRegex = Regex("""^\(([A-Z])\)""")
    val dateRegex = Regex("""(?<=^|\s)(\d{4}-\d{2}-\d{2})(?=\s|$)""")
    val doneLineRegex =
        Regex("""^x\s(?:\((?<pri>[A-Z])\)\s)?(?<completion>\d{4}-\d{2}-\d{2})(?:\s(?<creation>\d{4}-\d{2}-\d{2}))?""")
    val contextRegex = Regex("""(?:^|\s)(@[\w\p{L}\p{N}\u4e00-\u9fff]+)""")
    val projectRegex = Regex("""(?:^|\s)(\+[\w\p{L}\p{N}\u4e00-\u9fff]+)""")
    val metaRegex = Regex("""(?:^|\s)([\w\p{L}\p{N}\u4e00-\u9fff]+:[\w\p{L}\p{N}\u4e00-\u9fff]+)""")

    val contextStyle = SpanStyle(color = Color(0xFF009688)) // context: 青色
    val projectStyle = SpanStyle(color = Color(0xFFEC407A)) // project: 粉色
    val metaStyle = SpanStyle(color = Color(0xFF8E24AA))    // metadata: 紫色

    // A-Z 优先级颜色表（照抄 Kori priorityColors）
    val priorityColors = intArrayOf(
        0xFFD32F2F.toInt(), 0xFFF57C00.toInt(), 0xFFFBC02D.toInt(), 0xFF388E3C.toInt(),
        0xFF1976D2.toInt(), 0xFF7B1FA2.toInt(), 0xFF0097A7.toInt(), 0xFF5D4037.toInt(),
        0xFF0288D1.toInt(), 0xFF388E3C.toInt(), 0xFF8D6E63.toInt(), 0xFF455A64.toInt(),
        0xFFAFB42B.toInt(), 0xFFEC407A.toInt(), 0xFF00ACC1.toInt(), 0xFF43A047.toInt(),
        0xFF6D4C41.toInt(), 0xFF7E57C2.toInt(), 0xFF26A69A.toInt(), 0xFF9E9D24.toInt(),
        0xFF8E24AA.toInt(), 0xFF3949AB.toInt(), 0xFFD84315.toInt(), 0xFF00897B.toInt(),
        0xFF6D4C41.toInt(), 0xFFBDBDBD.toInt()
    )
}

/** Kori TodoDefaults.processTodo：解析 + 排序（待办按优先级→日期，完成按日期→优先级）。 */
private fun parseTodo(content: String): Pair<List<TodoItem>, List<TodoItem>> {
    val undone = mutableListOf<TodoItem>()
    val done = mutableListOf<TodoItem>()
    var offset = 0
    content.lines().forEach { line ->
        val trimmed = line.trim()
        if (trimmed.isNotEmpty()) {
            val range = offset until (offset + line.length)
            val isDone = TodoFormat.doneRegex.containsMatchIn(trimmed)
            var c = trimmed
            var priority: Char? = null
            var creation: String? = null
            var completion: String? = null
            if (isDone) {
                val doneMatch = TodoFormat.doneLineRegex.find(trimmed)
                if (doneMatch != null) {
                    priority = doneMatch.groups["pri"]?.value?.firstOrNull()
                    completion = doneMatch.groups["completion"]?.value
                    creation = doneMatch.groups["creation"]?.value
                    c = c.substring(doneMatch.range.last + 1).trimStart()
                } else {
                    c = c.removePrefix("x ").trimStart()
                }
            } else {
                TodoFormat.priorityRegex.find(c)?.let {
                    priority = it.groupValues[1].firstOrNull()
                    c = c.substring(it.range.last + 1).trimStart()
                }
                TodoFormat.dateRegex.find(c)?.let {
                    creation = it.value
                    completion = it.value
                    c = c.replaceFirst(it.value, "").trim()
                }
            }
            val item = TodoItem(c, isDone, priority, creation, completion, range)
            if (isDone) done.add(item) else undone.add(item)
        }
        offset += line.length + 1
    }
    val undoneSorted = undone.sortedWith(
        compareBy<TodoItem> { it.priority?.let { p -> p - 'A' } ?: 26 }
            .thenBy { it.completionDate ?: "9999-99-99" }
    )
    val doneSorted = done.sortedWith(
        compareBy<TodoItem> { it.completionDate ?: "9999-99-99" }
            .thenBy { it.priority?.let { p -> p - 'A' } ?: 26 }
    )
    return undoneSorted to doneSorted
}

/** Kori TodoDefaults.extractFilters：提取全部 @上下文 / +项目 / key:value 元数据。 */
private fun extractTodoTokens(content: String, regex: Regex): Set<String> =
    content.lines().flatMap { line ->
        val t = line.trim()
        if (t.isEmpty()) emptyList()
        else regex.findAll(t).map { it.groupValues[1].trim() }
            .filter { v -> !(v.startsWith("@") || v.startsWith("+")) || regex != TodoFormat.metaRegex }
            .toList()
    }.toSet()

/** Kori TodoViewer：清单视图（分区头 + 滑动卡片 + 过滤 chips）。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TodoViewerView(
    content: String,
    onContentChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val (undoneItems, doneItems) = remember(content) { parseTodo(content) }
    val availableContexts = remember(content) { extractTodoTokens(content, TodoFormat.contextRegex) }
    val availableProjects = remember(content) { extractTodoTokens(content, TodoFormat.projectRegex) }
    val availableMetadata = remember(content) { extractTodoTokens(content, TodoFormat.metaRegex) }
    val activeContexts = remember { mutableStateListOf<String>() }
    val activeProjects = remember { mutableStateListOf<String>() }
    val activeMetadata = remember { mutableStateListOf<String>() }
    var showFilters by remember { mutableStateOf(false) }

    fun matches(item: TodoItem): Boolean {
        val contextMatch = activeContexts.isEmpty() ||
            activeContexts.any { ctx -> TodoFormat.contextRegex.findAll(item.content).any { it.value.trim() == ctx } }
        val projectMatch = activeProjects.isEmpty() ||
            activeProjects.any { proj -> TodoFormat.projectRegex.findAll(item.content).any { it.value.trim() == proj } }
        val metaMatch = activeMetadata.isEmpty() ||
            activeMetadata.any { meta -> TodoFormat.metaRegex.findAll(item.content).any { it.value.trim() == meta } }
        return contextMatch && projectMatch && metaMatch
    }

    val filteredUndone = if (activeContexts.isEmpty() && activeProjects.isEmpty() && activeMetadata.isEmpty())
        undoneItems else undoneItems.filter { matches(it) }
    val filteredDone = if (activeContexts.isEmpty() && activeProjects.isEmpty() && activeMetadata.isEmpty())
        doneItems else doneItems.filter { matches(it) }

    // 行首切换完成标记（对齐 Kori toggleLineStart("x ")）
    fun toggleDoneState(range: IntRange) {
        val text = content
        val end = (range.last + 1).coerceAtMost(text.length)
        val line = text.substring(range.first, end)
        val newLine = if (line.startsWith("x ") || line.startsWith("X ")) line.substring(2) else "x " + line
        onContentChange(text.substring(0, range.first) + newLine + text.substring(end))
    }

    // 删除整行（对齐 Kori delete(range)，含行尾换行）
    fun deleteLine(range: IntRange) {
        val text = content
        val end = (range.last + 1).coerceAtMost(text.length)
        val deleteUntil = if (text.getOrNull(end) == '\n') end + 1 else end
        onContentChange(text.substring(0, range.first) + text.substring(deleteUntil.coerceAtMost(text.length)))
    }

    Column(modifier) {
        LazyColumn(Modifier.weight(1f)) {
            if (filteredUndone.isNotEmpty()) {
                stickyHeader(key = "Header Todo") {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillParentMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp, top = 4.dp)
                        ) {
                            Text(
                                stringResource(R.string.edit_076),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                "${filteredUndone.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                items(filteredUndone, key = { it.hashCode() }) { item ->
                    SwipeableTodoCard(
                        modifier = Modifier
                            .fillParentMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 8.dp),
                        todoItem = item,
                        onDelete = { deleteLine(item.range) },
                        toggleDoneState = { toggleDoneState(item.range) }
                    )
                }
            }
            if (filteredDone.isNotEmpty()) {
                stickyHeader(key = "Header Done") {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillParentMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                        ) {
                            Text(
                                stringResource(R.string.edit_077),
                                color = MaterialTheme.colorScheme.secondary,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                "${filteredDone.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                items(filteredDone, key = { it.hashCode() }) { item ->
                    SwipeableTodoCard(
                        modifier = Modifier
                            .fillParentMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 8.dp),
                        todoItem = item,
                        onDelete = { deleteLine(item.range) },
                        toggleDoneState = { toggleDoneState(item.range) }
                    )
                }
            }
        }

        // 分割线中央悬浮过滤按钮（对齐 Kori HorizontalDivider + FilterAlt IconButton）
        Box(contentAlignment = Alignment.Center) {
            HorizontalDivider()
            IconButton(
                modifier = Modifier.size(32.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                onClick = { showFilters = !showFilters }
            ) {
                Icon(
                    if (showFilters) Icons.Outlined.FilterAltOff else Icons.Outlined.FilterAlt,
                    contentDescription = null
                )
            }
        }

        AnimatedVisibility(showFilters) {
            TodoFilterChips(
                availableContexts = availableContexts,
                availableProjects = availableProjects,
                availableMetadata = availableMetadata,
                activeContexts = activeContexts,
                activeProjects = activeProjects,
                activeMetadata = activeMetadata,
                onContextClick = { if (it in activeContexts) activeContexts.remove(it) else activeContexts.add(it) },
                onProjectClick = { if (it in activeProjects) activeProjects.remove(it) else activeProjects.add(it) },
                onMetadataClick = { if (it in activeMetadata) activeMetadata.remove(it) else activeMetadata.add(it) }
            )
        }
    }
}

/** Kori TodoFilterChips：三组过滤 chips（上下文青 / 项目粉 / 元数据紫）。 */
@Composable
private fun TodoFilterChips(
    availableContexts: Set<String>,
    availableProjects: Set<String>,
    availableMetadata: Set<String>,
    activeContexts: MutableList<String>,
    activeProjects: MutableList<String>,
    activeMetadata: MutableList<String>,
    onContextClick: (String) -> Unit,
    onProjectClick: (String) -> Unit,
    onMetadataClick: (String) -> Unit
) {
    if (availableContexts.isEmpty() && availableProjects.isEmpty() && availableMetadata.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        if (availableContexts.isNotEmpty()) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp)) {
                items(availableContexts.toList()) { context ->
                    FilterChip(
                        selected = activeContexts.contains(context),
                        onClick = { onContextClick(context) },
                        label = { Text(context, maxLines = 1) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF009688).copy(alpha = 0.1f)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = true,
                            selectedBorderColor = Color(0xFF009688)
                        ),
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                }
            }
        }
        if (availableProjects.isNotEmpty()) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp)) {
                items(availableProjects.toList()) { project ->
                    FilterChip(
                        selected = activeProjects.contains(project),
                        onClick = { onProjectClick(project) },
                        label = { Text(project, maxLines = 1) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFEC407A).copy(alpha = 0.1f)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = true,
                            selectedBorderColor = Color(0xFFEC407A)
                        ),
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                }
            }
        }
        if (availableMetadata.isNotEmpty()) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp)) {
                items(availableMetadata.toList()) { metadata ->
                    FilterChip(
                        selected = activeMetadata.contains(metadata),
                        onClick = { onMetadataClick(metadata) },
                        label = { Text(metadata, maxLines = 1) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF8E24AA).copy(alpha = 0.1f)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = true,
                            selectedBorderColor = Color(0xFF8E24AA)
                        ),
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                }
            }
        }
    }
}

/** Kori SwipeableCard：右滑删除（红底 Delete）、左滑切完成（灰底 CheckBox），松手回弹。 */
@Composable
private fun SwipeableTodoCard(
    modifier: Modifier,
    todoItem: TodoItem,
    onDelete: () -> Unit,
    toggleDoneState: () -> Unit
) {
    val scope = rememberCoroutineScope()
    // material3 1.3.0 无 onDismiss 参数，用 confirmValueChange 等效实现（对齐 Kori onDismiss 语义）
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> { onDelete(); true }
                SwipeToDismissBoxValue.StartToEnd -> { toggleDoneState(); true }
                else -> false
            }
        }
    )
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            when (state.dismissDirection) {
                SwipeToDismissBoxValue.EndToStart -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.errorContainer, CardDefaults.shape),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.edit_078),
                            modifier = Modifier.padding(end = 12.dp).size(20.dp),
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceContainer, CardDefaults.shape),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Icon(
                            if (todoItem.isDone) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                            contentDescription = if (todoItem.isDone) stringResource(R.string.edit_079) else stringResource(R.string.edit_080),
                            modifier = Modifier.padding(start = 12.dp).size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {}
            }
        },
        modifier = modifier,
        content = { TodoCard(todoItem, toggleDoneState) }
    )
}

/** Kori TodoCard：优先级徽章 / CheckCircle + 高亮正文 + 日期行（小册子质感：左侧色条 + 精致圆角）。 */
@Composable
private fun TodoCard(todoItem: TodoItem, toggleDoneState: () -> Unit) {
    val priorityColor = todoItem.priority?.let { c ->
        Color(TodoFormat.priorityColors[(c - 'A').coerceIn(TodoFormat.priorityColors.indices)])
    } ?: MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)

    val contentColor = if (todoItem.isDone) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val textDecoration = if (todoItem.isDone) TextDecoration.LineThrough else null

    val dateLabel = if (todoItem.isDone) todoItem.completionDate?.let { stringResource(R.string.edit_res_007, it) }
        ?: stringResource(R.string.edit_081)
    else todoItem.creationDate?.let { stringResource(R.string.edit_res_008, it) }

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (todoItem.isDone)
                MaterialTheme.colorScheme.surfaceContainerLow
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { toggleDoneState() },
                contentAlignment = Alignment.Center
            ) {
                if (todoItem.isDone) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = stringResource(R.string.edit_081),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    // 优先级徽章：字母圆角块（A/B/C...），无优先级用空心圈
                    if (todoItem.priority != null) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(priorityColor.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                todoItem.priority.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = priorityColor
                            )
                        }
                    } else {
                        Icon(
                            Icons.Filled.Circle,
                            contentDescription = stringResource(R.string.edit_080),
                            tint = priorityColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = buildAnnotatedString {
                        val text = todoItem.content
                        append(text)
                        TodoFormat.contextRegex.findAll(text).forEach {
                            addStyle(TodoFormat.contextStyle, it.range.first, it.range.last + 1)
                        }
                        TodoFormat.projectRegex.findAll(text).forEach {
                            addStyle(TodoFormat.projectStyle, it.range.first, it.range.last + 1)
                        }
                        TodoFormat.metaRegex.findAll(text).forEach {
                            val v = it.groupValues[1]
                            if (!v.startsWith("@") && !v.startsWith("+")) {
                                addStyle(TodoFormat.metaStyle, it.range.first, it.range.last + 1)
                            }
                        }
                    },
                    style = MaterialTheme.typography.bodyLarge.copy(textDecoration = textDecoration),
                    color = contentColor
                )
                if (dateLabel != null) {
                    Text(
                        dateLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun queryName(context: android.content.Context, uri: Uri): String? {
    return runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    }.getOrNull()
}

// ============================ 搜索替换（对齐 Kori FindAndReplaceField） ============================

/**
 * 搜索替换栏：窄屏纵向布局（对齐 Kori Column 分支），查找行 + 替换行。
 * 查找行 = 查找输入框(计数 suffix + 自动聚焦) + 上一处/下一处；替换行 = 替换输入框 + 替换/全部替换。
 */
@Composable
private fun FindAndReplaceField(
    searchWord: String,
    onSearchWord: (String) -> Unit,
    replaceWord: String,
    onReplaceWord: (String) -> Unit,
    positionText: String,
    findFocusRequester: FocusRequester,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onReplaceCurrent: () -> Unit,
    onReplaceAll: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, bottom = 4.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SearchTextField(
                value = searchWord,
                onValueChange = onSearchWord,
                leadingIcon = Icons.Outlined.LocationSearching,
                placeholder = stringResource(R.string.edit_082),
                modifier = Modifier.padding(end = 4.dp).weight(1f).focusRequester(findFocusRequester),
                suffix = {
                    if (positionText.isNotEmpty()) {
                        Text(
                            modifier = Modifier.padding(end = 8.dp),
                            text = positionText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            )
            IconButton(onClick = onPrevious) { Icon(Icons.Default.ArrowUpward, contentDescription = stringResource(R.string.edit_083)) }
            IconButton(onClick = onNext) { Icon(Icons.Default.ArrowDownward, contentDescription = stringResource(R.string.edit_084)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            SearchTextField(
                value = replaceWord,
                onValueChange = onReplaceWord,
                leadingIcon = Icons.Outlined.Autorenew,
                placeholder = stringResource(R.string.edit_085),
                modifier = Modifier.padding(end = 4.dp).weight(1f)
            )
            IconButton(onClick = onReplaceCurrent) { Icon(Icons.Default.ChangeCircle, contentDescription = stringResource(R.string.edit_085)) }
            IconButton(onClick = onReplaceAll) { Icon(Icons.Default.DoneAll, contentDescription = stringResource(R.string.edit_086)) }
        }
    }
    // 打开搜索栏时自动聚焦查找框（对齐 Kori LaunchedEffect { focusRequester.requestFocus() }）
    LaunchedEffect(Unit) { runCatching { findFocusRequester.requestFocus() } }
}

/**
 * 搜索输入框：等效 Kori CustomTextField——BasicTextField 48dp + surfaceContainerHigh 圆角背景，
 * leading 图标 + placeholder(0.5) + 计数 suffix + 清空钮；material3 1.3.0 用 Surface 自绘替代 DecorationBox。
 */
@Composable
private fun SearchTextField(
    value: String,
    onValueChange: (String) -> Unit,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    placeholder: String,
    modifier: Modifier = Modifier,
    suffix: (@Composable () -> Unit)? = null
) {
    val focusManager = LocalFocusManager.current
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = androidx.compose.ui.text.input.ImeAction.Done
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onDone = { focusManager.clearFocus() }
            ),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth().height(48.dp),
            decorationBox = { inner ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 12.dp, end = 4.dp)
                ) {
                    Icon(leadingIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            )
                        }
                        inner()
                    }
                    suffix?.invoke()
                    if (value.isNotBlank()) {
                        IconButton(
                            onClick = { onValueChange("") },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.edit_087))
                        }
                    }
                }
            }
        )
    }
}

/**
 * 编辑类型三选一分段切换（文本 / 待办 / 代码）。
 *
 * 对齐 Kori NoteTypeDialog 的理念：三种编辑模式并列直观选择。
 * 竖向排列，适配右侧概览侧栏的窄操作栏；选中项高亮底色 + 主色图标。
 */
@Composable
private fun NoteTypeSegment(
    current: Int,
    onSelect: (Int) -> Unit
) {
    val items = listOf(
        Triple(0, Icons.Default.Notes, stringResource(R.string.edit_043)),
        Triple(1, Icons.Default.Checklist, stringResource(R.string.edit_042)),
        Triple(2, Icons.Default.Code, stringResource(R.string.edit_045))
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        items.forEach { (idx, icon, label) ->
            val selected = current == idx
            Box(
                Modifier
                    .padding(4.dp)
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        else Color.Transparent
                    )
                    .clickable { onSelect(idx) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = if (selected) MaterialTheme.colorScheme.primary
                    else LocalContentColor.current
                )
            }
        }
    }
}
