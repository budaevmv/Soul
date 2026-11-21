package com.travelplanner.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.travelplanner.app.TravelApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** После перезагрузки телефона / обновления приложения будильники нужно восстановить. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext as TravelApp
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.container.repository.rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }
}
