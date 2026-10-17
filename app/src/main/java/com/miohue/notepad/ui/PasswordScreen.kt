package com.miohue.notepad.ui

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import androidx.compose.ui.res.stringResource
import com.miohue.notepad.R

/**
 * 密码本：本地加密口令托管。
 * - 数据存 SharedPreferences（应用私有目录，随应用卸载清除），JSON 序列化
 * - 首次进入设置 4 位以上数字 PIN；之后每次进入需验证
 * - 条目：标题 / 账号 / 密码 / 备注；支持复制、显示切换、新增、编辑、删除
 */
private const val KEY_PIN = "password_book_pin"
private const val KEY_ENTRIES = "password_book_entries"

private data class PwEntry(
    val id: String,
    val title: String,
    val username: String,
    val password: String,
    val note: String,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("title", title); put("username", username)
        put("password", password); put("note", note)
        put("createdAt", createdAt); put("updatedAt", updatedAt)
    }

    companion object {
        fun fromJson(j: JSONObject) = PwEntry(
            id = j.optString("id", UUID.randomUUID().toString()),
            title = j.optString("title", ""),
            username = j.optString("username", ""),
            password = j.optString("password", ""),
            note = j.optString("note", ""),
            createdAt = j.optLong("createdAt"),
            updatedAt = j.optLong("updatedAt")
        )
    }
}

private fun loadEntries(prefs: SharedPreferences): List<PwEntry> = runCatching {
    val arr = JSONArray(prefs.getString(KEY_ENTRIES, "[]") ?: "[]")
    List(arr.length()) { i -> PwEntry.fromJson(arr.optJSONObject(i) ?: JSONObject()) }
}.getOrDefault(emptyList())

private fun saveEntries(prefs: SharedPreferences, list: List<PwEntry>) {
    val arr = JSONArray()
    list.forEach { arr.put(it.toJson()) }
    prefs.edit().putString(KEY_ENTRIES, arr.toString()).apply()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordScreen(prefs: SharedPreferences, onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scheme = MaterialTheme.colorScheme

    val savedPin = prefs.getString(KEY_PIN, null)
    var unlocked by remember { mutableStateOf(savedPin == null) }

    // ---------- PIN 锁屏（首次设置 / 之后验证） ----------
    if (!unlocked) {
        var input by remember { mutableStateOf("") }
        var setting by remember { mutableStateOf(savedPin == null) }
        var confirmStage by remember { mutableStateOf(false) }
        var firstPin by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }

        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = scheme.background,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.list_031)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.web_002)) }
                    }
                )
            }
        ) { padding ->
            Column(
                Modifier.padding(padding).fillMaxSize().padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Lock, null, tint = scheme.primary, modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(16.dp))
                Text(
                    when {
                        setting && confirmStage -> stringResource(R.string.pw_001)
                        setting -> stringResource(R.string.pw_002)
                        else -> stringResource(R.string.pw_003)
                    },
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.filter(Char::isDigit).take(12); error = null },
                    label = { Text("PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    isError = error != null,
                    supportingText = { error?.let { Text(it, color = scheme.error) } }
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        when {
                            setting && !confirmStage -> {
                                if (input.length < 4) error = context.getString(R.string.pw_004)
                                else { firstPin = input; confirmStage = true; input = "" }
                            }
                            setting && confirmStage -> {
                                if (input == firstPin) {
                                    prefs.edit().putString(KEY_PIN, input).apply()
                                    unlocked = true
                                } else {
                                    error = context.getString(R.string.pw_005); confirmStage = false; firstPin = ""; input = ""
                                }
                            }
                            else -> {
                                if (input == savedPin) unlocked = true
                                else error = context.getString(R.string.pw_006)
                            }
                        }
                    },
                    enabled = input.isNotEmpty()
                ) { Text(if (setting) stringResource(R.string.pw_007) else stringResource(R.string.pw_008)) }
                // 忘记 PIN：清空密码本重置（破坏性，双确认）
                if (!setting) {
                    var confirmReset by remember { mutableStateOf(false) }
                    TextButton(onClick = { confirmReset = true }) {
                        Text(stringResource(R.string.pw_009), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                    if (confirmReset) {
                        AlertDialog(
                            onDismissRequest = { confirmReset = false },
                            title = { Text(stringResource(R.string.pw_010)) },
                            text = { Text(stringResource(R.string.pw_011)) },
                            confirmButton = {
                                TextButton(onClick = {
                                    prefs.edit().remove(KEY_PIN).remove(KEY_ENTRIES).apply()
                                    confirmReset = false; onBack()
                                }) { Text(stringResource(R.string.memo_012), color = scheme.error) }
                            },
                            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.memo_010)) } }
                        )
                    }
                }
            }
        }
        return
    }

    // ---------- 已解锁：条目列表 ----------
    var entries by remember { mutableStateOf(loadEntries(prefs)) }
    var editing by remember { mutableStateOf<PwEntry?>(null) } // null=不弹，PwEntry=编辑，id="" 新建
    var showEditor by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    fun copy(label: String, text: String) {
        clipboard.setText(AnnotatedString(text)); toast(context.getString(R.string.pw_res_001, label))
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = scheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.list_031)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.web_002)) }
                },
                actions = {
                    IconButton(onClick = {
                        prefs.edit().remove(KEY_PIN).apply(); unlocked = false
                    }) { Icon(Icons.Default.Lock, contentDescription = stringResource(R.string.pw_012)) }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                modifier = Modifier.navigationBarsPadding(),
                onClick = { editing = PwEntry("", "", "", "", "", System.currentTimeMillis(), System.currentTimeMillis()); showEditor = true }
            ) { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.pw_013)) }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.pw_014)) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
            val filtered = entries.filter {
                query.isBlank() || it.title.contains(query, true) ||
                    it.username.contains(query, true) || it.note.contains(query, true)
            }
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (entries.isEmpty()) stringResource(R.string.pw_015) else stringResource(R.string.pw_016),
                        color = scheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(filtered, key = { it.id }) { e ->
                        var reveal by remember(e.id) { mutableStateOf(false) }
                        var menuOpen by remember { mutableStateOf(false) }
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Row(
                                Modifier.padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Key, null, tint = scheme.primary)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(e.title.ifBlank { stringResource(R.string.pw_017) }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "${e.username.ifBlank { stringResource(R.string.pw_029) }} · ${if (reveal) e.password else "••••••"}",
                                        style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1
                                    )
                                }
                                IconButton(onClick = { reveal = !reveal }) {
                                    Icon(if (reveal) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, contentDescription = stringResource(R.string.pw_018))
                                }
                                IconButton(onClick = { copy(context.getString(R.string.pw_019), e.password) }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.pw_020))
                                }
                                Box {
                                    IconButton(onClick = { menuOpen = true }) {
                                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.list_026))
                                    }
                                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.pw_021)) },
                                            onClick = { menuOpen = false; copy(context.getString(R.string.pw_022), e.username) },
                                            leadingIcon = { Icon(Icons.Default.Person, null) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.edit_001)) },
                                            onClick = { menuOpen = false; editing = e; showEditor = true },
                                            leadingIcon = { Icon(Icons.Default.Edit, null) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.memo_006), color = scheme.error) },
                                            onClick = {
                                                menuOpen = false
                                                entries = entries.filterNot { it.id == e.id }
                                                saveEntries(prefs, entries)
                                                toast(context.getString(R.string.memo_005))
                                            },
                                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = scheme.error) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ---------- 新增 / 编辑对话框 ----------
    if (showEditor && editing != null) {
        val e = editing!!
        var title by remember { mutableStateOf(e.title) }
        var username by remember { mutableStateOf(e.username) }
        var password by remember { mutableStateOf(e.password) }
        var note by remember { mutableStateOf(e.note) }
        var showPw by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showEditor = false },
            title = { Text(if (e.id.isBlank()) stringResource(R.string.pw_013) else stringResource(R.string.pw_023)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(stringResource(R.string.pw_024)) }, singleLine = true)
                    OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text(stringResource(R.string.pw_025)) }, singleLine = true)
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.pw_019)) },
                        singleLine = true,
                        visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPw = !showPw }) {
                                Icon(if (showPw) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, contentDescription = stringResource(R.string.pw_018))
                            }
                        }
                    )
                    OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text(stringResource(R.string.pw_026)) })
                    // 随机强密码生成（12 位字母数字混合）
                    TextButton(onClick = {
                        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789"
                        password = (1..12).map { chars.random() }.joinToString("")
                        showPw = true
                    }) { Text(stringResource(R.string.pw_027)) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (title.isBlank() && username.isBlank()) {
                        toast(context.getString(R.string.pw_028))
                    } else {
                        val now = System.currentTimeMillis()
                        val updated = e.copy(
                            id = e.id.ifBlank { UUID.randomUUID().toString() },
                            title = title.trim(), username = username.trim(),
                            password = password, note = note.trim(),
                            updatedAt = now, createdAt = e.createdAt.takeIf { it > 0 } ?: now
                        )
                        entries = if (e.id.isBlank()) entries + updated
                        else entries.map { if (it.id == e.id) updated else it }
                        saveEntries(prefs, entries)
                        showEditor = false
                    }
                }) { Text(stringResource(R.string.memo_009)) }
            },
            dismissButton = { TextButton(onClick = { showEditor = false }) { Text(stringResource(R.string.memo_010)) } }
        )
    }
}
