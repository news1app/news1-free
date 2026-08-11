package com.news1.market;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public final class WorkScheduler {
    private WorkScheduler() {}

    public static void apply(Context context) {
        WorkManager wm = WorkManager.getInstance(context);
        if (!Prefs.autoRefresh(context) || Prefs.endpoint(context).trim().isEmpty()) {
            wm.cancelUniqueWork(AppConfig.WORK_NAME);
            return;
        }
        long hours = Math.max(1L, Prefs.intervalHours(context));
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(News1Worker.class, hours, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build();
        wm.enqueueUniquePeriodicWork(
                AppConfig.WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
        );
    }
}
