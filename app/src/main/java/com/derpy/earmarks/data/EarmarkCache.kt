package com.derpy.earmarks.data

import android.content.Context
import java.io.File

class EarmarkCache(context: Context) {
    private val cacheDir = File(context.cacheDir, "earmarks").also { it.mkdirs() }

    fun listCachedTs(): Set<Long> =
        cacheDir.listFiles()
            ?.mapNotNull { f -> f.nameWithoutExtension.removePrefix("earmark_").toLongOrNull() }
            ?.toSet()
            ?: emptySet()

    fun getCachedFile(earmark: Earmark): File? =
        targetFile(earmark).takeIf { it.exists() && it.length() > 0 }

    fun targetFile(earmark: Earmark): File =
        File(cacheDir, "earmark_${earmark.ts}${earmark.blossom!!.ext}")

    fun pruneExpired(activeTsList: Set<Long>) {
        cacheDir.listFiles()?.forEach { file ->
            val ts = file.nameWithoutExtension.removePrefix("earmark_").toLongOrNull()
            if (ts != null && ts !in activeTsList) file.delete()
        }
    }
}
