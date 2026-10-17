package com.miohue.notepad.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** 附件：图片或文件 */
data class Attachment(
    val path: String,
    val name: String,
    val type: AttachmentType
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("path", path); put("name", name); put("type", type.name)
    }

    companion object {
        fun fromJson(json: JSONObject): Attachment? = runCatching {
            Attachment(
                path = json.getString("path"),
                name = json.optString("name", ""),
                type = runCatching { AttachmentType.valueOf(json.optString("type", "IMAGE")) }.getOrDefault(AttachmentType.IMAGE)
            )
        }.getOrNull()
    }
}

enum class AttachmentType { IMAGE, FILE }

data class Note(
    val id: String,
    val title: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
    val tags: List<String> = emptyList(),
    val color: Int = 0xFF1976D2.toInt(),
    val pinned: Boolean = false,
    val folderId: String = "",
    val favorite: Boolean = false,
    val images: List<String> = emptyList(),
    val videos: List<String> = emptyList(),
    val files: List<Attachment> = emptyList(),
    val audios: List<String> = emptyList(),
    val type: String = "text", // text | todo | code（对齐 Kori NoteType，code 驱动编辑器语法高亮模式）
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("content", content)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("tags", JSONArray(tags))
        put("color", color)
        put("pinned", pinned)
        put("folderId", folderId)
        put("favorite", favorite)
        put("images", JSONArray(images))
        put("videos", JSONArray(videos))
        put("files", JSONArray().apply { files.forEach { put(it.toJson()) } })
        put("audios", JSONArray(audios))
        put("type", type)
    }

    fun toJsonString(): String = toJson().toString()

    companion object {
        fun new(folderId: String = ""): Note {
            val now = System.currentTimeMillis()
            val id = UUID.randomUUID().toString()
            return Note(id = id, title = "", content = "", createdAt = now, updatedAt = now, folderId = folderId)
        }

        fun fromJson(json: JSONObject): Note? = runCatching {
            Note(
                id = json.getString("id"),
                title = json.optString("title", ""),
                content = json.optString("content", ""),
                createdAt = json.optLong("createdAt"),
                updatedAt = json.optLong("updatedAt"),
                tags = json.optJSONArray("tags")?.let { arr -> List(arr.length()) { i -> arr.optString(i) } } ?: emptyList(),
                color = json.optInt("color", 0xFF1976D2.toInt()),
                pinned = json.optBoolean("pinned", false),
                folderId = json.optString("folderId", "").let { if (it == "default") "" else it },
                favorite = json.optBoolean("favorite", false),
                images = json.optJSONArray("images")?.let { arr -> List(arr.length()) { i -> arr.optString(i) } } ?: emptyList(),
                videos = json.optJSONArray("videos")?.let { arr -> List(arr.length()) { i -> arr.optString(i) } } ?: emptyList(),
                files = json.optJSONArray("files")?.let { arr -> List(arr.length()) { i -> Attachment.fromJson(arr.optJSONObject(i)) }.filterNotNull() } ?: emptyList(),
                audios = json.optJSONArray("audios")?.let { arr -> List(arr.length()) { i -> arr.optString(i) } } ?: emptyList(),
                type = json.optString("type", "text")
            )
        }.getOrNull()

        fun fromJson(text: String): Note? = runCatching { fromJson(JSONObject(text)) }.getOrNull()
    }
}
