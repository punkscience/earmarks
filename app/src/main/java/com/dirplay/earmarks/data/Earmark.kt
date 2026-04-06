package com.dirplay.earmarks.data

import org.json.JSONArray
import org.json.JSONObject

data class Chunk(
    val index: Int,
    val sha256: String,
    val size: Int,
    val servers: List<String>
)

data class BlossomManifest(
    val key: String,
    val ext: String,
    val chunks: List<Chunk>
)

data class Earmark(
    val artist: String,
    val album: String,
    val title: String,
    val ts: Long,
    val blossom: BlossomManifest?
)

fun parseEarmarkList(json: String): List<Earmark> {
    val arr = JSONArray(json)
    return (0 until arr.length()).mapNotNull { i ->
        try {
            val obj = arr.getJSONObject(i)
            val blossomObj = if (obj.isNull("blossom")) null else obj.optJSONObject("blossom")
            val blossom = blossomObj?.let { b ->
                val chunksArr = b.getJSONArray("chunks")
                BlossomManifest(
                    key = b.getString("key"),
                    ext = b.getString("ext"),
                    chunks = (0 until chunksArr.length()).map { j ->
                        val c = chunksArr.getJSONObject(j)
                        val serversArr = c.getJSONArray("servers")
                        Chunk(
                            index = c.getInt("index"),
                            sha256 = c.getString("sha256"),
                            size = c.getInt("size"),
                            servers = (0 until serversArr.length()).map { k -> serversArr.getString(k) }
                        )
                    }
                )
            }
            Earmark(
                artist = obj.optString("artist", ""),
                album = obj.optString("album", ""),
                title = obj.optString("title", ""),
                ts = obj.getLong("ts"),
                blossom = blossom
            )
        } catch (_: Exception) { null }
    }.filter { it.blossom != null }
}
