package com.miohue.notepad.ui

import android.content.SharedPreferences
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.miohue.notepad.data.Folder
import com.miohue.notepad.data.Note
import com.miohue.notepad.data.NoteRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import com.miohue.notepad.R

enum class SortMode { UPDATED, CREATED, TITLE }

/** 排序模式的本地化显示名。 */
@Composable
private fun sortModeLabel(mode: SortMode): String = when (mode) {
    SortMode.UPDATED -> stringResource(R.string.list_001)
    SortMode.CREATED -> stringResource(R.string.list_002)
    SortMode.TITLE -> stringResource(R.string.list_003)
}

/** 文件夹显示名：直接用用户自定义名（无默认文件夹，对齐 Kori）。 */
@Composable
fun folderDisplayName(folder: com.miohue.notepad.data.Folder): String = folder.name

/**
 * 按 id 取文件夹显示名。
 *
 * 对齐 Kori：无默认文件夹。id 为空（笔记不属于任何文件夹）或文件夹已删除时，
 * 显示「全部笔记」——与 Kori FoldersDialog 的空文件夹选项文案一致。
 */
@Composable
fun folderNameOf(folders: List<com.miohue.notepad.data.Folder>, id: String): String {
    if (id.isBlank()) return stringResource(R.string.list_030)
    val folder = folders.firstOrNull { it.id == id } ?: return stringResource(R.string.list_030)
    return folderDisplayName(folder)
}

/** 自动分类的本地化显示名。 */
@Composable
private fun categoryDisplayName(key: String): String = when (NoteRepository.CategoryType.fromKey(key)) {
    NoteRepository.CategoryType.LINK -> stringResource(R.string.repo_008)
    NoteRepository.CategoryType.IMAGE -> stringResource(R.string.repo_003)
    NoteRepository.CategoryType.VIDEO -> stringResource(R.string.repo_004)
    NoteRepository.CategoryType.FILE -> stringResource(R.string.repo_005)
    NoteRepository.CategoryType.AUDIO -> stringResource(R.string.repo_006)
    NoteRepository.CategoryType.CODE -> stringResource(R.string.repo_009)
    else -> stringResource(R.string.repo_007)
}

/** 自动分类的图标。 */
private fun categoryIcon(key: String) = when (NoteRepository.CategoryType.fromKey(key)) {
    NoteRepository.CategoryType.LINK -> Icons.Default.Link
    NoteRepository.CategoryType.IMAGE -> Icons.Default.Image
    NoteRepository.CategoryType.VIDEO -> Icons.Default.Videocam
    NoteRepository.CategoryType.FILE -> Icons.Default.InsertDriveFile
    NoteRepository.CategoryType.AUDIO -> Icons.Default.MusicNote
    NoteRepository.CategoryType.CODE -> Icons.Default.Code
    else -> Icons.Default.Notes
}

/** 分类条目点击：链接走内置浏览器，其余交给系统打开。 */
private fun handleCategoryItemClick(
    item: NoteRepository.CategoryItem,
    onOpenUrl: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onOpen: (String) -> Unit
) {
    when (NoteRepository.CategoryType.fromKey(item.kind)) {
        NoteRepository.CategoryType.LINK -> onOpenUrl(item.data)
        NoteRepository.CategoryType.IMAGE ->
            if (item.data.startsWith("http")) onOpenUrl(item.data) else onOpenFile(item.data)
        NoteRepository.CategoryType.VIDEO,
        NoteRepository.CategoryType.FILE,
        NoteRepository.CategoryType.AUDIO -> onOpenFile(item.data)
        else -> onOpen(item.note.id)
    }
}

/**
 * 日期分组标题（今天 / 昨天 / 具体日期）。
 *
 * 性能：`SimpleDateFormat` 非线程安全，加锁复用调用方传入的实例，避免每次新建。
 * （分组计算本身已在 `remember(sorted, sortMode)` 内，只在列表/排序变化时执行，不是每帧。）
 */
private fun groupLabel(context: android.content.Context, ts: Long, today: Calendar, fmt: SimpleDateFormat): String {
    val cal = Calendar.getInstance().apply { timeInMillis = ts }
    return when {
        today.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
                today.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR) -> context.getString(R.string.list_004)
        today.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
                today.get(Calendar.DAY_OF_YEAR) - cal.get(Calendar.DAY_OF_YEAR) == 1 -> context.getString(R.string.list_005)
        else -> synchronized(fmt) { fmt.format(Date(ts)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun NoteListScreen(
    repo: NoteRepository,
    prefs: SharedPreferences,
    onOpen: (String) -> Unit,
    onEdit: (String) -> Unit,
    onNew: () -> Unit,
    onTrash: () -> Unit,
    onSettings: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenBrowser: () -> Unit,
    onToolbox: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onPassword: () -> Unit,
    onMemo: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    // 用 state 保存笔记列表，操作后触发重组，置顶/收藏即时刷新
    var notes by remember { mutableStateOf(repo.list()) }
    val folders = repo.folders()
    val categories = remember(notes) { repo.categorize() }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedFolder by rememberSaveable { mutableStateOf("all") }
    var onlyFavorite by remember { mutableStateOf(false) }
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var sortMode by remember { mutableStateOf(SortMode.valueOf(prefs.getString("sort_mode", "UPDATED")!!)) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showFolderManager by remember { mutableStateOf(false) }
    // 视图模式（网格/列表）持久化：点进笔记返回后保持用户上次选择，不恢复默认
    var gridView by remember { mutableStateOf(prefs.getBoolean("grid_view", false)) }
    // 卡片大小：0=默认(两行预览) 1=仅标题 2=紧凑(一行预览)，与设置页 CardPane 同一持久化键
    val cardSize = prefs.getInt("card_size", 0)
    var pendingDelete by remember { mutableStateOf<Note?>(null) }
    // 多选模式
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    // 移动到文件夹
    var moveNote by remember { mutableStateOf<Note?>(null) }
    var moveNoteIds by remember { mutableStateOf<List<String>>(emptyList()) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    // 多选模式下，返回键/侧滑取消选中（而非退出）
    androidx.activity.compose.BackHandler(enabled = selectionMode) {
        selectionMode = false
        selectedIds = emptySet()
    }

    // 搜索框有内容时，返回键/侧滑清空搜索框
    androidx.activity.compose.BackHandler(enabled = query.isNotEmpty()) {
        query = ""
    }

    // 分类筛选
    val categoryFiltered = if (selectedCategory != null) {
        categories[selectedCategory] ?: emptyList()
    } else notes
    // 性能：筛选+排序对全部笔记做多次遍历与比较，原先每次重组（滚动/菜单/多选状态变化）都重算。
    // 这里按真实依赖做记忆化，依赖不变时直接复用上次结果。
    val sorted = remember(categoryFiltered, selectedFolder, onlyFavorite, query, sortMode) {
        val inFolder = categoryFiltered.filter { selectedFolder == "all" || it.folderId == selectedFolder }
        val inFav = if (onlyFavorite) inFolder.filter { it.favorite } else inFolder
        val filtered = inFav.filter {
            query.isBlank() || it.title.contains(query, true) || it.content.contains(query, true) ||
                    it.tags.any { t -> t.contains(query, true) }
        }
        when (sortMode) {
            SortMode.UPDATED -> filtered.sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt })
            SortMode.CREATED -> filtered.sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.createdAt })
            SortMode.TITLE -> filtered.sortedWith(compareByDescending<Note> { it.pinned }.thenBy { it.title.ifBlank { "~" }.lowercase() })
        }
    }

    val dayFmt = remember { SimpleDateFormat(context.getString(R.string.list_006), Locale.getDefault()) }
    // 「今天/昨天」的分组依据：跨天时需要重算，否则第二天打开「昨天」还会算成「今天」。
    // 用 memoized 的当天 0 点时间戳作为 key，日期一变 key 就变，sections 自动重算。
    val dayKey = remember { System.currentTimeMillis() / 86_400_000L }
    val sections: List<Pair<String, List<Note>>> = remember(sorted, sortMode, dayKey) {
        if (sortMode == SortMode.UPDATED) {
            val today = Calendar.getInstance()
            val pinned = sorted.filter { it.pinned }
            val byLabel = LinkedHashMap<String, MutableList<Note>>()
            sorted.filter { !it.pinned }.forEach { n ->
                val label = groupLabel(context, n.updatedAt, today, dayFmt)
                byLabel.getOrPut(label) { mutableListOf() }.add(n)
            }
            buildList {
                if (pinned.isNotEmpty()) add(context.getString(R.string.list_007) to pinned)
                byLabel.forEach { (k, v) -> add(k to v) }
            }
        } else {
            if (sorted.isEmpty()) emptyList() else listOf(context.getString(R.string.list_008) to sorted)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(
                folders = folders,
                selectedFolder = selectedFolder,
                onlyFavorite = onlyFavorite,
                selectedCategory = selectedCategory,
                categories = categories,
                onSelectFolder = { selectedFolder = it; onlyFavorite = false; selectedCategory = null },
                onAll = { selectedFolder = "all"; onlyFavorite = false; selectedCategory = null },
                onFavorites = { onlyFavorite = true; selectedFolder = "all"; selectedCategory = null },
                onSelectCategory = { selectedCategory = it; onlyFavorite = false; selectedFolder = "all" },
                onOpenBrowser = { scope.launch { drawerState.close() }; onOpenBrowser() },
                onOpenFiles = { scope.launch { drawerState.close() }; onOpenFiles() },
                onToolbox = { scope.launch { drawerState.close() }; onToolbox() },
                onTrash = { scope.launch { drawerState.close() }; onTrash() },
                onSettings = { scope.launch { drawerState.close() }; onSettings() },
                onManageFolders = { scope.launch { drawerState.close() }; showFolderManager = true },
                onPassword = { scope.launch { drawerState.close() }; onPassword() },
                onMemo = { scope.launch { drawerState.close() }; onMemo() }
            )
        }
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = Color.Transparent,
            topBar = {
                if (selectionMode) {
                    TopAppBar(
                        title = { Text(stringResource(R.string.list_res_001, selectedIds.size)) },
                        navigationIcon = {
                            IconButton(onClick = { selectionMode = false; selectedIds = emptySet() }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.memo_010))
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                        actions = {
                            IconButton(onClick = {
                                selectedIds = if (selectedIds.size == sorted.size) emptySet() else sorted.map { it.id }.toSet()
                            }) {
                                Icon(Icons.Default.SelectAll, contentDescription = stringResource(R.string.list_009))
                            }
                            IconButton(onClick = { moveNoteIds = selectedIds.toList(); selectedIds = emptySet() }) {
                                Icon(Icons.Default.DriveFileMove, contentDescription = stringResource(R.string.list_010))
                            }
                            IconButton(onClick = {
                                selectedIds.forEach { repo.deleteToTrash(it) }
                                selectedIds = emptySet()
                                selectionMode = false
                                notes = repo.list()
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.list_011))
                            }
                        }
                    )
                } else {
                    TopAppBar(
                        title = {
                            // 圆滑容器：搜索框 + 视图切换 + 排序
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = scheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                    IconButton(onClick = { scope.launch { drawerState.open() } }, modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.list_012), modifier = Modifier.size(20.dp))
                                    }
                                    Icon(Icons.Default.Search, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Box(Modifier.weight(1f)) {
                                        BasicTextField(
                                            value = query,
                                            onValueChange = { query = it },
                                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = scheme.onSurface),
                                            singleLine = true,
                                            cursorBrush = SolidColor(scheme.primary),
                                            modifier = Modifier.fillMaxWidth(),
                                            decorationBox = { inner ->
                                                if (query.isEmpty()) {
                                                    Text(stringResource(R.string.list_013), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                                                }
                                                inner()
                                            }
                                        )
                                    }
                                    IconButton(onClick = {
                                        gridView = !gridView
                                        prefs.edit().putBoolean("grid_view", gridView).apply()
                                    }, modifier = Modifier.size(36.dp)) {
                                        Icon(
                                            if (gridView) Icons.Default.ViewList else Icons.Default.GridView,
                                            contentDescription = stringResource(R.string.list_014),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Box {
                                        IconButton(onClick = { showSortMenu = true }, modifier = Modifier.size(36.dp)) {
                                            Icon(Icons.Default.Sort, contentDescription = stringResource(R.string.list_015), modifier = Modifier.size(20.dp))
                                        }
                                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                            SortMode.entries.forEach { sm ->
                                                DropdownMenuItem(
                                                    text = { Text(sortModeLabel(sm) + if (sm == sortMode) " ✓" else "") },
                                                    onClick = {
                                                        sortMode = sm
                                                        prefs.edit().putString("sort_mode", sm.name).apply()
                                                        showSortMenu = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        navigationIcon = {
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                        actions = {
                        }
                    )
                }
            }
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                if (selectedCategory != null) {
                    // 分类视图：显示扫描出的具体内容条目（链接/图片/文件/文字），而非整个笔记
                    val items = remember(selectedCategory, notes) { repo.categorizeItems(selectedCategory!!) }
                    if (items.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.list_res_002, categoryDisplayName(selectedCategory ?: "")))
                        }
                    } else {
                        if (gridView) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                contentPadding = PaddingValues(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                gridItems(items, key = { "${it.kind}_${it.text}_${it.note.id}" }) { item ->
                                    CategoryItemRow(
                                        item = item,
                                        onClick = { handleCategoryItemClick(item, onOpenUrl, onOpenFile, onOpen) },
                                        onOpenNote = { onOpen(item.note.id) }
                                    )
                                }
                            }
                        } else {
                            LazyColumn {
                                items(items, key = { "${it.kind}_${it.text}_${it.note.id}" }) { item ->
                                    CategoryItemRow(
                                        item = item,
                                        onClick = { handleCategoryItemClick(item, onOpenUrl, onOpenFile, onOpen) },
                                        onOpenNote = { onOpen(item.note.id) }
                                    )
                                }
                            }
                        }
                    }
                } else if (sorted.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            // 小猫点缀：低透明度着色，作为空态插画而不喧宾夺主
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(RoundedCornerShape(28.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.30f))
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.ic_meow_cat),
                                    contentDescription = null,
                                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                                    ),
                                    modifier = Modifier.size(52.dp)
                                )
                            }
                            Spacer(Modifier.height(20.dp))
                            Text(
                                when {
                                    onlyFavorite -> stringResource(R.string.list_016)
                                    query.isBlank() -> stringResource(R.string.list_017)
                                    else -> stringResource(R.string.list_018)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            if (query.isBlank() && !onlyFavorite) {
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    "＋",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                )
                            }
                        }
                    }
                } else {
                    if (gridView) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            gridItems(sorted, key = { it.id }, contentType = { "note" }) { note ->
                                NoteCard(
                                    note = note,
                                    folderName = folderNameOf(folders, note.folderId),
                                    cardSize = cardSize,
                                    selectionMode = selectionMode,
                                    isSelected = note.id in selectedIds,
                                    onSelect = {
                                        selectedIds = if (note.id in selectedIds) selectedIds - note.id else selectedIds + note.id
                                    },
                                    onLongPress = { selectionMode = true; selectedIds = setOf(note.id) },
                                    onClick = { onOpen(note.id) },
                                    onEdit = { onEdit(note.id) },
                                    onPin = { repo.save(note.copy(pinned = !note.pinned)); notes = repo.list() },
                                    onFavorite = { repo.save(note.copy(favorite = !note.favorite)); notes = repo.list() },
                                    onDelete = { pendingDelete = note },
                                    onMoveToFolder = { moveNote = note },
                                    onTagClick = { tag -> query = tag; selectedFolder = "all"; onlyFavorite = false; selectedCategory = null }
                                )
                            }
                            item { Spacer(Modifier.height(72.dp)) }
                        }
                    } else {
                        LazyColumn {
                            sections.forEach { (label, list) ->
                                item(key = "header_$label", contentType = "header") {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 4.dp)
                                    )
                                }
                                // 性能：加 contentType，滚动时 Compose 只复用同类型内容，
                                // 避免 header 与 note 卡片交叉复用导致的反复重建。
                                items(list, key = { it.id }, contentType = { "note" }) { note ->
                                    NoteCard(
                                        note = note,
                                        folderName = folderNameOf(folders, note.folderId),
                                        cardSize = cardSize,
                                        selectionMode = selectionMode,
                                        isSelected = note.id in selectedIds,
                                        onSelect = {
                                            selectedIds = if (note.id in selectedIds) selectedIds - note.id else selectedIds + note.id
                                        },
                                        onLongPress = { selectionMode = true; selectedIds = setOf(note.id) },
                                        onClick = { onOpen(note.id) },
                                        onEdit = { onEdit(note.id) },
                                        onPin = { repo.save(note.copy(pinned = !note.pinned)); notes = repo.list() },
                                        onFavorite = { repo.save(note.copy(favorite = !note.favorite)); notes = repo.list() },
                                        onDelete = { pendingDelete = note },
                                        onMoveToFolder = { moveNote = note },
                                        onTagClick = { tag -> query = tag; selectedFolder = "all"; onlyFavorite = false; selectedCategory = null }
                                    )
                                }
                            }
                            item { Spacer(Modifier.height(72.dp)) }
                        }
                    }
                }
            }
        }
    }

    if (showFolderManager) {
        FolderManagerDialog(repo = repo, onDismiss = { showFolderManager = false })
    }

    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.list_019)) },
            text = { Text(stringResource(R.string.list_res_003, pendingDelete!!.title.ifBlank { stringResource(R.string.edit_029) })) },
            confirmButton = {
                TextButton(onClick = {
                    repo.deleteToTrash(pendingDelete!!.id)
                    pendingDelete = null
                    notes = repo.list()
                }) { Text(stringResource(R.string.memo_006), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.memo_010)) } }
        )
    }

    // 移动到文件夹对话框
    if (moveNote != null) {
        AlertDialog(
            onDismissRequest = { moveNote = null },
            title = { Text(stringResource(R.string.edit_037)) },
            text = {
                Column {
                    // 对齐 Kori FoldersDialog：首项「全部笔记」= 移出文件夹（folderId 置空）
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                repo.save(moveNote!!.copy(folderId = ""))
                                moveNote = null
                                notes = repo.list()
                            }
                            .padding(vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.list_030) + if (moveNote!!.folderId.isBlank()) " ✓" else "")
                    }
                    folders.forEach { f ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    repo.save(moveNote!!.copy(folderId = f.id))
                                    moveNote = null
                                    notes = repo.list()
                                }
                                .padding(vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(folderDisplayName(f) + if (moveNote!!.folderId == f.id) " ✓" else "")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { moveNote = null }) { Text(stringResource(R.string.memo_010)) } }
        )
    }

    // 批量移动到文件夹对话框
    if (moveNoteIds.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { moveNoteIds = emptyList() },
            title = { Text(stringResource(R.string.list_res_004, moveNoteIds.size)) },
            text = {
                Column {
                    // 对齐 Kori FoldersDialog：首项「全部笔记」= 移出文件夹（folderId 置空）
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                moveNoteIds.forEach { noteId ->
                                    repo.get(noteId)?.let { n -> repo.save(n.copy(folderId = "")) }
                                }
                                moveNoteIds = emptyList()
                                selectionMode = false
                                notes = repo.list()
                            }
                            .padding(vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.list_030))
                    }
                    folders.forEach { f ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    moveNoteIds.forEach { noteId ->
                                        repo.get(noteId)?.let { n -> repo.save(n.copy(folderId = f.id)) }
                                    }
                                    moveNoteIds = emptyList()
                                    selectionMode = false
                                    notes = repo.list()
                                }
                                .padding(vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(folderDisplayName(f))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { moveNoteIds = emptyList() }) { Text(stringResource(R.string.memo_010)) } }
        )
    }
}

@Composable
fun FolderManagerDialog(repo: NoteRepository, onDismiss: () -> Unit) {
    var folders by remember { mutableStateOf(repo.folders()) }
    var newName by remember { mutableStateOf("") }
    var renamingId by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }
    // 对齐 Kori FoldersScreen：待删除文件夹状态，删除前弹警告对话框
    var deletingFolder by remember { mutableStateOf<com.miohue.notepad.data.Folder?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.edit_071)) } },
        title = { Text(stringResource(R.string.list_021)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                folders.forEach { f ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        if (renamingId == f.id) {
                            OutlinedTextField(
                                value = renameText,
                                onValueChange = { renameText = it },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            IconButton(onClick = {
                                if (renameText.isNotBlank()) {
                                    repo.renameFolder(f.id, renameText.trim())
                                    folders = repo.folders()
                                }
                                renamingId = null
                            }) { Icon(Icons.Default.Check, contentDescription = stringResource(R.string.list_022)) }
                        } else {
                            Text(folderDisplayName(f), modifier = Modifier.weight(1f))
                            IconButton(onClick = { renamingId = f.id; renameText = f.name }) {
                                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.list_023))
                            }
                            IconButton(onClick = { deletingFolder = f }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.memo_006))
                            }
                        }
                    }
                }
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text(stringResource(R.string.list_024)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    TextButton(onClick = {
                        if (newName.isNotBlank()) {
                            repo.addFolder(newName.trim())
                            newName = ""
                            folders = repo.folders()
                        }
                    }) { Text(stringResource(R.string.app_005)) }
                }
            }
        }
    )

    // 对齐 Kori FoldersScreen WarningDialog：删除文件夹警告，确认后文件夹与其中笔记一并删除
    deletingFolder?.let { target ->
        val haptic = LocalHapticFeedback.current
        AlertDialog(
            onDismissRequest = { deletingFolder = null },
            title = { Text(stringResource(R.string.list_039)) },
            text = { Text(stringResource(R.string.list_040)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        repo.deleteFolder(target.id)
                        folders = repo.folders()
                        deletingFolder = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) { Text(stringResource(R.string.memo_006)) }
            },
            dismissButton = {
                TextButton(onClick = { deletingFolder = null }) { Text(stringResource(R.string.memo_010)) }
            }
        )
    }
}

/**
 * 笔记卡片。
 *
 * 性能：时间格式化原来是**每帧重建 SimpleDateFormat**（`SimpleDateFormat(pattern, Locale)`），
 * 这个类构造开销不小，列表滚动时会持续分配对象触发 GC 抖动。
 * 改为共享一个实例并做同步，避免反复构造。
 */
private val cardDateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
private val cardDateLock = Any()

private fun formatCardTime(ts: Long): String = synchronized(cardDateLock) {
    cardDateFmt.format(Date(ts))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(
    note: Note,
    folderName: String,
    cardSize: Int = 0,
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    onSelect: () -> Unit = {},
    onLongPress: () -> Unit = {},
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onPin: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit,
    onMoveToFolder: () -> Unit = {},
    onTagClick: (String) -> Unit = {}
) {
    val scheme = MaterialTheme.colorScheme
    var menuOpen by remember { mutableStateOf(false) }
    // 选中态用 primaryContainer 无阴影 + 明显描边；普通态用 surface 微投影 + 极淡描边，
    // 与主题统一圆角体系（MaterialTheme.shapes.medium=14dp）呼应，卡片层次更清晰。
    val cardShape = RoundedCornerShape(16.dp)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .combinedClickable(
                onClick = { if (selectionMode) onSelect() else onClick() },
                onLongClick = { if (!selectionMode) onLongPress() }
            ),
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) scheme.primaryContainer else scheme.surfaceContainerLow
        ),
        border = if (isSelected) {
            BorderStroke(2.dp, scheme.primary)
        } else {
            BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.45f))
        },
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 0.dp else 1.dp,
            pressedElevation = 0.dp
        )
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectionMode) {
                    Icon(
                        if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = stringResource(R.string.list_025),
                        tint = if (isSelected) scheme.primary else scheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                if (note.favorite) {
                    Icon(Icons.Default.Star, null, Modifier.size(14.dp), tint = Color(0xFFF9A825))
                    Spacer(Modifier.width(3.dp))
                }
                if (note.pinned) {
                    Icon(Icons.Default.PushPin, null, Modifier.size(12.dp), tint = scheme.primary)
                    Spacer(Modifier.width(3.dp))
                }
                Text(
                    note.title.ifBlank { stringResource(R.string.edit_030) },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1
                )
                // 三点菜单：置顶/收藏/编辑/删除
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.list_026), modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(if (note.pinned) stringResource(R.string.list_027) else stringResource(R.string.edit_039)) },
                            leadingIcon = { Icon(Icons.Default.PushPin, null) },
                            onClick = { menuOpen = false; onPin() }
                        )
                        DropdownMenuItem(
                            text = { Text(if (note.favorite) stringResource(R.string.list_028) else stringResource(R.string.edit_040)) },
                            leadingIcon = { Icon(if (note.favorite) Icons.Default.Star else Icons.Default.StarBorder, null) },
                            onClick = { menuOpen = false; onFavorite() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.edit_001)) },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick = { menuOpen = false; onEdit() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.memo_006)) },
                            leadingIcon = { Icon(Icons.Default.Delete, null) },
                            onClick = { menuOpen = false; onDelete() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.edit_037)) },
                            leadingIcon = { Icon(Icons.Default.DriveFileMove, null) },
                            onClick = { menuOpen = false; onMoveToFolder() }
                        )
                    }
                }
            }
            if (cardSize != 1 && note.content.isNotBlank()) {
                Text(
                    note.content.take(80),
                    maxLines = if (cardSize == 2) 1 else 2,
                    style = if (note.content.contains("```")) {
                        MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    } else {
                        MaterialTheme.typography.bodySmall
                    }
                )
            }
            // 标签显示（可点击搜索）
            if (note.tags.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    note.tags.take(3).forEach { tag ->
                        Text(
                            "#$tag",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.primary,
                            maxLines = 1,
                            modifier = Modifier.clickable { onTagClick(tag) }
                        )
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                formatCardTime(note.updatedAt),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun DrawerContent(
    folders: List<Folder>,
    selectedFolder: String,
    onlyFavorite: Boolean,
    selectedCategory: String?,
    categories: Map<String, List<Note>>,
    onSelectFolder: (String) -> Unit,
    onAll: () -> Unit,
    onFavorites: () -> Unit,
    onSelectCategory: (String) -> Unit,
    onOpenBrowser: () -> Unit,
    onOpenFiles: () -> Unit,
    onToolbox: () -> Unit,
    onTrash: () -> Unit,
    onSettings: () -> Unit,
    onManageFolders: () -> Unit,
    onPassword: () -> Unit,
    onMemo: () -> Unit
) {
    ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.ic_meow_cat),
                        contentDescription = null,
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "MeowPad",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onSettings) {
                    Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.list_029), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider()

            NavigationDrawerItem(
                label = { Text(stringResource(R.string.list_030)) },
                selected = selectedFolder == "all" && !onlyFavorite && selectedCategory == null,
                onClick = onAll,
                icon = { Icon(Icons.Default.Description, contentDescription = null) },
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.list_031)) },
                selected = false,
                onClick = onPassword,
                icon = { Icon(Icons.Default.Password, contentDescription = null) },
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.memo_001)) },
                selected = false,
                onClick = onMemo,
                icon = { Icon(Icons.Default.StickyNote2, contentDescription = null) },
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.list_032)) },
                selected = false,
                onClick = onTrash,
                icon = { Icon(Icons.Default.Delete, contentDescription = null) },
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.edit_040)) },
                selected = onlyFavorite,
                onClick = onFavorites,
                icon = { Icon(Icons.Default.Star, contentDescription = null) },
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.list_033)) },
                selected = false,
                onClick = onToolbox,
                icon = { Icon(Icons.Default.BusinessCenter, contentDescription = null) },
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // 自动分类分组（对齐 Kori：折叠头为 NavigationDrawerItem + 箭头 + badge，AnimatedVisibility 展开）
            var catExpanded by remember { mutableStateOf(true) }
            NavigationDrawerItem(
                icon = {
                    Icon(
                        if (catExpanded) Icons.Default.KeyboardArrowDown
                        else Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.list_034),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                label = { Text(stringResource(R.string.list_035), fontWeight = FontWeight.SemiBold) },
                badge = { Text(categories.size.toString(), style = MaterialTheme.typography.labelMedium) },
                selected = false,
                onClick = { catExpanded = !catExpanded },
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            AnimatedVisibility(visible = catExpanded) {
                Column {
                    categories.forEach { (catKey, list) ->
                        val catName = categoryDisplayName(catKey)
                        NavigationDrawerItem(
                            label = { Text(catName, fontWeight = FontWeight.Normal, modifier = Modifier.height(44.dp).wrapContentHeight(Alignment.CenterVertically)) },
                            selected = selectedCategory == catKey,
                            onClick = { onSelectCategory(catKey) },
                            badge = { Text(list.size.toString(), style = MaterialTheme.typography.labelMedium) },
                            icon = {
                                Icon(
                                    categoryIcon(catKey),
                                    contentDescription = null
                                )
                            },
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // 文件夹分组（对齐 Kori：折叠头 NavigationDrawerItem + 箭头 + badge，AnimatedVisibility 展开）
            var folderExpanded by remember { mutableStateOf(false) }
            NavigationDrawerItem(
                icon = {
                    Icon(
                        if (folderExpanded) Icons.Default.KeyboardArrowDown
                        else Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.list_034),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                label = { Text(stringResource(R.string.list_021), fontWeight = FontWeight.SemiBold) },
                badge = { Text(folders.size.toString(), style = MaterialTheme.typography.labelMedium) },
                selected = false,
                onClick = { folderExpanded = !folderExpanded },
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            AnimatedVisibility(visible = folderExpanded) {
                Column {
                    folders.forEach { f ->
                        val selected = selectedFolder == f.id && !onlyFavorite && selectedCategory == null
                        NavigationDrawerItem(
                            label = { Text(folderDisplayName(f), fontWeight = FontWeight.Normal, modifier = Modifier.height(44.dp).wrapContentHeight(Alignment.CenterVertically)) },
                            selected = selected,
                            onClick = { onSelectFolder(f.id) },
                            icon = {
                                Icon(
                                    if (selected) Icons.Default.FolderOpen else Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                    TextButton(onClick = onManageFolders, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.list_036), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            NavigationDrawerItem(
                label = { Text(stringResource(R.string.web_001)) },
                selected = false,
                onClick = onOpenBrowser,
                icon = { Icon(Icons.Default.Public, contentDescription = null) },
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.list_037)) },
                selected = false,
                onClick = onOpenFiles,
                icon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 分类条目行：显示具体内容（链接/图片/文件/文字），而非整个笔记。 */
@Composable
fun CategoryItemRow(item: NoteRepository.CategoryItem, onClick: () -> Unit, onOpenNote: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surface)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
            Icon(
                when (NoteRepository.CategoryType.fromKey(item.kind)) {
                    NoteRepository.CategoryType.LINK -> Icons.Default.Link
                    NoteRepository.CategoryType.IMAGE -> Icons.Default.Image
                    NoteRepository.CategoryType.VIDEO -> Icons.Default.Videocam
                    NoteRepository.CategoryType.FILE -> Icons.Default.InsertDriveFile
                    NoteRepository.CategoryType.AUDIO -> Icons.Default.MusicNote
                    else -> Icons.Default.Notes
                },
                contentDescription = null,
                tint = scheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).clickable { onClick() }) {
                Text(item.text, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
                Text(item.note.title.ifBlank { stringResource(R.string.edit_030) }, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, maxLines = 1)
            }
            TextButton(onClick = onOpenNote) { Text(stringResource(R.string.list_038)) }
        }
    }
}
