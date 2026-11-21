package com.travelplanner.app.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.travelplanner.app.MainActivity
import com.travelplanner.app.R
import com.travelplanner.app.TravelApp
import com.travelplanner.app.ui.Format
import com.travelplanner.app.ui.fromCity
import com.travelplanner.app.ui.title
import com.travelplanner.app.ui.toCity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1)
        val kind = intent.getIntExtra(EXTRA_KIND, 0)
        if (id < 0) return
        val pending = goAsync()
        val app = context.applicationContext as TravelApp
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val c = app.container
                when (kind) {
                    ReminderScheduler.KIND_GO, ReminderScheduler.KIND_CHECKIN -> {
                        val f = c.db.flights().get(id) ?: return@launch
                        val ref = c.ref
                        val from = ref.airport(f.fromIata)
                        val depTime = Format.time(f.departureMillis, f.departureZone)
                        val route = "${f.fromCity(ref)} → ${f.toCity(ref)}"
                        val flightNo = f.title()
                        if (kind == ReminderScheduler.KIND_GO && f.isTrain) {
                            val place = listOfNotNull(f.car.takeIf { it.isNotBlank() }?.let { "вагон $it" }, f.seat.takeIf { it.isNotBlank() }?.let { "место $it" })
                            notify(
                                context, f.tripId, (id * 8 + kind).toInt(),
                                "Пора выезжать на вокзал",
                                "$flightNo $route, отправление в $depTime.\n${f.fromName}" +
                                    (if (place.isNotEmpty()) ", ${place.joinToString(", ")}." else "."),
                            )
                        } else if (kind == ReminderScheduler.KIND_GO) {
                            val terminal = f.departureTerminal.takeIf { it.isNotBlank() }?.let { ", терминал $it" } ?: ""
                            val airline = ref.airline(f.airlineCode)
                            val closes = airline?.airportCheckinCloseMinutes?.let { "\nРегистрация закрывается за $it мин до вылета." } ?: ""
                            notify(
                                context, f.tripId, (id * 8 + kind).toInt(),
                                "Пора выезжать в аэропорт",
                                "$flightNo $route, вылет в $depTime.\n${from?.title ?: f.fromIata}$terminal.$closes",
                            )
                        } else {
                            val site = ref.airline(f.airlineCode)?.web?.let { "\n$it" } ?: ""
                            notify(
                                context, f.tripId, (id * 8 + kind).toInt(),
                                "Открыта онлайн-регистрация",
                                "$flightNo $route, вылет ${Format.dateTime(f.departureMillis, f.departureZone)}.$site",
                            )
                        }
                    }
                    ReminderScheduler.KIND_STAY_CHECKIN -> {
                        val s = c.db.stays().get(id) ?: return@launch
                        notify(
                            context, s.tripId, (id * 8 + kind).toInt(),
                            "Сегодня заселение: ${s.name}",
                            "С ${Format.time(s.checkInMillis, s.zone)}. ${s.address}".trim() +
                                (s.bookingRef.takeIf { it.isNotBlank() }?.let { "\nБронь: $it" } ?: ""),
                        )
                    }
                    ReminderScheduler.KIND_STAY_CHECKOUT -> {
                        val s = c.db.stays().get(id) ?: return@launch
                        notify(
                            context, s.tripId, (id * 8 + kind).toInt(),
                            "Сегодня выезд: ${s.name}",
                            "Расчётный час — ${Format.time(s.checkOutMillis, s.zone)}. Проверьте, не забыли ли вещи, паспорт и зарядки.",
                        )
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_ID = "id"
        const val EXTRA_KIND = "kind"
        const val CHANNEL_ID = "trip_alerts"

        fun createChannel(context: Context) {
            val channel = NotificationChannel(CHANNEL_ID, "Напоминания о поездках", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Пора в аэропорт, онлайн-регистрация, выезд из отеля"
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        fun notify(context: Context, tripId: Long, notificationId: Int, title: String, text: String) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            val open = Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_TRIP_ID, tripId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pi = PendingIntent.getActivity(
                context, notificationId, open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val n = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text.lineSequence().first())
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()
            NotificationManagerCompat.from(context).notify(notificationId, n)
        }
    }
}
