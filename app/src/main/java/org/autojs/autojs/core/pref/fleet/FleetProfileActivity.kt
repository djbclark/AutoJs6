package org.autojs.autojs.core.pref.fleet

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.annotation.Nullable
import org.json.JSONObject
import java.io.File

/**
 * Entry point for applying a fleet/headless configuration profile without UI.
 *
 * Invocation examples:
 *
 *   am start -a org.autojs.autojs6.action.APPLY_FLEET_PROFILE \
 *            -e profile_path /sdcard/autojs6-fleet.json \
 *            org.autojs.autojs.core.pref.fleet.FleetProfileActivity
 *
 *   am start -a org.autojs.autojs6.action.APPLY_FLEET_PROFILE \
 *            -d file:///sdcard/autojs6-fleet.json \
 *            org.autojs.autojs.core.pref.fleet.FleetProfileActivity
 *
 * Input extras:
 *   - profile_path:  absolute path to a JSON profile on shared storage
 *   - silent:        if true, do not show result Toast
 *   - result_path:   override path for the JSON result file
 *                    (default: alongside profile or /sdcard/autojs6-fleet-result.json)
 *
 * Result delivery (all three fire on every invocation):
 *   1. Activity result intent — extras listed below (startActivityForResult callers)
 *   2. Broadcast — action FLEET_PROFILE_RESULT with same extras
 *   3. JSON result file — written to result_path (default
 *      /sdcard/autojs6-fleet-result.json)
 *
 * Result extras (on activity result intent AND broadcast):
 *   - result_success:       boolean
 *   - result_applied_count:  int
 *   - result_skipped_count:  int
 *   - result_applied_keys:   String[] — key aliases that were written
 *   - result_failed_keys:    String[] — key aliases that could not be written
 *   - result_errors:         String[] — human-readable error messages
 *   - result_message:        String  — summary string (also shown as Toast)
 *
 * The activity is exported so fleet orchestrators (stayturgid, MDM, provisioning
 * tools) can call it before the user opens the app. Profile files should be
 * placed on shared storage; AutoJs6 must have READ_EXTERNAL_STORAGE or
 * MANAGE_EXTERNAL_STORAGE as needed.
 */
class FleetProfileActivity : Activity() {

    companion object {
        const val ACTION_APPLY_FLEET_PROFILE = "org.autojs.autojs6.action.APPLY_FLEET_PROFILE"
        const val ACTION_FLEET_PROFILE_RESULT = "org.autojs.autojs6.action.FLEET_PROFILE_RESULT"
        const val EXTRA_PROFILE_PATH = "profile_path"
        const val EXTRA_RESULT_PATH = "result_path"
        const val EXTRA_SILENT = "silent"
        const val EXTRA_RESULT_SUCCESS = "result_success"
        const val EXTRA_RESULT_APPLIED_COUNT = "result_applied_count"
        const val EXTRA_RESULT_SKIPPED_COUNT = "result_skipped_count"
        const val EXTRA_RESULT_APPLIED_KEYS = "result_applied_keys"
        const val EXTRA_RESULT_FAILED_KEYS = "result_failed_keys"
        const val EXTRA_RESULT_ERRORS = "result_errors"
        const val EXTRA_RESULT_MESSAGE = "result_message"

        private const val DEFAULT_RESULT_FILENAME = "autojs6-fleet-result.json"
    }

    override fun onCreate(@Nullable savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val result = handleIntent(intent)
        if (!intent.getBooleanExtra(EXTRA_SILENT, false)) {
            Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
        }

        val resultIntent = buildResultIntent(result)
        setResult(if (result.success) RESULT_OK else RESULT_CANCELED, resultIntent)
        sendBroadcast(resultIntent.setAction(ACTION_FLEET_PROFILE_RESULT))
        writeResultFile(result)

        finish()
    }

    private fun handleIntent(intent: Intent): FleetProfileApplier.Result {
        return try {
            val data = intent.data
            val path = intent.getStringExtra(EXTRA_PROFILE_PATH)

            when {
                data != null -> FleetProfileApplier.applyFromUri(this, data)
                path != null -> FleetProfileApplier.applyFromPath(this, path)
                else -> FleetProfileApplier.Result(
                    success = false, appliedCount = 0, skippedCount = 0,
                    appliedKeys = emptyList(), failedKeys = emptyList(),
                    errors = listOf("Missing profile_path or data URI"),
                    message = "Missing profile_path or data URI"
                )
            }
        } catch (e: Exception) {
            FleetProfileApplier.Result(
                success = false, appliedCount = 0, skippedCount = 0,
                appliedKeys = emptyList(), failedKeys = emptyList(),
                errors = listOf(e.message ?: "Unknown error"),
                message = "Failed to apply profile: ${e.message}"
            )
        }
    }

    private fun buildResultIntent(result: FleetProfileApplier.Result): Intent = Intent().apply {
        putExtra(EXTRA_RESULT_SUCCESS, result.success)
        putExtra(EXTRA_RESULT_APPLIED_COUNT, result.appliedCount)
        putExtra(EXTRA_RESULT_SKIPPED_COUNT, result.skippedCount)
        putExtra(EXTRA_RESULT_APPLIED_KEYS, result.appliedKeys.toTypedArray())
        putExtra(EXTRA_RESULT_FAILED_KEYS, result.failedKeys.toTypedArray())
        putExtra(EXTRA_RESULT_ERRORS, result.errors.toTypedArray())
        putExtra(EXTRA_RESULT_MESSAGE, result.message)
    }

    private fun writeResultFile(result: FleetProfileApplier.Result) {
        val file = resolveResultFile()
        try {
            file.parentFile?.mkdirs()
            file.writeText(result.toJson().toString(2), Charsets.UTF_8)
        } catch (_: Exception) {
        }
    }

    private fun resolveResultFile(): File {
        intent.getStringExtra(EXTRA_RESULT_PATH)?.let { return File(it) }
        intent.getStringExtra(EXTRA_PROFILE_PATH)?.let { path ->
            val parent = File(path).parentFile
            if (parent != null && parent.exists()) {
                return File(parent, DEFAULT_RESULT_FILENAME)
            }
        }
        return File(
            Environment.getExternalStorageDirectory(),
            DEFAULT_RESULT_FILENAME
        )
    }

}
