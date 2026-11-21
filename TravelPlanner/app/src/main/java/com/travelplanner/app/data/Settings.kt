package com.travelplanner.app.data

import android.content.Context

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** За сколько часов до вылета напоминать «пора в аэропорт». */
    var remindHours: Int
        get() = prefs.getInt("remind_hours", 3)
        set(value) = prefs.edit().putInt("remind_hours", value).apply()

    /** Добавлять ли перелёты и проживание в системный календарь. */
    var calendarSync: Boolean
        get() = prefs.getBoolean("calendar_sync", true)
        set(value) = prefs.edit().putBoolean("calendar_sync", value).apply()

    /** Напоминать об открытии онлайн-регистрации. */
    var checkinReminder: Boolean
        get() = prefs.getBoolean("checkin_reminder", true)
        set(value) = prefs.edit().putBoolean("checkin_reminder", value).apply()
}
