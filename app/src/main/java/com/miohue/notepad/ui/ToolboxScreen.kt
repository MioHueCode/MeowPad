package com.miohue.notepad.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.automirrored.outlined.ArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.miohue.notepad.data.Note
import com.miohue.notepad.data.NoteRepository
import androidx.compose.ui.res.stringResource
import com.miohue.notepad.R

/**
 * 工具箱 —— 1:1 复刻 Kori ToolboxPage 模式：
 * Box(TopCenter) > Column(widthIn 600, h16, verticalScroll, CenterHorizontally)
 * 卡片1「添加示例笔记」trailing = SplitButton（Tonal 药丸：NoteAdd+类型文本 | 旋转箭头）+ DropdownMenu(选中 Check)
 * 卡片2「打开本地文件」+ ArrowRight（系统文件选择器）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolboxScreen(
    repo: NoteRepository,
    onBack: () -> Unit,
    onOpenFile: (String) -> Unit
) {
    val context = LocalContext.current
    // SplitButton：checked=菜单展开（对齐 Kori checked + 180° 旋转）
    var checked by remember { mutableStateOf(false) }
    var type by remember { mutableStateOf("text") } // text | todo
    val rotation by animateFloatAsState(if (checked) 180f else 0f)

    // 「打开本地文件」系统文件选择器（对齐 Kori PlatformFilePicker）
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            runCatching {
                val name = context.contentResolver.queryName(uri) ?: "file"
                val dst = java.io.File(context.cacheDir, name)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    dst.outputStream().use { input.copyTo(it) }
                }
                onOpenFile(dst.absolutePath)
            }
        }
    }

    fun toast(msg: String) =
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()

    Scaffold(

        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.list_033), color = MaterialTheme.colorScheme.onSurface) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.web_002))
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(16.dp))

                // 卡片1：添加示例笔记（对齐 Kori ListItem + SplitButtonLayout + DropdownMenu）
                ListItem(
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .clip(CardDefaults.shape),
                    colors = ListItemDefaults.colors(
                        containerColor = CardDefaults.elevatedCardColors().containerColor
                    ),
                    headlineContent = { Text(stringResource(R.string.tool_001)) },
                    trailingContent = {
                        Box {
                            // SplitButton（material3 1.3 无 SplitButtonLayout，用双 Surface 拼同款）
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // leading：NoteAdd + 当前类型文本
                                Surface(
                                    onClick = {
                                        repo.save(sampleNote(context, type))
                                        toast(context.getString(R.string.tool_002))
                                    },
                                    shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp),
                                    color = ButtonDefaults.buttonColors()
                                        .containerColor
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .heightIn(min = 32.dp)
                                            .padding(horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.NoteAdd,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            if (type == "todo") stringResource(R.string.tool_016) else stringResource(R.string.tool_003),
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                }
                                Spacer(Modifier.size(2.dp))
                                // trailing：旋转箭头
                                Surface(
                                    onClick = { checked = !checked },
                                    shape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp),
                                    color = ButtonDefaults.buttonColors()
                                        .containerColor
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .heightIn(min = 32.dp)
                                            .padding(horizontal = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Filled.KeyboardArrowDown,
                                            contentDescription = stringResource(R.string.tool_004),
                                            modifier = Modifier
                                                .size(18.dp)
                                                .rotate(rotation)
                                        )
                                    }
                                }
                            }

                            DropdownMenu(
                                expanded = checked,
                                onDismissRequest = { checked = false },
                                offset = DpOffset(x = 48.dp, y = 0.dp)
                            ) {
                                // material3 1.3.0 无 selected/selectedLeadingIcon，用 leadingIcon + Check 等效
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.tool_003)) },
                                    leadingIcon = {
                                        if (type == "text") Icon(Icons.Filled.Check, null) else null
                                    },
                                    onClick = { type = "text"; checked = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Todo") },
                                    leadingIcon = {
                                        if (type == "todo") Icon(Icons.Filled.Check, null) else null
                                    },
                                    onClick = { type = "todo"; checked = false }
                                )
                            }
                        }
                    }
                )

                // 卡片2：打开本地文件（对齐 Kori「编辑本地文件」+ ArrowRight）
                Column(
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .clip(CardDefaults.shape)
                        .background(CardDefaults.elevatedCardColors().containerColor)
                ) {
                    ListItem(
                        modifier = Modifier
                            .clip(CardDefaults.shape)
                            .clickable { filePicker.launch("*/*") },
                        colors = ListItemDefaults.colors(
                            containerColor = CardDefaults.elevatedCardColors().containerColor
                        ),
                        headlineContent = { Text(stringResource(R.string.tool_005)) },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowRight,
                                contentDescription = null
                            )
                        }
                    )
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // 选中文件后确认打开（缓存路径）
    // 注：文件已复制到缓存并通过 onOpenFile 打开，无需额外对话框
}

/** 示例笔记（对齐 Kori addSampleNote）。 */
private fun sampleNote(context: android.content.Context, type: String): Note {
    val base = Note.new()
    return if (type == "todo") {
        base.copy(
            title = context.getString(R.string.tool_006),
            content = context.getString(R.string.tool_007) +
                context.getString(R.string.tool_008) +
                context.getString(R.string.tool_009),
            type = "todo"
        )
    } else {
        base.copy(
            title = context.getString(R.string.tool_010),
            content = context.getString(R.string.tool_011) +
                context.getString(R.string.tool_012) +
                context.getString(R.string.tool_013) +
                context.getString(R.string.tool_014) +
                context.getString(R.string.tool_015),
            type = "text"
        )
    }
}

private fun android.content.ContentResolver.queryName(uri: android.net.Uri): String? =
    query(uri, null, null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
    }
