package com.miohue.notepad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.miohue.notepad.data.Note
import com.miohue.notepad.data.NoteRepository
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.res.stringResource
import com.miohue.notepad.R

/** 回收站（对齐 OpenNote）：卡片式列表 + 恢复全部 + 单条恢复/永久删除。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    repo: NoteRepository,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onEdit: (String) -> Unit
) {
    var trash by remember { mutableStateOf(repo.trashList()) }
    var status by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    fun refresh() { trash = repo.trashList() }

    Scaffold(

        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.list_032)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.web_002)) }
                },
                actions = {
                    if (trash.isNotEmpty()) {
                        IconButton(onClick = { confirmClear = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = stringResource(R.string.edit_087))
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (status.isNotBlank()) {
                Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp))
            }
            if (trash.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.trash_001))
                }
            } else {
                // 恢复全部
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Text(stringResource(R.string.trash_res_001, trash.size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        trash.forEach { repo.restoreFromTrash(it.id) }
                        status = context.getString(R.string.trash_002)
                        refresh()
                    }) { Text(stringResource(R.string.trash_003)) }
                }
                LazyColumn {
                    items(trash, key = { it.id }) { note ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(note.title.ifBlank { stringResource(R.string.edit_030) }, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1)
                                }
                                if (note.content.isNotBlank()) {
                                    Text(note.content.take(80), maxLines = 2, style = MaterialTheme.typography.bodySmall)
                                }
                                Spacer(Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(note.updatedAt)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = { repo.restoreFromTrash(note.id); status = context.getString(R.string.trash_004); refresh() }) { Text(stringResource(R.string.trash_005)) }
                                    TextButton(onClick = { repo.deleteForever(note.id); status = context.getString(R.string.trash_006); refresh() }) {
                                        Text(stringResource(R.string.memo_006), color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.trash_007)) },
            text = { Text(stringResource(R.string.trash_008)) },
            confirmButton = {
                TextButton(onClick = {
                    val n = repo.emptyTrash()
                    status = context.getString(R.string.trash_res_002, n)
                    confirmClear = false
                    refresh()
                }) { Text(stringResource(R.string.edit_087), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.memo_010)) } }
        )
    }
}
