package com.miohue.notepad.ui

import android.content.SharedPreferences
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.miohue.notepad.R
import com.miohue.notepad.data.AiConfig
import com.miohue.notepad.data.LlmClient
import kotlinx.coroutines.launch

/**
 * AI 模型设置面板。
 *
 * 参考 Kori `AiPane` 的字段设计（服务商 + API 地址 + Key + 模型），
 * 并支持两类自定义协议入口（OpenAI 兼容 / Anthropic 兼容）。
 *
 * 「刷新模型列表」会真的请求 `GET {baseUrl}/models`：
 * 成功则把服务端返回的模型清单存进缓存并作为下拉候选；
 * 失败（很多中转没实现该接口）给出提示，仍可手输模型名。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiPaneContent(prefs: SharedPreferences) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var enabled by remember { mutableStateOf(AiConfig.isEnabled(context)) }
    var provider by remember { mutableStateOf(AiConfig.provider(context)) }
    var baseUrl by remember { mutableStateOf(AiConfig.baseUrl(context)) }
    var apiKey by remember { mutableStateOf(AiConfig.apiKey(context)) }
    var model by remember { mutableStateOf(AiConfig.model(context)) }

    var showKey by remember { mutableStateOf(false) }
    var providerExpanded by remember { mutableStateOf(false) }
    var modelPickerOpen by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }

    // 模型候选：优先用「刷新」拉到的缓存，否则用内置预设
    var remoteModels by remember(provider, baseUrl) {
        mutableStateOf(AiConfig.cachedModels(context, provider.id, baseUrl))
    }
    val modelCandidates = remoteModels.ifEmpty { provider.models }

    fun persist() {
        AiConfig.save(context, provider.id, baseUrl, apiKey, model)
        AiConfig.setEnabled(context, enabled)
    }

    fun showResult(isError: Boolean, text: String) {
        statusIsError = isError
        status = text
    }

    Column(
        // 注意：外层 SettingsPaneOverlay 已提供 verticalScroll，
        // 这里绝不能再套 verticalScroll（嵌套可滚动容器 = 无限高度约束 → 点击即崩）。
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(horizontal = 16.dp)
    ) {
        // ==================== 总开关 ====================
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.ai_022), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.ai_024),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = enabled, onCheckedChange = { enabled = it; persist() })
        }

        Spacer(Modifier.height(16.dp))

        // ==================== 服务商下拉 ====================
        ExposedDropdownMenuBox(
            expanded = providerExpanded,
            onExpandedChange = { providerExpanded = !providerExpanded }
        ) {
            OutlinedTextField(
                value = provider.display,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.ai_028)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable)
            )
            ExposedDropdownMenu(expanded = providerExpanded, onDismissRequest = { providerExpanded = false }) {
                AiConfig.Provider.entries.forEach { p ->
                    DropdownMenuItem(
                        text = { Text(p.display) },
                        onClick = {
                            provider = p
                            providerExpanded = false
                            // 切换服务商时带出该家默认地址与模型（自定义则清空，交给用户填）
                            baseUrl = p.baseUrl
                            model = p.defaultModel
                            remoteModels = AiConfig.cachedModels(context, p.id, p.baseUrl)
                            persist()
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = { Text(stringResource(R.string.ai_029)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = {
                Text(
                    if (provider.needsKey) stringResource(R.string.ai_030)
                    else stringResource(R.string.ai_043)
                )
            },
            singleLine = true,
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { showKey = !showKey }) {
                    Icon(
                        if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        // ==================== 模型：点击弹出可滑动列表 + 刷新 ====================
        OutlinedTextField(
            value = model,
            onValueChange = { model = it },
            label = { Text(stringResource(R.string.ai_031)) },
            singleLine = true,
            readOnly = false,
            trailingIcon = {
                Row {
                    // 打开模型列表（有候选时）
                    if (modelCandidates.isNotEmpty()) {
                        IconButton(onClick = { modelPickerOpen = true }) {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = stringResource(R.string.ai_050))
                        }
                    }
                    // 刷新模型列表
                    IconButton(
                        enabled = !refreshing,
                        onClick = {
                            persist()
                            refreshing = true
                            showResult(false, context.getString(R.string.ai_045))
                            scope.launch {
                                when (val r = LlmClient.listModels(context)) {
                                    is LlmClient.Result.Ok -> {
                                        val ids = r.text.split('\n').filter { it.isNotBlank() }
                                        remoteModels = ids
                                        AiConfig.cacheModels(context, provider.id, baseUrl, ids)
                                        showResult(false, context.getString(R.string.ai_046, ids.size))
                                    }
                                    is LlmClient.Result.Err -> {
                                        showResult(true, "${context.getString(R.string.ai_049)}\n(${r.message})")
                                    }
                                }
                                refreshing = false
                            }
                        }
                    ) {
                        if (refreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.ai_044))
                        }
                    }
                }
            },
            supportingText = {
                Text(
                    stringResource(R.string.ai_044) + " · " + stringResource(R.string.ai_050),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            modifier = Modifier.fillMaxWidth()
        )

        // 模型列表：弹出可滑动的底部选择器（模型多时不再平铺占满面板）
        if (modelPickerOpen) {
            ModalBottomSheet(
                onDismissRequest = { modelPickerOpen = false },
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.ai_050),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(modelCandidates, key = { it }) { m ->
                            val selected = m == model
                            ListItem(
                                headlineContent = {
                                    Text(m, style = MaterialTheme.typography.bodyLarge)
                                },
                                trailingContent = {
                                    if (selected) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                colors = ListItemDefaults.colors(
                                    containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                    else Color.Transparent
                                ),
                                modifier = Modifier.clickable {
                                    model = m
                                    persist()
                                    modelPickerOpen = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ==================== 保存 / 测试连接 ====================
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                persist()
                showResult(false, context.getString(R.string.ai_032))
            }) { Text(stringResource(R.string.memo_009)) }

            TextButton(
                enabled = !testing,
                onClick = {
                    persist()
                    testing = true
                    status = null
                    scope.launch {
                        when (val result = LlmClient.chat(
                            context,
                            systemPrompt = "You are a helpful assistant.",
                            userContent = "ping"
                        )) {
                            is LlmClient.Result.Ok -> showResult(false, context.getString(R.string.ai_034))
                            is LlmClient.Result.Err -> showResult(true, context.getString(R.string.ai_027, result.message))
                        }
                        testing = false
                    }
                }
            ) {
                if (testing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text(stringResource(R.string.ai_033))
            }
        }

        status?.let {
            Spacer(Modifier.height(12.dp))
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = if (statusIsError) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}
