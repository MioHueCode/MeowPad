package com.miohue.notepad.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.miohue.notepad.data.NoteRepository
import java.io.File
import androidx.compose.ui.res.stringResource
import com.miohue.notepad.R

private const val SCREEN_LIST = "list"
private const val SCREEN_SETTINGS = "settings"
private const val SCREEN_BACKUP = "backup"
private const val SCREEN_TRASH = "trash"
private const val SCREEN_FILES = "files"
private const val SCREEN_TOOLBOX = "toolbox"
private const val SCREEN_PASSWORD = "password"
private const val SCREEN_MEMO = "memo"
private const val PREFIX_EDITOR = "editor:"
private const val PREFIX_BROWSER = "browser:"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteApp(context: Context) {
    val prefs = remember { context.getSharedPreferences("notepad_prefs", Context.MODE_PRIVATE) }
    var mode by remember { mutableStateOf(ThemeMode.valueOf(prefs.getString("theme_mode", "SYSTEM")!!)) }
    var appColor by remember { mutableStateOf(AppColor.valueOf(prefs.getString("app_color", "DYNAMIC")!!)) }
    var isAmoled by remember { mutableStateOf(prefs.getBoolean("amoled", false)) }
    var fontScale by remember { mutableStateOf(prefs.getFloat("font_scale", 1f)) }
    var skin by remember { mutableStateOf(prefs.getString("skin", "default")!!) }
    var paperMode by remember { mutableStateOf(prefs.getBoolean("paper_mode", false)) }

    fun persistTheme() {
        prefs.edit().putString("theme_mode", mode.name).putString("app_color", appColor.name)
            .putBoolean("amoled", isAmoled).putFloat("font_scale", fontScale)
            .putString("skin", skin).putBoolean("paper_mode", paperMode).apply()
    }

    // 首次打开时请求通知权限 (Android 13+)
    val ctx = LocalContext.current as ComponentActivity
    val permLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        prefs.edit().putBoolean("perm_requested", true).apply()
    }

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val hasPerm = androidx.core.content.ContextCompat.checkSelfPermission(
                ctx, android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!hasPerm && !prefs.getBoolean("perm_requested", false)) {
                kotlinx.coroutines.delay(800)
                permLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val dark = isDark(mode)
    val scheme = remember(appColor, dark, skin, paperMode) {
        buildColorScheme(context, appColor, dark)
            .withCatSkin(skin, dark)
            .withPaperStyle(paperMode, dark)
    }
    val scaledTypography = remember(fontScale) { Typography().withScaledFontSizes(fontScale) }
    val repo = remember { NoteRepository(context) }

    // 启动时后台创建根目录标准文件夹（backup/images/files/audios），避免主线程 IO
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { repo.ensureFolders() }
        }
    }

    MaterialTheme(colorScheme = scheme, typography = scaledTypography) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides scheme.onBackground
        ) {
        Box(Modifier.fillMaxSize().background(scheme.background)) {
            val stack = remember { mutableStateListOf(SCREEN_LIST) }
            val top = stack.last()
            val onList = top == SCREEN_LIST
            var showNewTypeDialog by remember { mutableStateOf(false) }

            // 始终拦截返回/侧滑：栈底时消费事件（不退出桌面），多选/菜单由子组件处理
            androidx.activity.compose.BackHandler(enabled = true) {
                if (stack.size > 1) {
                    stack.removeAt(stack.lastIndex)
                }
                // stack.size == 1 时不做任何事，消费掉侧滑避免退出桌面
            }

            // 新建笔记类型选择（FAB 点击弹出：文本 / 待办清单 / 代码笔记）
            if (showNewTypeDialog) {
                AlertDialog(
                    onDismissRequest = { showNewTypeDialog = false },
                    title = { Text(stringResource(R.string.app_001)) },
                    text = {
                        Column {
                            listOf(
                                Triple("text", stringResource(R.string.app_002), Icons.Default.Description),
                                Triple("todo", stringResource(R.string.app_003), Icons.Default.Checklist),
                                Triple("code", stringResource(R.string.app_004), Icons.Default.Code)
                            ).forEach { (type, label, icon) ->
                                ListItem(
                                    headlineContent = { Text(label) },
                                    leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                    modifier = Modifier.clip(MaterialTheme.shapes.medium).clickable {
                                        showNewTypeDialog = false
                                        stack.add("${PREFIX_EDITOR}new:$type")
                                    }
                                )
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { showNewTypeDialog = false }) { Text(stringResource(R.string.memo_010)) }
                    }
                )
            }

            Scaffold(

                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                containerColor = Color.Transparent,
                floatingActionButton = {
                    if (onList) {
                        FloatingActionButton(
                            modifier = Modifier.navigationBarsPadding(),
                            onClick = { showNewTypeDialog = true }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.app_005))
                        }
                    }
                }
            ) { padding ->
                // 过渡动画：页面切换淡入 + 横向滑动
                AnimatedContent(
                    targetState = top,
                    transitionSpec = {
                        (fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 4 })
                            .togetherWith(fadeOut(tween(180)) + slideOutHorizontally(tween(180)) { -it / 4 })
                    },
                    label = "screen_transition",
                    modifier = Modifier.padding(padding).fillMaxSize()
                ) { route ->
                    when {
                        route == SCREEN_LIST -> NoteListScreen(
                            repo = repo, prefs = prefs,
                            onOpen = { stack.add("$PREFIX_EDITOR$it") },
                            onEdit = { stack.add("$PREFIX_EDITOR$it") },
                            onNew = { stack.add("${PREFIX_EDITOR}new") },
                            onTrash = { stack.add(SCREEN_TRASH) },
                            onSettings = { stack.add(SCREEN_SETTINGS) },
                            onOpenFiles = { stack.add(SCREEN_FILES) },
                            onToolbox = { stack.add(SCREEN_TOOLBOX) },
                            onOpenBrowser = { stack.add(PREFIX_BROWSER) },
                            onOpenUrl = { url -> stack.add("$PREFIX_BROWSER$url") },
                            onOpenFile = { path -> openFile(context, path) },
                            onPassword = { stack.add(SCREEN_PASSWORD) },
                            onMemo = { stack.add(SCREEN_MEMO) }
                        )
                        route == SCREEN_SETTINGS -> SettingsScreen(
                            prefs = prefs, repo = repo, context = context,
                            mode = mode, appColor = appColor, isAmoled = isAmoled, fontSize = fontScale,
                            skin = skin, paperMode = paperMode,
                            onMode = { mode = it; persistTheme() },
                            onColor = { appColor = it; persistTheme() },
                            onAmoled = { isAmoled = it; persistTheme() },
                            onFontSize = { fontScale = it; persistTheme() },
                            onSkin = { skin = it; persistTheme() },
                            onPaperMode = { paperMode = it; persistTheme() },
                            onBackup = { stack.add(SCREEN_BACKUP) },
                            onBack = { stack.removeAt(stack.lastIndex) }
                        )
                        route == SCREEN_BACKUP -> BackupSettingsScreen(
                            prefs = prefs, repo = repo, context = context,
                            onBack = { stack.removeAt(stack.lastIndex) }
                        )
                        route == SCREEN_TRASH -> TrashScreen(
                            repo = repo,
                            onBack = { stack.removeAt(stack.lastIndex) },
                            onOpen = { stack.add("$PREFIX_EDITOR$it") },
                            onEdit = { stack.add("$PREFIX_EDITOR$it") }
                        )
                        route == SCREEN_FILES -> {
                            FileBrowser(dir = repo.rootDir(), onBack = { stack.removeAt(stack.lastIndex) })
                        }
                        route == SCREEN_TOOLBOX -> ToolboxScreen(
                            repo = repo,
                            onBack = { stack.removeAt(stack.lastIndex) },
                            onOpenFile = { path -> openFile(context, path) }
                        )
                        route == SCREEN_PASSWORD -> PasswordScreen(
                            prefs = prefs,
                            onBack = { stack.removeAt(stack.lastIndex) }
                        )
                        route == SCREEN_MEMO -> MemoScreen(
                            prefs = prefs,
                            onBack = { stack.removeAt(stack.lastIndex) }
                        )
                        route.startsWith(PREFIX_BROWSER) -> BrowserScreen(
                            url = route.removePrefix(PREFIX_BROWSER),
                            onBack = { stack.removeAt(stack.lastIndex) }
                        )
                        route.startsWith(PREFIX_EDITOR) -> NoteEditorScreen(
                            repo = repo, id = route.removePrefix(PREFIX_EDITOR),
                            onBack = { stack.removeAt(stack.lastIndex) },
                            onSavedToDetail = { stack.removeAt(stack.lastIndex) },
                            onSavedPop = { stack.removeAt(stack.lastIndex) },
                            onOpenUrl = { url -> stack.add("$PREFIX_BROWSER$url") }
                        )
                    }
                }
            }
        }
        }
    }
}

fun openFile(context: Context, path: String) {
    runCatching {
        val file = File(path)
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val mime = android.webkit.MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.app_006)))
    }
}
