package com.habit.tracker;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Handles the Yes / No / Snooze buttons of a notification WITHOUT opening the app.
 * Yes -> saved in a small queue (with today's date); the app reads the queue the next time it is open.
 */
public class NotifActionReceiver extends BroadcastReceiver {
    static final String PREFS = "habitx_native";
    static final String KEY_Q = "pending";
    static final String KEY_T = "titles";
    static final String ACT_SNOOZE_FIRE = "habitx.SNOOZE_FIRE";
    static final long SNOOZE_MS = 10 * 60 * 1000L;

    @Override
    public void onReceive(Context c, Intent i) {
        try {
            if (ACT_SNOOZE_FIRE.equals(i.getAction())) {
                showSnoozed(c, i);
                return;
            }
            int nid = i.getIntExtra("nid", 0);
            int base = i.getIntExtra("oid", nid);
            String aid = i.getStringExtra("aid");
            String src = i.getStringExtra("src");
            NotificationManagerCompat.from(c).cancel(nid);

            if ("yes".equals(aid) || "done".equals(aid)) {
                JSONObject o = new JSONObject(src);
                JSONObject ex = o.optJSONObject("extra");
                if (ex == null) return;
                JSONObject item = new JSONObject();
                item.put("kind", ex.optString("kind"));
                item.put("id", ex.opt("id"));
                item.put("act", "yes");
                item.put("date", new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()));
                item.put("ts", System.currentTimeMillis());
                enqueue(c, item);
            } else if ("snooze".equals(aid)) {
                scheduleSnooze(c, base, src);
            }
            // "no": notification is just dismissed
        } catch (Exception e) {
            // never crash from a background receiver
        }
    }

    static synchronized void enqueue(Context c, JSONObject item) {
        SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        JSONArray a;
        try {
            a = new JSONArray(sp.getString(KEY_Q, "[]"));
        } catch (Exception e) {
            a = new JSONArray();
        }
        a.put(item);
        sp.edit().putString(KEY_Q, a.toString()).commit();
    }

    static synchronized String drain(Context c) {
        SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String s = sp.getString(KEY_Q, "[]");
        if (!"[]".equals(s)) sp.edit().putString(KEY_Q, "[]").commit();
        return s;
    }

    private void scheduleSnooze(Context c, int base, String src) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        Intent fire = new Intent(c, NotifActionReceiver.class);
        fire.setAction(ACT_SNOOZE_FIRE);
        fire.putExtra("oid", base);
        fire.putExtra("src", src);
        PendingIntent pi = PendingIntent.getBroadcast(c, 1000000000 + base, fire,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        long at = System.currentTimeMillis() + SNOOZE_MS;
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        }
    }

    private PendingIntent actionPi(Context c, int snoozeId, int base, String aid, String src) {
        Intent a = new Intent(c, NotifActionReceiver.class);
        a.setAction("habitx.ACT." + snoozeId + "." + aid);
        a.putExtra("nid", snoozeId);
        a.putExtra("oid", base);
        a.putExtra("aid", aid);
        a.putExtra("src", src);
        return PendingIntent.getBroadcast(c, snoozeId + aid.hashCode(), a,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void showSnoozed(Context c, Intent i) throws Exception {
        int base = i.getIntExtra("oid", 0);
        String src = i.getStringExtra("src");
        JSONObject o = new JSONObject(src);
        int snoozeId = 1000000000 + base;

        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel("default", "Default", NotificationManager.IMPORTANCE_HIGH));
        }

        JSONObject titles;
        try {
            titles = new JSONObject(c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_T, "{}"));
        } catch (Exception e) {
            titles = new JSONObject();
        }

        int small = c.getResources().getIdentifier("ic_stat_icon", "drawable", c.getPackageName());
        int large = c.getResources().getIdentifier("ic_large_icon", "drawable", c.getPackageName());
        Intent open = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
        PendingIntent openPi = PendingIntent.getActivity(c, snoozeId, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder b = new NotificationCompat.Builder(c, "default")
                .setSmallIcon(small != 0 ? small : android.R.drawable.ic_popup_reminder)
                .setColor(0xFF2196F3)
                .setContentTitle(o.optString("title"))
                .setContentText(o.optString("body"))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(openPi);
        if (large != 0) b.setLargeIcon(BitmapFactory.decodeResource(c.getResources(), large));
        b.addAction(0, titles.optString("yes", "Yes"), actionPi(c, snoozeId, base, "yes", src));
        b.addAction(0, titles.optString("no", "No"), actionPi(c, snoozeId, base, "no", src));
        if ("TODO".equals(o.optString("actionTypeId"))) {
            b.addAction(0, titles.optString("snooze", "Snooze"), actionPi(c, snoozeId, base, "snooze", src));
        }
        nm.notify(snoozeId, b.build());
    }
}
