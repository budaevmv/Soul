package com.travelplanner.app.data

import android.content.Context

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** За сколько часов до вылета напоминать «пора в аэропорт». */
    var remindHours: Int
        get() = prefs.getInt("remind_hours", 3)
        set(value) = prefs.edit().putInt("remind_hours", value).apply()

    /** За сколько минут до отправления поезда напоминать «пора на вокзал». */
    var trainRemindMinutes: Int
        get() = prefs.getInt("train_remind_minutes", 60)
        set(value) = prefs.edit().putInt("train_remind_minutes", value).apply()

    /** Добавлять ли перелёты и проживание в системный календарь. */
    var calendarSync: Boolean
        get() = prefs.getBoolean("calendar_sync", true)
        set(value) = prefs.edit().putBoolean("calendar_sync", value).apply()

    /** Напоминать об открытии онлайн-регистрации. */
    /** Версия логики событий календаря: при обновлении приложения события пересоздаются один раз. */
    var calendarLogicVersion: Int
        get() = prefs.getInt("calendar_logic_version", 1)
        set(value) = prefs.edit().putInt("calendar_logic_version", value).apply()

    /** Рейсы/поезда, у которых время «пора выезжать» задано вручную (не пересчитываем автоматически). */
    fun isManualRemind(id: Long): Boolean = prefs.getStringSet("manual_remind", emptySet())!!.contains(id.toString())
    fun setManualRemind(id: Long) {
        val set = prefs.getStringSet("manual_remind", emptySet())!!.toMutableSet().apply { add(id.toString()) }
        prefs.edit().putStringSet("manual_remind", set).apply()
    }

    var checkinReminder: Boolean
        get() = prefs.getBoolean("checkin_reminder", true)
        set(value) = prefs.edit().putBoolean("checkin_reminder", value).apply()
}
