package com.travelplanner.app.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.travelplanner.app.data.Flight
import com.travelplanner.app.data.Settings
import com.travelplanner.app.data.Stay
import com.travelplanner.app.reference.ReferenceData

/**
 * Планирует уведомления:
 *  - «Пора выезжать в аэропорт» — за N часов до вылета (по умолчанию 3);
 *  - «Открыта онлайн-регистрация» — по правилам авиакомпании;
 *  - «Пора на вокзал» — для поездов;
 *  - «Сегодня заселение» — за 3 часа до заселения, «Сегодня выезд из отеля» — за 2 часа до расчётного часа.
 */
class ReminderScheduler(
    private val context: Context,
    private val settings: Settings,
    private val ref: ReferenceData,
) {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    /** [connection] — пересадка: уже в аэропорту, «пора выезжать» не нужно. */
    fun scheduleFlight(flight: Flight, now: Long = System.currentTimeMillis(), connection: Boolean = false) {
        cancelFlight(flight.id)
        if (!connection) {
            val goAt = flight.departureMillis - flight.remindMinutes * 60_000L
            set(goAt, now, requestCode(flight.id, KIND_GO), flight.id, KIND_GO)
        }

        if (flight.isTrain) return
        val airline = ref.airline(flight.airlineCode)
        val openHours = airline?.onlineCheckinHours ?: 24
        if (settings.checkinReminder && openHours > 0) {
            val checkinAt = flight.departureMillis - openHours * 3_600_000L
            set(checkinAt, now, requestCode(flight.id, KIND_CHECKIN), flight.id, KIND_CHECKIN)
        }
    }

    fun cancelFlight(flightId: Long) {
        cancel(requestCode(flightId, KIND_GO), flightId, KIND_GO)
        cancel(requestCode(flightId, KIND_CHECKIN), flightId, KIND_CHECKIN)
    }

    /** [leaveAt] — когда на самом деле уезжаем из отеля (раньше расчётного часа), если известно. */
    fun scheduleStay(stay: Stay, now: Long = System.currentTimeMillis(), leaveAt: Long? = null) {
        cancelStay(stay.id)
        set(stay.checkInMillis - 3 * 3_600_000L, now, requestCode(stay.id, KIND_STAY_CHECKIN), stay.id, KIND_STAY_CHECKIN)
        if (leaveAt != null) {
            set(minOf(leaveAt - 30 * 60_000L, stay.checkOutMillis - 2 * 3_600_000L), now, requestCode(stay.id, KIND_STAY_CHECKOUT), stay.id, KIND_STAY_CHECKOUT)
            return
        }
        set(stay.checkOutMillis - 2 * 3_600_000L, now, requestCode(stay.id, KIND_STAY_CHECKOUT), stay.id, KIND_STAY_CHECKOUT)
    }

    fun cancelStay(stayId: Long) {
        cancel(requestCode(stayId, KIND_STAY_CHECKOUT), stayId, KIND_STAY_CHECKOUT)
        cancel(requestCode(stayId, KIND_STAY_CHECKIN), stayId, KIND_STAY_CHECKIN)
    }

    private fun set(at: Long, now: Long, code: Int, id: Long, kind: Int) {
        if (at <= now) return
        val pi = pendingIntent(code, id, kind, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        if (canScheduleExact()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun cancel(code: Int, id: Long, kind: Int) {
        pendingIntent(code, id, kind, PendingIntent.FLAG_NO_CREATE)?.let {
            alarms.cancel(it)
            it.cancel()
        }
    }

    private fun pendingIntent(code: Int, id: Long, kind: Int, flag: Int): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction("com.travelplanner.app.REMINDER_$kind")
            .putExtra(ReminderReceiver.EXTRA_ID, id)
            .putExtra(ReminderReceiver.EXTRA_KIND, kind)
        return PendingIntent.getBroadcast(context, code, intent, flag or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun requestCode(id: Long, kind: Int): Int = (id * 8 + kind).toInt()

    companion object {
        const val KIND_GO = 1
        const val KIND_CHECKIN = 2
        const val KIND_STAY_CHECKOUT = 5
        const val KIND_STAY_CHECKIN = 6
    }
}
