package ms.sreeram.attendance;

import android.app.*;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import org.json.JSONObject;
import java.util.Calendar;

public class Alarm extends BroadcastReceiver {
    static final String DAILY = "ms.sreeram.attendance.DAILY", WEEKLY = "ms.sreeram.attendance.WEEKLY", MIDNIGHT = "ms.sreeram.attendance.MIDNIGHT";
    static final String CH = "reminders";

    @Override public void onReceive(Context c, Intent i) {
        String a = i.getAction() == null ? "" : i.getAction();
        JSONObject set = Store.set(c), days = Store.days(c);
        Calendar now = Calendar.getInstance();
        if (DAILY.equals(a)) {
            if (set.optBoolean("dailyOn", true) && !Store.isOff(set, now)) {
                JSONObject d = days.optJSONObject(Store.key(now));
                if (!Store.has(d, "leave")) {
                    String text;
                    if (d == null) text = "Late, leave or overtime today? Tap to log it before you forget.";
                    else text = "Logged today: " + Store.summary(Store.sum(days, now, 1), "") + ". Anything else?";
                    notify(c, "Log today's attendance", text, 1);
                }
            }
        } else if (WEEKLY.equals(a)) {
            if (set.optBoolean("weeklyOn", true)) {
                Calendar from = (Calendar) now.clone(); from.add(Calendar.DAY_OF_MONTH, -6);
                String week = Store.summary(Store.sum(days, from, 7), "Nothing logged");
                Calendar m1 = (Calendar) now.clone(); m1.set(Calendar.DAY_OF_MONTH, 1);
                String month = Store.summary(Store.sum(days, m1, now.get(Calendar.DAY_OF_MONTH)), "nothing yet");
                notify(c, "Your week: " + week, "Last 7 days: " + week + ".\nMonth so far: " + month + ".", 2);
            }
        } else if (MIDNIGHT.equals(a)) {
            CalWidget.refreshAll(c);
        } else {
            CalWidget.refreshAll(c);
        }
        schedule(c);
    }

    static PendingIntent pi(Context c, String action, int rq) {
        return PendingIntent.getBroadcast(c, rq, new Intent(c, Alarm.class).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static int[] hm(String t, int dh, int dm) {
        try { String[] p = t.split(":"); return new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1])}; } catch (Exception e) { return new int[]{dh, dm}; }
    }

    static void schedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        JSONObject set = Store.set(c);
        Calendar now = Calendar.getInstance();

        PendingIntent pd = pi(c, DAILY, 1);
        am.cancel(pd);
        if (set.optBoolean("dailyOn", true)) {
            int[] t = hm(set.optString("dailyTime", "18:45"), 18, 45);
            Calendar k = (Calendar) now.clone();
            k.set(Calendar.HOUR_OF_DAY, t[0]); k.set(Calendar.MINUTE, t[1]); k.set(Calendar.SECOND, 0); k.set(Calendar.MILLISECOND, 0);
            if (!k.after(now)) k.add(Calendar.DAY_OF_MONTH, 1);
            set(am, k.getTimeInMillis(), pd);
        }

        PendingIntent pw = pi(c, WEEKLY, 2);
        am.cancel(pw);
        if (set.optBoolean("weeklyOn", true)) {
            int[] t = hm(set.optString("weeklyTime", "19:00"), 19, 0);
            int dow = set.optInt("weeklyDay", 6) + 1; // Calendar.SUNDAY = 1
            Calendar k = (Calendar) now.clone();
            k.set(Calendar.HOUR_OF_DAY, t[0]); k.set(Calendar.MINUTE, t[1]); k.set(Calendar.SECOND, 0); k.set(Calendar.MILLISECOND, 0);
            int diff = (dow - k.get(Calendar.DAY_OF_WEEK) + 7) % 7;
            k.add(Calendar.DAY_OF_MONTH, diff);
            if (!k.after(now)) k.add(Calendar.DAY_OF_MONTH, 7);
            set(am, k.getTimeInMillis(), pw);
        }

        Calendar mid = (Calendar) now.clone();
        mid.add(Calendar.DAY_OF_MONTH, 1);
        mid.set(Calendar.HOUR_OF_DAY, 0); mid.set(Calendar.MINUTE, 0); mid.set(Calendar.SECOND, 5); mid.set(Calendar.MILLISECOND, 0);
        am.set(AlarmManager.RTC, mid.getTimeInMillis(), pi(c, MIDNIGHT, 3));
    }

    static void set(AlarmManager am, long t, PendingIntent p) {
        try {
            if (Build.VERSION.SDK_INT >= 31 && am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, p);
            else if (Build.VERSION.SDK_INT < 31) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, p);
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, p);
        } catch (SecurityException e) { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, p); }
    }

    static PendingIntent open(Context c, String what, int rq) {
        Intent i = new Intent(c, Main.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (what != null) i.putExtra("open", what);
        return PendingIntent.getActivity(c, rq, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void notify(Context c, String title, String text, int id) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CH) == null) {
            NotificationChannel ch = new NotificationChannel(CH, "Reminders and summaries", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("Daily attendance reminder and weekly summary");
            nm.createNotificationChannel(ch);
        }
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CH) : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_n).setColor(0xFF0D7A84).setContentTitle(title).setContentText(text)
            .setStyle(new Notification.BigTextStyle().bigText(text)).setAutoCancel(true)
            .setContentIntent(open(c, null, 100 + id));
        if (id == 1) {
            b.addAction(new Notification.Action.Builder(null, "Late", open(c, "late", 201)).build());
            b.addAction(new Notification.Action.Builder(null, "Overtime", open(c, "ot", 202)).build());
            b.addAction(new Notification.Action.Builder(null, "Leave", open(c, "leave", 203)).build());
        }
        try { nm.notify(id, b.build()); } catch (SecurityException ignored) {}
    }
}
