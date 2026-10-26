package dev.abdullah.openlock

import android.app.AppOpsManager
import android.content.Context
import android.os.Process
import android.provider.Settings

object Access {
    fun usage(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    }
    fun ready(context: Context): Boolean = usage(context) && Settings.canDrawOverlays(context)
}
