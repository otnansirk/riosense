package id.bangsadigital.riosense.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import id.bangsadigital.riosense.MainActivity

/**
 * Receiver untuk menyalakan aplikasi RioSense secara otomatis (Auto-Up)
 * saat Head Unit Android Orca Eco Plus menyala bersama kontak ACC/Mesin.
 */
class BootUpReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (
            action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON" ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            context.startActivity(launchIntent)
        }
    }
}
