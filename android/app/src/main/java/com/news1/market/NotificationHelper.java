package com.news1.market;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

public final class NotificationHelper {
    private static final String CHANNEL_ID = "news1_updates";
    private NotificationHelper() {}

    public static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "NEWS1 Market Updates",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Perubahan bias dan laporan NEWS1 terbaru");
            context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    public static void showUpdate(Context context, String json) {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return;
        }
        try {
            JSONObject root = new JSONObject(json);
            JSONObject s = root.optJSONObject("summary");
            String xau = s == null ? "-" : s.optString("xauusd", "-");
            String dxy = s == null ? "-" : s.optString("dxy", "-");
            String oil = s == null ? "-" : s.optString("oil", "-");
            String message = "XAU " + xau + " • DXY " + dxy + " • Oil " + oil;

            Intent intent = new Intent(context, MainActivity.class);
            PendingIntent pi = PendingIntent.getActivity(
                    context, 1, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_notify_more)
                    .setContentTitle("NEWS1 diperbarui")
                    .setContentText(message)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                    .setAutoCancel(true)
                    .setContentIntent(pi)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT);

            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.notify(1001, builder.build());
        } catch (Exception ignored) {}
    }
}
