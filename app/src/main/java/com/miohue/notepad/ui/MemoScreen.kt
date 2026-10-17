package com.miohue.notepad.ui

import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import androidx.compose.ui.res.stringResource
import com.miohue.notepad.R

/**
 * 备忘录：轻量便签（独立于笔记库，不进笔记列表）。
 * - 数据存 SharedPreferences（应用私有目录），JSON 序列化
 * - 顶部快速输入即记；条目可勾选完成、点击编辑、长按/菜单删除
 * - 未完成在前（新在上），已完成置底带删除线
 */
private const val KEY_MEMOS = "memo_entries"

private data class Memo(
    val id: String,
    val content: String,
    val done: Boolean,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("content", content); put("done", done)
        put("createdAt", createdAt); put("updatedAt", updatedAt)
    }

    companion object {
        fun fromJson(j: JSONObject) = Memo(
            id = j.optString("id", UUID.randomUUID().toString()),
            content = j.optString("content", ""),
            done = j.optBoolean("done", false),
            createdAt = j.optLong("createdAt"),
            updatedAt = j.optLong("updatedAt")
        )
    }
}

private fun loadMemos(prefs: SharedPreferences): List<Memo> = runCatching {
    val arr = JSONArray(prefs.getString(KEY_MEMOS, "[]") ?: "[]")
    List(arr.length()) { i -> Memo.fromJson(arr.optJSONObject(i) ?: JSONObject()) }
}.getOrDefault(emptyList())

private fun saveMemos(prefs: SharedPreferences, list: List<Memo>) {
    val arr = JSONArray()
    list.forEach { arr.put(it.toJson()) }
    prefs.edit().putString(KEY_MEMOS, arr.toString()).apply()
}

private val memoDateFmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoScreen(prefs: SharedPreferences, onBack: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    var memos by remember { mutableStateOf(loadMemos(prefs)) }
    var quickInput by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Memo?>(null) } // 非空时弹编辑对话框
    var showClearDone by remember { mutableStateOf(false) }

    fun persist(list: List<Memo>) {
        memos = list
        saveMemos(prefs, list)
    }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    // 排序：未完成在前（新在上），已完成置底
    val sorted = memos.sortedWith(
        compareBy({ it.done }, { -it.updatedAt })
    )
    val doneCount = memos.count { it.done }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = scheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.memo_001)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.web_002)) }
                },
                actions = {
                    if (doneCount > 0) {
                        TextButton(onClick = { showClearDone = true }) {
                            Text(stringResource(R.string.memo_res_001, doneCount))
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // 快速记录行：输入 + 回车/按钮即存
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = quickInput,
                    onValueChange = { quickInput = it },
                    placeholder = { Text(stringResource(R.string.memo_002)) },
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.extraLarge
                )
                Spacer(Modifier.width(8.dp))
                FilledTonalIconButton(
                    enabled = quickInput.isNotBlank(),
                    onClick = {
                        val now = System.currentTimeMillis()
                        persist(
                            listOf(Memo(UUID.randomUUID().toString(), quickInput.trim(), false, now, now)) + memos
                        )
                        quickInput = ""
                    }
                ) { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.memo_003)) }
            }

            if (sorted.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.memo_004), color = scheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(sorted, key = { it.id }) { m ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = m.done,
                                    onCheckedChange = { checked ->
                                        persist(memos.map { if (it.id == m.id) it.copy(done = checked, updatedAt = System.currentTimeMillis()) else it })
                                    }
                                )
                                Column(
                                    Modifier.weight(1f).clickable { editing = m },
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        m.content,
                                        style = MaterialTheme.typography.bodyMedium,
                                        textDecoration = if (m.done) TextDecoration.LineThrough else null,
                                        color = if (m.done) scheme.onSurfaceVariant else scheme.onSurface
                                    )
                                    Text(
                                        memoDateFmt.format(Date(m.updatedAt)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = scheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = {
                                    persist(memos.filterNot { it.id == m.id })
                                    toast(context.getString(R.string.memo_005))
                                }) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.memo_006), modifier = Modifier.size(18.dp)) }
                            }
                        }
                    }
                }
            }
        }
    }

    // 编辑对话框（点击条目正文）
    editing?.let { m ->
        var text by remember { mutableStateOf(m.content) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(R.string.memo_007)) },
            text = {
                OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            },
            confirmButton = {
                TextButton(onClick = {
                    if (text.isBlank()) {
                        toast(context.getString(R.string.memo_008))
                    } else {
                        persist(memos.map { if (it.id == m.id) it.copy(content = text.trim(), updatedAt = System.currentTimeMillis()) else it })
                        editing = null
                    }
                }) { Text(stringResource(R.string.memo_009)) }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.memo_010)) } }
        )
    }

    // 清除已完成（破坏性，确认）
    if (showClearDone) {
        AlertDialog(
            onDismissRequest = { showClearDone = false },
            title = { Text(stringResource(R.string.memo_011)) },
            text = { Text(stringResource(R.string.memo_res_002, doneCount)) },
            confirmButton = {
                TextButton(onClick = {
                    persist(memos.filterNot { it.done })
                    showClearDone = false
                }) { Text(stringResource(R.string.memo_012), color = scheme.error) }
            },
            dismissButton = { TextButton(onClick = { showClearDone = false }) { Text(stringResource(R.string.memo_010)) } }
        )
    }
}
