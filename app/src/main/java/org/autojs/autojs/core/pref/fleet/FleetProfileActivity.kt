package org.autojs.autojs.core.pref.fleet

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.Nullable

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
 * Extras:
 *   - profile_path: absolute path to a JSON profile on shared storage
 *   - silent:       if true, do not show result Toast
 *
 * The activity is exported so fleet orchestrators (stayturgid, MDM, provisioning
 * tools) can call it before the user opens the app. Profile files should be
 * placed on shared storage; AutoJs6 must have READ_EXTERNAL_STORAGE or
 * MANAGE_EXTERNAL_STORAGE as needed.
 */
class FleetProfileActivity : Activity() {

    companion object {
        const val ACTION_APPLY_FLEET_PROFILE = "org.autojs.autojs6.action.APPLY_FLEET_PROFILE"
        const val EXTRA_PROFILE_PATH = "profile_path"
        const val EXTRA_SILENT = "silent"
    }

    override fun onCreate(@Nullable savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val result = handleIntent(intent)
        if (!intent.getBooleanExtra(EXTRA_SILENT, false)) {
            Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
        }
        setResult(if (result.success) RESULT_OK else RESULT_CANCELED)
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
                    false, 0, 0,
                    listOf("Missing profile_path or data URI"),
                    "Missing profile_path or data URI"
                )
            }
        } catch (e: Exception) {
            FleetProfileApplier.Result(
                false, 0, 0,
                listOf(e.message ?: "Unknown error"),
                "Failed to apply profile: ${e.message}"
            )
        }
    }

}
