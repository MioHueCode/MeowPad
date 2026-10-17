package com.miohue.notepad.data

import android.content.Context
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import com.miohue.notepad.R

data class Folder(val id: String, val name: String) {
    fun toJson(): JSONObject = JSONObject().apply { put("id", id); put("name", name) }

    companion object {
        /** 旧版本数据兼容：历史笔记/文件夹可能残留 "default" id，读取时过滤或归空（见 Note.fromJson 与导入逻辑）。 */
        const val DEFAULT_ID = "default"

        fun fromJson(json: JSONObject): Folder? = runCatching {
            Folder(json.getString("id"), json.optString("name", ""))
        }.getOrNull()
    }
}

/**
 * 笔记仓库：笔记存为 files/notes/<id>.json，回收站 files/trash/，文件夹 files/folders.json。
 * 备份格式 v2：{"version":2,"folders":[...],"notes":[...]}；兼容 v1（纯数组）。
 */
class NoteRepository(context: Context) {
    private val appContext = context.applicationContext
    private val dir = File(context.filesDir, "notes").apply { mkdirs() }
    private val trashDir = File(context.filesDir, "trash").apply { mkdirs() }
    private val foldersFile = File(context.filesDir, "folders.json")

    /** 根目录：默认 /storage/emulated/0/MeowPad，可用 prefs 的 storage_path（真实绝对路径）覆盖 */
    fun rootDir(): File {
        val prefs = appContext.getSharedPreferences("notepad_prefs", Context.MODE_PRIVATE)
        val configured = prefs.getString("storage_path", "").orEmpty()
        val root = if (configured.isBlank()) File("/storage/emulated/0/MeowPad") else File(configured)
        runCatching { root.mkdirs() }
        return root
    }

    /** 确保根目录下存在标准子文件夹（backup/images/videos/files/audios） */
    fun ensureFolders(): File {
        val root = rootDir()
        listOf("backup", "images", "videos", "files", "audios").forEach { name ->
            runCatching { File(root, name).mkdirs() }
        }
        return root
    }

    /** 备份目录：根目录下的 backup 子目录 */
    fun backupDir(): File {
        val d = File(rootDir(), "backup")
        runCatching { d.mkdirs() }
        return d
    }

    // ---------- 附件自动分类目录（跟随根目录选择） ----------

    /** 分类目录名：图片 / 文件 / 音频 */
    val categoryDirs = listOf("images", "videos", "files", "audios")

    /**
     * 真实写探测：只有能创建、读到非空内容并删除探针文件才算「可写」。
     * File.canWrite() 在部分 ROM / 作用域存储下会给出错误结果，不能作为落盘依据。
     */
    private fun writable(dir: File): Boolean = runCatching {
        if (!dir.isDirectory) return@runCatching false
        val probe = File(dir, ".np_write_probe")
        if (probe.exists()) probe.delete()
        probe.writeText("ok")
        val ok = probe.exists() && probe.length() > 0
        probe.delete()
        ok
    }.getOrDefault(false)

    /** 根目录当前是否真的可写（决定附件能否进入根目录下的对应分类目录） */
    fun rootWritable(): Boolean = writable(rootDir())

    /** 该文件是否真的位于当前根目录下（用于判断附件有没有「进入对应目录」） */
    fun inRoot(file: File?): Boolean {
        if (file == null) return false
        val prefix = rootDir().absolutePath.trimEnd('/') + "/"
        return file.absolutePath.startsWith(prefix)
    }

    /**
     * 取某个分类目录（images / files / audios）。
     * 优先落在用户选择的根目录下；只有根目录确实不可写（例如未授予「所有文件访问」权限）时，
     * 才回退到应用私有目录，保证附件功能不中断（此时分类仍然生效，但位置不在根目录）。
     */
    fun storageDir(sub: String): File {
        val external = File(rootDir(), sub)
        runCatching { external.mkdirs() }
        if (writable(external)) return external
        val internal = File(appContext.filesDir, sub)
        runCatching { internal.mkdirs() }
        return internal
    }

    /** 附件当前实际落盘位置描述（用于设置页展示） */
    fun storageDirLabel(sub: String): String = storageDir(sub).absolutePath

    /** 分类目录状态：给设置页展示「附件到底落在哪、是否可写」。 */
    data class StorageStatus(val sub: String, val path: String, val writable: Boolean, val inRoot: Boolean)

    fun storageStatus(): List<StorageStatus> = categoryDirs.map { sub ->
        val d = storageDir(sub)
        StorageStatus(sub, d.absolutePath, writable(d), inRoot(d))
    }

    /**
     * 把「没进入根目录」的附件整理进根目录对应的分类目录，并同步笔记里的引用路径。
     * 覆盖两种情况：①历史版本落在应用私有回退目录里的附件；②换过根目录后仍指向旧位置的附件。
     * 先复制成功、更新笔记，再删除源文件，避免中途失败丢数据。返回搬运的文件数。
     */
    fun migrateAttachmentsToRoot(): Int {
        val root = rootDir()
        if (!writable(root)) return 0
        val copied = mutableListOf<Pair<File, File>>()
        val remap = mutableMapOf<String, String>()

        fun copyInto(src: File): String? {
            if (!src.isFile) return null
            remap[src.absolutePath]?.let { return it }
            val sub = classifyDir(null, src.name)
            val target = File(root, sub).apply { runCatching { mkdirs() } }
            if (!writable(target)) return null
            if (src.parentFile?.absolutePath == target.absolutePath) return null
            val dest = uniqueFile(target, src.name)
            val ok = runCatching {
                src.inputStream().use { input -> dest.outputStream().use { out -> input.copyTo(out) } }
                dest.length() > 0
            }.getOrDefault(false)
            if (!ok) {
                runCatching { dest.delete() }
                return null
            }
            copied += src to dest
            remap[src.absolutePath] = dest.absolutePath
            return dest.absolutePath
        }

        // ① 应用私有回退目录里的历史附件
        categoryDirs.forEach { sub ->
            File(appContext.filesDir, sub).listFiles()?.forEach { copyInto(it) }
        }

        // ② 笔记里记录、但不在当前根目录下的附件
        list().forEach { n ->
            var changed = false
            val images = n.images.map { p ->
                if (inRoot(File(p))) p else copyInto(File(p))?.also { changed = true } ?: p
            }
            val audios = n.audios.map { p ->
                if (inRoot(File(p))) p else copyInto(File(p))?.also { changed = true } ?: p
            }
            val files = n.files.map { a ->
                if (inRoot(File(a.path))) a else copyInto(File(a.path))?.let { changed = true; a.copy(path = it) } ?: a
            }
            if (changed) save(n.copy(images = images, audios = audios, files = files))
        }

        // ③ 复制并落库成功后再清理源文件
        copied.forEach { (old, new) ->
            if (new.exists() && new.length() > 0) runCatching { old.delete() }
        }
        // 清理空的历史回退目录
        categoryDirs.forEach { sub ->
            val d = File(appContext.filesDir, sub)
            if (d.isDirectory && d.listFiles()?.isEmpty() == true) runCatching { d.delete() }
        }
        return copied.size
    }

    private val imageExts = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "svg", "avif")
    private val audioExts = setOf("mp3", "m4a", "aac", "wav", "ogg", "oga", "flac", "amr", "opus", "mid")
    private val videoExts = setOf("mp4", "mov", "avi", "mkv", "webm", "3gp", "3gpp", "m4v", "ts", "flv")

    /** 自动分类：根据 MIME 与扩展名决定附件应进入的分类目录 */
    fun classifyDir(mime: String?, name: String?): String {
        val m = (mime ?: "").lowercase()
        val ext = (name ?: "").substringAfterLast('.', "").lowercase()
        return when {
            m.startsWith("image/") || ext in imageExts -> "images"
            m.startsWith("video/") || ext in videoExts -> "videos"
            m.startsWith("audio/") || ext in audioExts -> "audios"
            else -> "files"
        }
    }

    private fun uniqueFile(dir: File, baseName: String): File {
        var candidate = File(dir, baseName)
        val dot = baseName.lastIndexOf('.')
        val stem = if (dot > 0) baseName.substring(0, dot) else baseName
        val ext = if (dot > 0) baseName.substring(dot) else ""
        var i = 1
        while (candidate.exists()) {
            candidate = File(dir, "$stem-$i$ext")
            i++
        }
        return candidate
    }

    /** 把一个输入流按自动分类写入对应目录，返回落盘文件；失败返回 null。 */
    fun importStream(name: String?, mime: String?, input: java.io.InputStream): File? = runCatching {
        val sub = classifyDir(mime, name)
        val raw = (name ?: "attachment").replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "attachment" }
        val safe = raw.take(60)
        val dest = uniqueFile(storageDir(sub), "${System.currentTimeMillis()}_$safe")
        dest.outputStream().use { out -> input.copyTo(out) }
        if (!dest.exists() || dest.length() == 0L) {
            runCatching { dest.delete() }
            null
        } else dest
    }.getOrNull()

    /** 从 content:// Uri 读取并自动分类落盘（编辑器附件统一入口）。 */
    fun importUri(uri: android.net.Uri, name: String?, mime: String?): File? = runCatching {
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            importStream(name, mime, input)
        }
    }.getOrNull()

    // ---------- 重置 ----------

    /** 重置数据：删除全部笔记、回收站内容与自定义文件夹（附件文件保留）。 */
    fun resetAll(): Int {
        var n = 0
        runCatching { dir.listFiles()?.forEach { if (it.delete()) n++ } }
        runCatching { trashDir.listFiles()?.forEach { it.delete() } }
        runCatching { if (foldersFile.exists()) foldersFile.delete() }
        invalidate()
        return n
    }

    // ---------- 笔记 ----------

    /** 全部未删除笔记：置顶在前，其余按 updatedAt 倒序。 */
    /**
     * 笔记列表。
     *
     * 性能说明：UI 里 `repo.list()` 被调用了 20+ 处（每次增删改后刷新、每个导航目的地重建）。
     * 原实现每次都 `listFiles()` + 逐个读 JSON + 解析，笔记多时明显卡顿。
     * 这里加一层内存缓存，用「目录 mtime + 文件数」作为失效信号：
     * 命中时零 IO；一旦外部或本进程改动文件，mtime 变化即自动失效。
     */
    @Volatile private var listCache: List<Note>? = null
    @Volatile private var listCacheStamp: Long = 0L
    @Volatile private var listCacheCount: Int = -1

    private fun dirStamp(): Pair<Long, Int> {
        val files = dir.listFiles { f -> f.isFile && f.extension == "json" } ?: return 0L to 0
        // 目录 mtime 在部分 ROM 上增删文件不更新，所以叠加「文件数 + 最新文件 mtime」
        val newest = files.maxOfOrNull { it.lastModified() } ?: 0L
        return (dir.lastModified() + newest) to files.size
    }

    fun list(): List<Note> {
        val (stamp, count) = dirStamp()
        val cached = listCache
        if (cached != null && stamp == listCacheStamp && count == listCacheCount) return cached
        val fresh = readNotes(dir)
        listCache = fresh
        listCacheStamp = stamp
        listCacheCount = count
        return fresh
    }

    /**
     * 让所有派生缓存失效（本进程写盘后调用，避免依赖 mtime 精度）。
     *
     * 注意：这里**不做 IO**。`scanCache` 的过期项交给 [purgeScanCache] 在拿到笔记列表后懒清理，
     * 否则每次 save 都要多跑一次 listFiles，反而抵消缓存收益。
     */
    private fun invalidate() {
        listCache = null
        listCacheStamp = 0L
        listCacheCount = -1
        itemsCache = null
        itemsCacheStamp = 0L
    }

    /** 清掉已不存在笔记的扫描缓存（key 是 note id）。 */
    private fun purgeScanCache(notes: List<Note>) {
        if (scanCache.size <= notes.size) return
        val alive = notes.mapTo(HashSet(notes.size)) { it.id }
        scanCache.keys.retainAll(alive)
    }

    @Volatile private var foldersCache: List<Folder>? = null
    @Volatile private var foldersCacheStamp: Long = -1L

    /** 文件夹缓存失效（写 folders.json 后调用）。 */
    private fun invalidateFolders() {
        foldersCache = null
        foldersCacheStamp = -1L
    }

    /**
     * 自动分类的稳定类别标识。
     * 注意：这里刻意不使用中文文案当 key（那样无法本地化），显示名由 UI 层按 [catKeyRes] 解析。
     */
    enum class CategoryType(val key: String) {
        LINK("link"), IMAGE("image"), VIDEO("video"), FILE("file"), AUDIO("audio"), CODE("code"), TEXT("text");

        companion object {
            fun fromKey(key: String?): CategoryType? = entries.firstOrNull { it.key == key }
        }
    }

    /** 自动分类统计：返回「类别 key -> 该类别笔记列表」。顺序固定：link/image/video/file/audio/code/text。 */
    fun categorize(): Map<String, List<Note>> {
        val notes = list()
        purgeScanCache(notes)
        val links = mutableListOf<Note>()
        val images = mutableListOf<Note>()
        val videos = mutableListOf<Note>()
        val files = mutableListOf<Note>()
        val audios = mutableListOf<Note>()
        val codes = mutableListOf<Note>()
        val texts = mutableListOf<Note>()
        notes.forEach { n ->
            // 复用 [scanOf] 的缓存，避免与 buildCategoryItems 重复扫同一篇笔记
            val scan = scanOf(n)
            if (scan.urls.isNotEmpty()) links.add(n)
            if (scan.imageUrls.isNotEmpty() || n.images.isNotEmpty()) images.add(n)
            if (n.videos.isNotEmpty()) videos.add(n)
            if (n.files.isNotEmpty()) files.add(n)
            if (n.audios.isNotEmpty()) audios.add(n)
            // 代码识别：含代码块标记或代码特征（函数/关键字/等宽结构）
            if (isCodeContent(n.content)) codes.add(n)
            if (n.content.isNotBlank() && scan.urls.isEmpty() && n.images.isEmpty() && n.videos.isEmpty() && n.files.isEmpty() && n.audios.isEmpty() && !isCodeContent(n.content)) texts.add(n)
        }
        return linkedMapOf(
            CategoryType.LINK.key to links,
            CategoryType.IMAGE.key to images,
            CategoryType.VIDEO.key to videos,
            CategoryType.FILE.key to files,
            CategoryType.AUDIO.key to audios,
            CategoryType.CODE.key to codes,
            CategoryType.TEXT.key to texts
        )
    }

    /** 判断正文是否含代码：代码块标记或代码特征（函数定义/关键字/缩进结构）。 */
    private fun isCodeContent(content: String): Boolean {
        if (content.isBlank()) return false
        // 代码块标记（markdown 或 HTML）
        if (content.contains("```") || content.contains("<code") || content.contains("<pre")) return true
        // 函数/类定义特征
        if (Regex("""\b(fun|def|function|public|private|class|interface|import|package)\s+\w+""").containsMatchIn(content)) return true
        // 常见代码关键字密集出现
        val kwCount = Regex("""\b(fun|val|var|if|else|for|while|return|import|class|def|function|const|let|var|print|return|null|true|false)\b""")
            .findAll(content).count()
        return kwCount >= 3
    }

    /** 自动分类的「具体内容条目」：每条是扫描出的具体链接/图片/文件，而非整个笔记。 */
    data class CategoryItem(val note: Note, val text: String, val kind: String, val data: String = "")

    /**
     * 单篇笔记的内容扫描缓存。
     * [categorize] 与 [buildCategoryItems] 都要扫描全部笔记，共享同一个缓存可省掉一半正则开销。
     * key 是 note id，value 附带 updatedAt 以便内容变更后自动失效。
     */
    private val scanCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, ContentScanner.ScanResult>>()

    private fun scanOf(n: Note): ContentScanner.ScanResult {
        val hit = scanCache[n.id]
        if (hit != null && hit.first == n.updatedAt) return hit.second
        val fresh = ContentScanner.scan(n.content)
        scanCache[n.id] = n.updatedAt to fresh
        return fresh
    }

    fun categorizeItems(categoryKey: String): List<CategoryItem> =
        categorizeItemsInternal(categoryKey, list())

    /**
     * 性能：分类页每次切换分类都会对全部笔记重跑正则扫描。
     * 这里把「一次扫描的全部结果」按类别缓存，切换分类时零重扫。
     * 缓存随 list 变化（notesStamp）自动失效。
     */
    @Volatile private var itemsCache: Map<String, List<CategoryItem>>? = null
    @Volatile private var itemsCacheStamp: Long = 0L

    private fun categorizeItemsInternal(categoryKey: String, notes: List<Note>): List<CategoryItem> {
        purgeScanCache(notes)
        val stamp = notes.size.toLong() * 31 + notes.sumOf { it.updatedAt }
        val cached = itemsCache
        if (cached != null && stamp == itemsCacheStamp) return cached[categoryKey] ?: emptyList()
        val all = buildCategoryItems(notes)
        itemsCache = all
        itemsCacheStamp = stamp
        return all[categoryKey] ?: emptyList()
    }

    private fun buildCategoryItems(notes: List<Note>): Map<String, List<CategoryItem>> {
        val result = LinkedHashMap<String, MutableList<CategoryItem>>()
        CategoryType.entries.forEach { result[it.key] = mutableListOf() }
        notes.forEach { n ->
            val scan = scanOf(n)
            if (scan.urls.isNotEmpty()) {
                scan.urls.forEach { result[CategoryType.LINK.key]!!.add(CategoryItem(n, it, CategoryType.LINK.key, it)) }
            }
            if (scan.imageUrls.isNotEmpty() || n.images.isNotEmpty()) {
                scan.imageUrls.forEach { result[CategoryType.IMAGE.key]!!.add(CategoryItem(n, it, CategoryType.IMAGE.key, it)) }
                n.images.forEach { result[CategoryType.IMAGE.key]!!.add(CategoryItem(n, it, CategoryType.IMAGE.key, it)) }
            }
            n.videos.forEach {
                result[CategoryType.VIDEO.key]!!.add(CategoryItem(n, it.substringAfterLast('/'), CategoryType.VIDEO.key, it))
            }
            n.files.forEach {
                result[CategoryType.FILE.key]!!.add(CategoryItem(n, it.name, CategoryType.FILE.key, it.path))
            }
            n.audios.forEach {
                result[CategoryType.AUDIO.key]!!.add(CategoryItem(n, it.substringAfterLast('/'), CategoryType.AUDIO.key, it))
            }
            if (isCodeContent(n.content)) {
                result[CategoryType.CODE.key]!!.add(CategoryItem(n, n.content.trim().take(80), CategoryType.CODE.key))
            }
            val t = n.content.trim()
            if (t.isNotBlank() && scan.urls.isEmpty() && !isCodeContent(n.content)) {
                result[CategoryType.TEXT.key]!!.add(CategoryItem(n, t.take(80), CategoryType.TEXT.key))
            }
        }
        return result
    }

    fun get(id: String): Note? =
        file(id).takeIf { it.exists() }?.let { runCatching { Note.fromJson(it.readText()) }.getOrNull() }

    fun save(note: Note) {
        file(note.id).writeText(note.toJsonString())
        invalidate()
    }

    fun deleteToTrash(id: String): Boolean =
        runCatching { file(id).renameTo(trashFile(id)) }.getOrDefault(false).also { if (it) invalidate() }

    fun delete(id: String): Boolean = file(id).delete().also { if (it) invalidate() }

    fun trashList(): List<Note> = readNotes(trashDir)

    fun restoreFromTrash(id: String): Boolean =
        runCatching { trashFile(id).renameTo(file(id)) }.getOrDefault(false).also { if (it) invalidate() }

    fun deleteForever(id: String): Boolean = trashFile(id).delete()

    fun emptyTrash(): Int {
        val files = trashDir.listFiles { f -> f.isFile && f.extension == "json" } ?: return 0
        var n = 0
        files.forEach { if (it.delete()) n++ }
        return n
    }

    // ---------- 文件夹 ----------

    fun folders(): List<Folder> {
        val stamp = foldersFile.takeIf { it.exists() }?.lastModified() ?: 0L
        val cached = foldersCache
        if (cached != null && stamp == foldersCacheStamp) return cached
        val saved = runCatching {
            val arr = JSONArray(foldersFile.takeIf { it.exists() }?.readText() ?: "[]")
            List(arr.length()) { i -> Folder.fromJson(arr.optJSONObject(i)) }.filterNotNull()
        }.getOrDefault(emptyList())
        // 对齐 Kori：无默认文件夹，folders() 只返回用户自建文件夹
        val result = mutableListOf<Folder>()
        saved.forEach { if (result.none { f -> f.id == it.id }) result.add(it) }
        foldersCache = result
        foldersCacheStamp = stamp
        return result
    }

    fun addFolder(name: String): Folder {
        val folder = Folder(UUID.randomUUID().toString(), name)
        persistFolders(folders() + folder)
        return folder
    }

    fun renameFolder(id: String, newName: String) {
        persistFolders(folders().map { if (it.id == id) it.copy(name = newName) else it })
    }

    /** 删除文件夹：对齐 Kori 语义，文件夹与其中的笔记一并删除（笔记移入回收站，可从回收站找回）。 */
    fun deleteFolder(id: String) {
        persistFolders(folders().filter { it.id != id })
        list().filter { it.folderId == id }.forEach { deleteToTrash(it.id) }
    }

    private fun persistFolders(list: List<Folder>) {
        val arr = JSONArray()
        list.filter { it.id != Folder.DEFAULT_ID }.forEach { arr.put(it.toJson()) }
        foldersFile.writeText(arr.toString())
        invalidateFolders()
    }

    // ---------- 备份 / 恢复 ----------

    /** 导出为 v2 格式 JSON（含文件夹），返回笔记条数。 */
    fun backupToFile(targetFile: File): Int {
        val obj = JSONObject()
        obj.put("version", 2)
        val fArr = JSONArray()
        folders().forEach { fArr.put(it.toJson()) }
        obj.put("folders", fArr)
        val nArr = JSONArray()
        list().forEach { nArr.put(it.toJson()) }
        obj.put("notes", nArr)
        targetFile.parentFile?.mkdirs()
        targetFile.writeText(obj.toString())
        return nArr.length()
    }

    /** 从 JSON 恢复；兼容 v1 纯数组与 v2 对象，返回恢复的笔记条数。 */
    fun restoreFromFile(sourceFile: File): Int {
        if (!sourceFile.exists()) return 0
        val text = sourceFile.readText().trim()
        var n = 0
        runCatching {
            if (text.startsWith("{")) {
                val obj = JSONObject(text)
                // 恢复文件夹（忽略默认）
                obj.optJSONArray("folders")?.let { fArr ->
                    val existing = folders().map { it.id }.toSet()
                    val incoming = List(fArr.length()) { i -> Folder.fromJson(fArr.optJSONObject(i)) }.filterNotNull()
                        .filter { it.id != Folder.DEFAULT_ID && it.id !in existing }
                    if (incoming.isNotEmpty()) persistFolders(folders() + incoming)
                }
                obj.optJSONArray("notes")?.let { nArr ->
                    for (i in 0 until nArr.length()) {
                        Note.fromJson(nArr.optJSONObject(i))?.let { save(it); n++ }
                    }
                }
            } else {
                val arr = JSONArray(text)
                for (i in 0 until arr.length()) {
                    Note.fromJson(arr.optJSONObject(i))?.let { save(it); n++ }
                }
            }
        }
        if (n > 0) invalidate()
        return n
    }

    // ---------- 同步与云端 ----------

    /** 导出到根目录 /backup/backup_<时间戳>.json，返回笔记条数。 */
    fun backup(context: Context): Int {
        val dir = backupDir()
        val target = File(dir, "backup_${System.currentTimeMillis()}.json")
        dir.mkdirs()
        return backupToFile(target)
    }

    /** 上传当前备份到 WebDAV，返回是否成功。 */
    fun cloudBackup(context: Context, url: String, user: String, pass: String): Boolean {
        if (url.isBlank()) return false
        val file = backupDir().listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.sortedBy { it.lastModified() }?.lastOrNull() ?: return false
        val client = OkHttpClient.Builder().build()
        val credentials = if (user.isBlank()) "" else Credentials.basic(user, pass)
        val req = Request.Builder().url(url).put(file.readBytes().toRequestBody())
            .header("Authorization", credentials).header("Content-Type", "application/json").build()
        return runCatching { client.newCall(req).execute().use { it.body?.string() } }.isSuccess
    }

    /** 从 WebDAV 下载备份并恢复，返回是否成功。 */
    fun cloudRestore(context: Context, url: String, user: String, pass: String): Boolean {
        if (url.isBlank()) return false
        val client = OkHttpClient.Builder().build()
        val credentials = if (user.isBlank()) "" else Credentials.basic(user, pass)
        val req = Request.Builder().url(url).get()
            .header("Authorization", credentials).header("Accept", "application/json").build()
        return runCatching {
            val req2 = Request.Builder().url(url).get()
                .header("Authorization", credentials).header("Accept", "application/json").build()
            client.newCall(req2).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching false
                val tmp = File(context.cacheDir, "cloud_restore.json")
                tmp.outputStream().use { out -> resp.body?.byteStream()?.copyTo(out) }
                restoreFromFile(tmp)
            }
        }.isSuccess
    }

    private fun readNotes(from: File): List<Note> =
        from.listFiles { f -> f.isFile && f.extension == "json" }
            ?.mapNotNull { runCatching { Note.fromJson(it.readText()) }.getOrNull() }
            ?.sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt })
            ?: emptyList()

    private fun file(id: String) = File(dir, "$id.json")
    private fun trashFile(id: String) = File(trashDir, "$id.json")
}
