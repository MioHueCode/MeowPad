package com.miohue.notepad.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.TextSnippet
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GeneratingTokens
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PermDeviceInformation
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.miohue.notepad.R

// ============================ 设置页标题（Kori SettingsHeader） ============================

@Composable
fun SettingsHeader(
    text: String
) = Row(
    modifier = Modifier
        .fillMaxWidth()
        .padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
    horizontalArrangement = Arrangement.Start
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary
    )
}

// ============================ 设置项列表容器（Kori ListPaneSection） ============================

@Composable
fun ListPaneSection(
    items: Iterable<@Composable () -> Unit>,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column {
            val itemList = items.toList()
            itemList.forEachIndexed { index, item ->
                item()
                if (index < itemList.size - 1) {
                    ListPaneSectionDivider()
                }
            }
        }
    }
}

@Composable
private fun ListPaneSectionDivider() = HorizontalDivider(
    thickness = 4.dp,
    color = MaterialTheme.colorScheme.surfaceContainer
)

// ============================ 列表项（Kori ListPaneItem） ============================

@Composable
fun ListPaneItem(
    modifier: Modifier = Modifier,
    isSelected: Boolean,
    title: String,
    description: String,
    icon: ImageVector,
    colors: ListItemColors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright),
    onClick: (() -> Unit)? = null
) = ListItem(
    modifier = modifier.clip(MaterialTheme.shapes.extraSmall)
        .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
    headlineContent = { Text(text = title, maxLines = 1) },
    supportingContent = {
        Text(
            text = description,
            maxLines = 1
        )
    },
    leadingContent = {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                else LocalContentColor.current,
                modifier = Modifier
                    .background(
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else Color.Transparent,
                        shape = CircleShape
                    )
                    .padding(10.dp)
            )
        }
    },
    colors = colors
)

// ============================ 详情项（Kori DetailPaneItem） ============================

@Composable
fun DetailPaneItem(
    modifier: Modifier = Modifier,
    title: String,
    description: String? = null,
    icon: ImageVector? = null,
    trailingContent: @Composable () -> Unit = {},
    onClick: (() -> Unit)? = null
) = ListItem(
    modifier = modifier.clip(MaterialTheme.shapes.large)
        .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
    headlineContent = { Text(title) },
    supportingContent = description?.let { { Text(it) } },
    leadingContent = icon?.let {
        {
            Icon(
                imageVector = icon,
                contentDescription = null
            )
        }
    },
    trailingContent = trailingContent
)

// ============================ 设置主入口（Kori SettingsListPane 简化版：仅主题/数据两块） ============================

@Composable
fun SettingsListPane(
    selectedItem: Int? = null,
    navigateToDetail: (Int) -> Unit
) = Column(
    Modifier
        .background(MaterialTheme.colorScheme.surfaceContainer)
        .padding(top = 8.dp)
) {
    ListPaneSection(
        items = listOf(
            {
                ListPaneItem(
                    title = stringResource(R.string.sc_001),
                    description = stringResource(R.string.sc_002),
                    icon = Icons.Outlined.Palette,
                    isSelected = selectedItem == 0,
                    onClick = { navigateToDetail(0) }
                )
            },
            {
                ListPaneItem(
                    title = stringResource(R.string.list_037),
                    description = stringResource(R.string.sc_003),
                    icon = Icons.Outlined.SdStorage,
                    isSelected = selectedItem == 1,
                    onClick = { navigateToDetail(1) }
                )
            }
        )
    )
}

// ============================ 分段控件（Kori SegmentedControl 的 material3 1.3.0 等效自绘） ============================

@Composable
fun SegmentedControlRow(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            labels.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.large)
                        .background(
                            if (selected) MaterialTheme.colorScheme.secondaryContainer
                            else Color.Transparent
                        )
                        .clickable { onSelect(index) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

// ============================ 设置弹出面板（对齐编辑页侧栏交互：scrim + 右侧滑入面板） ============================

@Composable
fun SettingsPaneOverlay(
    visible: Boolean,
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize()) {
        // scrim 淡入淡出（点击关闭），面板从右滑入
        AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(scheme.scrim.copy(alpha = 0.32f))
                    .clickable { onDismiss() }
            )
        }
        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally(animationSpec = tween(260)) { it },
            exit = slideOutHorizontally(animationSpec = tween(220)) { it },
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
        Surface(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = 400.dp)
                .fillMaxWidth(0.92f),
            shape = MaterialTheme.shapes.extraLarge.copy(
                topEnd = CornerSize(0.dp),
                bottomEnd = CornerSize(0.dp)
            ),
            color = scheme.surfaceContainer,
            shadowElevation = 6.dp
        ) {
            Column(
                Modifier
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .fillMaxSize()
            ) {
                // 面板顶栏：返回 + 标题
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.web_002))
                    }
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)
                    )
                }
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 24.dp),
                    content = content
                )
            }
        }
    }
}
}