package com.news1.market;

import android.content.Context;

import org.json.JSONObject;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class News1Worker extends Worker {
    public News1Worker(@NonNull Context appContext, @NonNull WorkerParameters workerParams) {
        super(appContext, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        if (!Prefs.autoRefresh(getApplicationContext())) return Result.success();
        String before = News1Repository.loadLatest(getApplicationContext());
        News1Repository.FetchResult result = News1Repository.refresh(getApplicationContext());
        if (result.success) {
            try {
                String oldTime = new JSONObject(before).optString("generatedAtWib", "");
                String newTime = new JSONObject(result.json).optString("generatedAtWib", "");
                if (!newTime.trim().isEmpty() && !newTime.equals(oldTime)) {
                    NotificationHelper.ensureChannel(getApplicationContext());
                    NotificationHelper.showUpdate(getApplicationContext(), result.json);
                }
            } catch (Exception ignored) {}
            return Result.success();
        }
        return Result.retry();
    }
}
