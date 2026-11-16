package ru.careshare.app;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Напоминания: планирование будильников, показ уведомлений, кнопка «Принято». */
public class Rx extends BroadcastReceiver {
    static final String ALARM = "ru.careshare.ALARM";
    static final String TAKEN = "ru.careshare.TAKEN";
    static final String CH = "care";
    static final int IMMUTABLE = 67108864; // PendingIntent.FLAG_IMMUTABLE (API 23)

    static SharedPreferences sp(Context c) { return c.getSharedPreferences("careshare", 0); }
    static String day(long t) { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(t)); }

    @Override
    public void onReceive(Context c, Intent i) {
        String a = i.getAction();
        if (ALARM.equals(a)) fire(c, i.getIntExtra("i", -1), i.getStringExtra("key"));
        else if (TAKEN.equals(a)) taken(c, i);
        else scheduleAll(c); // загрузка, обновление, смена времени
    }

    static void save(Context c, String json, boolean on) {
        SharedPreferences.Editor e = sp(c).edit().putString("specs", json).putBoolean("on", on);
        String lim = day(System.currentTimeMillis() - 3 * 86400000L);
        for (String k : sp(c).getAll().keySet()) if (k.startsWith("d_") && k.substring(2, 12).compareTo(lim) < 0) e.remove(k);
        e.commit();
        scheduleAll(c);
    }

    static Intent alarmIntent(Context c, int i, String key) {
        return new Intent(c, Rx.class).setAction(ALARM).setData(Uri.parse("careshare://alarm/" + i)).putExtra("i", i).putExtra("key", key);
    }

    static long next(JSONObject s, long now) {
        String k = s.optString("kind");
        if ("once".equals(k)) { long at = s.optLong("at"); return at > now ? at : -1; }
        if ("daily".equals(k)) {
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, s.optInt("h")); cal.set(Calendar.MINUTE, s.optInt("m"));
            cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
            if (cal.getTimeInMillis() <= now + 1000) cal.add(Calendar.DAY_OF_MONTH, 1);
            return cal.getTimeInMillis();
        }
        return -1;
    }

    static boolean exactAllowed(Context c) {
        if (Build.VERSION.SDK_INT < 31) return true;
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            return (Boolean) AlarmManager.class.getMethod("canScheduleExactAlarms").invoke(am);
        } catch (Throwable t) { return false; }
    }

    static void scheduleAll(Context c) {
        SharedPreferences p = sp(c);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        int prev = p.getInt("count", 0);
        for (int i = 0; i < prev; i++) {
            PendingIntent pi = PendingIntent.getBroadcast(c, i, alarmIntent(c, i, null), PendingIntent.FLAG_NO_CREATE | IMMUTABLE);
            if (pi != null) { am.cancel(pi); pi.cancel(); }
        }
        int n = 0;
        if (p.getBoolean("on", true)) {
            try {
                JSONArray arr = new JSONArray(p.getString("specs", "[]"));
                long now = System.currentTimeMillis();
                boolean exact = exactAllowed(c);
                n = Math.min(arr.length(), 400);
                for (int i = 0; i < n; i++) {
                    JSONObject s = arr.getJSONObject(i);
                    long at = next(s, now);
                    if (at <= 0) continue;
                    PendingIntent pi = PendingIntent.getBroadcast(c, i, alarmIntent(c, i, s.optString("key")), PendingIntent.FLAG_UPDATE_CURRENT | IMMUTABLE);
                    try {
                        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                    } catch (SecurityException se) {
                        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                    }
                }
            } catch (Exception e) { n = Math.max(n, 0); }
        }
        p.edit().putInt("count", n).commit();
    }

    static void fire(Context c, int idx, String key) {
        try {
            JSONArray arr = new JSONArray(sp(c).getString("specs", "[]"));
            JSONObject s = null;
            if (idx >= 0 && idx < arr.length() && arr.getJSONObject(idx).optString("key").equals(key)) s = arr.getJSONObject(idx);
            if (s == null) for (int i = 0; i < arr.length(); i++) if (arr.getJSONObject(i).optString("key").equals(key)) s = arr.getJSONObject(i);
            if (s != null && sp(c).getBoolean("on", true)) {
                long now = System.currentTimeMillis();
                JSONObject chk = s.optJSONObject("check"), dose = s.optJSONObject("dose");
                boolean skip = false;
                if (chk != null) {
                    String d = day(now - s.optInt("off") * 86400000L);
                    skip = sp(c).getBoolean("d_" + d + "_" + chk.optString("medId") + "_" + chk.optString("time"), false);
                }
                if (dose != null) skip = sp(c).getBoolean("d_" + day(now) + "_" + dose.optString("medId") + "_" + dose.optString("time"), false);
                if (!skip) show(c, s.optString("key"), s.optString("title"), s.optString("text"), dose);
            }
        } catch (Exception ignored) { }
        scheduleAll(c);
    }

    static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            Class<?> chc = Class.forName("android.app.NotificationChannel");
            Object ch = chc.getConstructor(String.class, CharSequence.class, int.class).newInstance(CH, "Напоминания о заботе", 4);
            chc.getMethod("setDescription", String.class).invoke(ch, "Лекарства, визиты к врачу, рецепты и задачи");
            chc.getMethod("enableVibration", boolean.class).invoke(ch, true);
            NotificationManager.class.getMethod("createNotificationChannel", chc).invoke(nm, ch);
        } catch (Throwable ignored) { }
    }

    @SuppressWarnings("deprecation")
    static void show(Context c, String key, String title, String text, JSONObject dose) {
        ensureChannel(c);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            try { b = (Notification.Builder) Notification.Builder.class.getConstructor(Context.class, String.class).newInstance(c, CH); }
            catch (Throwable t) { b = new Notification.Builder(c); }
        } else {
            b = new Notification.Builder(c);
            b.setPriority(Notification.PRIORITY_HIGH).setDefaults(Notification.DEFAULT_ALL);
        }
        int id = key.hashCode();
        Intent open = new Intent(c, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        b.setSmallIcon(R.drawable.ic_stat).setColor(Color.parseColor("#C0583A"))
                .setContentTitle(title).setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text))
                .setAutoCancel(true).setCategory(Notification.CATEGORY_REMINDER).setShowWhen(true)
                .setContentIntent(PendingIntent.getActivity(c, id, open, PendingIntent.FLAG_UPDATE_CURRENT | IMMUTABLE));
        if (dose != null) {
            Intent t = new Intent(c, Rx.class).setAction(TAKEN).setData(Uri.parse("careshare://taken/" + id))
                    .putExtra("medId", dose.optString("medId")).putExtra("time", dose.optString("time"))
                    .putExtra("date", day(System.currentTimeMillis())).putExtra("nid", id);
            b.addAction(0, "✓ Принято", PendingIntent.getBroadcast(c, id, t, PendingIntent.FLAG_UPDATE_CURRENT | IMMUTABLE));
        }
        try { nm.notify(id, b.build()); } catch (SecurityException ignored) { }
    }

    static void taken(Context c, Intent i) {
        String date = i.getStringExtra("date"), med = i.getStringExtra("medId"), time = i.getStringExtra("time");
        SharedPreferences p = sp(c);
        try {
            JSONArray arr = new JSONArray(p.getString("pending", "[]"));
            JSONObject o = new JSONObject();
            o.put("date", date); o.put("medId", med); o.put("time", time); o.put("at", System.currentTimeMillis());
            arr.put(o);
            p.edit().putString("pending", arr.toString()).putBoolean("d_" + date + "_" + med + "_" + time, true).commit();
        } catch (Exception ignored) { }
        ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(i.getIntExtra("nid", 0));
    }

    static String takePending(Context c) {
        String s = sp(c).getString("pending", "[]");
        sp(c).edit().remove("pending").commit();
        return s;
    }

    static void markDose(Context c, String date, String med, String time, boolean on) {
        String k = "d_" + date + "_" + med + "_" + time;
        if (on) sp(c).edit().putBoolean(k, true).apply(); else sp(c).edit().remove(k).apply();
    }

}
