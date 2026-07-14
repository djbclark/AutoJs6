package org.autojs.autojs.util

import android.os.Environment
import java.io.File

object EnvironmentUtils {

    val externalStorageDirectory: File
        get() = Environment.getExternalStorageDirectory()

    @JvmStatic
    val externalStoragePath: String
        get() = externalStorageDirectory.path

    /**
     * Normalize a file path that starts with /sdcard (or legacy /mnt/sdcard) to the
     * canonical external storage path returned by Environment.getExternalStorageDirectory().
     *
     * On standard Android, /sdcard is a symlink to /storage/emulated/0, so File(path).exists()
     * returns true and no normalization occurs. On devices without the symlink
     * (Fire OS, some custom ROMs), the path is remapped to the real storage root.
     *
     * Returns null when path is null; returns the original path when the file exists
     * at that path or the normalized path doesn't resolve.
     */
    @JvmStatic
    fun normalizePath(path: String?): String? {
        if (path == null) return null
        if (File(path).exists()) return path
        val externalPath = Environment.getExternalStorageDirectory().absolutePath
        if (externalPath == "/sdcard") return path
        val normalized = path.replaceFirst(
            Regex("^/sdcard(?=/|$)"),
            externalPath
        ).replaceFirst(
            Regex("^/mnt/sdcard(?=/|$)"),
            externalPath
        )
        return if (normalized != path && File(normalized).exists()) normalized else path
    }

}