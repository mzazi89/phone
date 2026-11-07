package com.mzazi.phone

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.TelecomManager

/** Helpers for becoming (and checking) the system's default phone app. */
object DialerRole {

    fun isDefault(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roles = context.getSystemService(RoleManager::class.java)
                roles?.isRoleHeld(RoleManager.ROLE_DIALER) == true
            } else {
                val telecom = context.getSystemService(TelecomManager::class.java)
                telecom?.defaultDialerPackage == context.packageName
            }
        } catch (e: Exception) {
            false
        }
    }

    /** Intent that asks the user to make this app the default phone app. */
    fun requestIntent(context: Context): Intent? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roles = context.getSystemService(RoleManager::class.java) ?: return null
                if (!roles.isRoleAvailable(RoleManager.ROLE_DIALER)) return null
                roles.createRequestRoleIntent(RoleManager.ROLE_DIALER)
            } else {
                Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).putExtra(
                    TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME,
                    context.packageName
                )
            }
        } catch (e: Exception) {
            null
        }
    }
}
