package com.travelplanner.app.ui

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

object Format {
    private val ru: Locale = Locale.forLanguageTag("ru")
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", ru)
    private val dayFmt = DateTimeFormatter.ofPattern("EEE, d MMMM", ru)
    private val shortDateFmt = DateTimeFormatter.ofPattern("d MMM", ru)
    private val dateFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy", ru)
    private val dateTimeFmt = DateTimeFormatter.ofPattern("d MMM, HH:mm", ru)

    fun zone(id: String): ZoneId = runCatching { ZoneId.of(id) }.getOrDefault(ZoneId.systemDefault())

    fun local(millis: Long, zone: String): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone(zone))

    fun time(millis: Long, zone: String): String = local(millis, zone).format(timeFmt)
    fun day(millis: Long, zone: String): String = local(millis, zone).format(dayFmt).replaceFirstChar { it.uppercase() }
    fun shortDate(millis: Long, zone: String): String = local(millis, zone).format(shortDateFmt)
    fun dateTime(millis: Long, zone: String): String = local(millis, zone).format(dateTimeFmt)

    fun date(d: LocalDate): String = d.format(dateFmt)
    fun time(t: LocalTime): String = t.format(timeFmt)

    fun toMillis(date: LocalDate, time: LocalTime, zone: String): Long =
        LocalDateTime.of(date, time).atZone(zone(zone)).toInstant().toEpochMilli()

    /** «UTC+3» для отображения часового пояса. */
    fun utcOffset(zone: String, millis: Long = System.currentTimeMillis()): String {
        val offset = zone(zone).rules.getOffset(Instant.ofEpochMilli(millis)).totalSeconds
        val h = offset / 3600
        val m = abs(offset % 3600) / 60
        val sign = if (offset >= 0) "+" else "−"
        return "UTC$sign${abs(h)}" + if (m != 0) ":%02d".format(m) else ""
    }

    fun duration(ms: Long): String {
        val totalMin = ms / 60_000
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h == 0L -> "$m мин"
            m == 0L -> "$h ч"
            else -> "$h ч $m мин"
        }
    }

    /** «через 2 дн 3 ч», «через 45 мин», «сейчас». */
    fun countdown(target: Long, now: Long = System.currentTimeMillis()): String {
        val diff = target - now
        if (diff <= 0) return "сейчас"
        val min = diff / 60_000
        val days = min / (60 * 24)
        val hours = (min % (60 * 24)) / 60
        val mins = min % 60
        return "через " + when {
            days > 0 -> "$days дн" + if (hours > 0) " $hours ч" else ""
            hours > 0 -> "$hours ч" + if (mins > 0) " $mins мин" else ""
            else -> "$mins мин"
        }
    }
}
