package dev.abdullah.openlock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val store = ConfigStore(context)
        if (store.enabled && store.hasPin() && Access.ready(context)) {
            runCatching { context.startForegroundService(Intent(context, OpenLockMonitorService::class.java)) }
        }
    }
}
