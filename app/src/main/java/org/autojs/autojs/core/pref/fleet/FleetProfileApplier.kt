package org.autojs.autojs.core.pref.fleet

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.widget.Toast
import androidx.core.content.edit
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.util.StringUtils.key
import org.autojs.autojs6.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream

/**
 * Applies a fleet/headless configuration profile to AutoJs6's default SharedPreferences.
 *
 * Profiles are JSON objects mapping preference keys to typed values. Supported types:
 * - boolean  -> putBoolean
 * - string   -> putString
 * - int      -> putInt
 * - long     -> putLong
 * - float    -> putFloat
 * - string[] -> putStringSet
 *
 * Known preference keys are exported in R.string.key_* and resolve to keys like
 * "key_$_foreground_service". The profile can use either the raw key string or
 * a short alias from the key alias table (e.g. "foreground_service").
 *
 * Example profile:
 * {
 *   "foreground_service": true,
 *   "floating_menu_shown": false,
 *   "enable_a11y_service_with_secure_settings": true,
 *   "stable_mode": true,
 *   "guard_mode": true,
 *   "restart_strategy": "quick",
 *   "file_extensions": "show_all"
 * }
 */
object FleetProfileApplier {

    private const val KEY_PREFIX = "key_$_"

    private val aliasToKey by lazy {
        mapOf(
            "foreground_service" to key(R.string.key_foreground_service),
            "floating_menu_shown" to key(R.string.key_floating_menu_shown),
            "a11y_service" to key(R.string.key_a11y_service),
            "enable_a11y_service_with_root_access" to key(R.string.key_enable_a11y_service_with_root_access),
            "enable_a11y_service_with_secure_settings" to key(R.string.key_enable_a11y_service_with_secure_settings),
            "stable_mode" to key(R.string.key_stable_mode),
            "guard_mode" to key(R.string.key_guard_mode),
            "use_volume_control_running" to key(R.string.key_use_volume_control_running),
            "use_volume_control_record" to key(R.string.key_use_volume_control_record),
            "record_toast" to key(R.string.key_record_toast),
            "extending_js_build_in_objects" to key(R.string.key_extending_js_build_in_objects),
            "rhino_java_primitive_wrap" to key(R.string.key_rhino_java_primitive_wrap),
            "auto_check_for_updates" to key(R.string.key_auto_check_for_updates),
            "post_notifications_permission" to key(R.string.key_post_notifications_permission),
            "display_over_other_apps" to key(R.string.key_display_over_other_apps),
            "all_files_access" to key(R.string.key_all_files_access),
            "root_mode" to key(R.string.key_root_mode),
            "restart_strategy" to key(R.string.key_restart_strategy),
            "timed_task_backend" to key(R.string.key_timed_task_backend),
            "scheduled_restart_backend" to key(R.string.key_scheduled_restart_backend),
            "night_mode" to key(R.string.key_night_mode),
            "app_language" to key(R.string.key_app_language),
            "theme_color" to key(R.string.key_theme_color),
            "editor_theme" to key(R.string.key_editor_theme),
            "editor_text_size" to key(R.string.key_editor_text_size),
            "screen_capture_request_delay" to key(R.string.key_screen_capture_request_delay),
            "file_extensions" to key(R.string.key_file_extensions),
            "hidden_files" to key(R.string.key_hidden_files),
            "working_directory" to key(R.string.key_working_directory),
            "documentation_source" to key(R.string.key_documentation_source),
            "server_address" to key(R.string.key_server_address),
            "client_socket_normally_closed" to key(R.string.key_client_socket_normally_closed),
            "server_socket_normally_closed" to key(R.string.key_server_socket_normally_closed),
            "gesture_observing" to key(R.string.key_gesture_observing),
            "launcher_icon" to key(R.string.key_launcher_icon),
            "keep_screen_on_when_in_foreground" to key(R.string.key_keep_screen_on_when_in_foreground),
        )
    }

    data class Result(
        val success: Boolean,
        val appliedCount: Int,
        val skippedCount: Int,
        val errors: List<String>,
        val message: String,
    )

    /**
     * Apply a fleet profile from a JSON string.
     */
    @JvmStatic
    fun applyJson(context: Context, json: String): Result {
        return try {
            val profile = JSONObject(json)
            applyProfile(context, profile)
        } catch (e: Exception) {
            Result(false, 0, 0, listOf(e.message ?: "Invalid JSON"), "Profile parse failed: ${e.message}")
        }
    }

    /**
     * Apply a fleet profile read from a local file path.
     */
    @JvmStatic
    fun applyFromPath(context: Context, path: String): Result {
        return try {
            val json = File(path).readText(Charsets.UTF_8)
            applyJson(context, json)
        } catch (e: Exception) {
            Result(false, 0, 0, listOf(e.message ?: "Read error"), "Failed to read $path: ${e.message}")
        }
    }

    /**
     * Apply a fleet profile read from a content URI.
     */
    @JvmStatic
    fun applyFromUri(context: Context, uri: Uri): Result {
        return try {
            val stream = context.contentResolver.openInputStream(uri)
                ?: return Result(false, 0, 0, listOf("Cannot open URI"), "Cannot open URI: $uri")
            val json = stream.use { it.reader(Charsets.UTF_8).readText() }
            applyJson(context, json)
        } catch (e: Exception) {
            Result(false, 0, 0, listOf(e.message ?: "URI error"), "Failed to read URI $uri: ${e.message}")
        }
    }

    private fun applyProfile(context: Context, profile: JSONObject): Result {
        val errors = mutableListOf<String>()
        val pref = Pref.get()
        val meta = profile.optJSONObject("_meta")
        val clearExisting = meta?.optBoolean("clear_existing", false) ?: false

        var applied = 0
        var skipped = 0

        pref.edit(commit = true) {
            if (clearExisting) {
                clear()
            }

            val keys = profile.keys()
            while (keys.hasNext()) {
                val rawKey = keys.next()
                if (rawKey.startsWith("_")) {
                    continue
                }
                val prefKey = resolveKey(rawKey)
                if (prefKey == null) {
                    skipped++
                    errors.add("Unknown key: $rawKey")
                    continue
                }

                val value = profile.get(rawKey)
                try {
                    putValue(this, prefKey, value)
                    applied++
                } catch (e: Exception) {
                    skipped++
                    errors.add("$rawKey: ${e.message}")
                }
            }
        }

        val message = "Applied $applied preferences, skipped $skipped" +
                if (errors.isEmpty()) "" else " (${errors.size} errors)"

        return Result(errors.isEmpty(), applied, skipped, errors, message)
    }

    private fun resolveKey(rawKey: String): String? {
        if (rawKey.startsWith(KEY_PREFIX)) {
            return rawKey
        }
        return aliasToKey[rawKey]
    }

    private fun putValue(editor: SharedPreferences.Editor, key: String, value: Any?) {
        when (value) {
            is Boolean -> editor.putBoolean(key, value)
            is String -> editor.putString(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Double -> editor.putFloat(key, value.toFloat())
            is Float -> editor.putFloat(key, value)
            is JSONArray -> {
                val set = LinkedHashSet<String>()
                for (i in 0 until value.length()) {
                    set.add(value.getString(i))
                }
                editor.putStringSet(key, set)
            }
            else -> throw IllegalArgumentException("Unsupported type: ${value?.javaClass?.simpleName}")
        }
    }

}
