package dev.abdullah.openlock

import android.app.AppOpsManager
import android.content.Context
import android.os.Process
import android.provider.Settings
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityManager

object Access {
    fun usage(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    }
    fun fastEnabled(context: Context): Boolean = context.getSystemService(AccessibilityManager::class.java)
        .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any { it.resolveInfo.serviceInfo.packageName == context.packageName && it.resolveInfo.serviceInfo.name == FastLockService::class.java.name }
    fun ready(context: Context): Boolean = fastEnabled(context) || (usage(context) && Settings.canDrawOverlays(context))
}
