package com.news1.market;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {
    private Prefs() {}

    public static SharedPreferences get(Context context) {
        return context.getSharedPreferences(AppConfig.PREFS, Context.MODE_PRIVATE);
    }

    public static String endpoint(Context context) {
        return get(context).getString(AppConfig.KEY_ENDPOINT, AppConfig.DEFAULT_ENDPOINT).trim();
    }

    public static boolean autoRefresh(Context context) {
        return get(context).getBoolean(AppConfig.KEY_AUTO_REFRESH, true);
    }

    public static long intervalMinutes(Context context) {
        return get(context).getLong(AppConfig.KEY_INTERVAL_MINUTES, AppConfig.DEFAULT_INTERVAL_MINUTES);
    }

    public static boolean compact(Context context) {
        return get(context).getBoolean(AppConfig.KEY_COMPACT, true);
    }

    public static boolean translate(Context context) {
        return get(context).getBoolean(AppConfig.KEY_TRANSLATE, true);
    }

    public static boolean showSource(Context context) {
        return get(context).getBoolean(AppConfig.KEY_SHOW_SOURCE, true);
    }

    public static boolean analysisOnly(Context context) {
        return get(context).getBoolean(AppConfig.KEY_ANALYSIS_ONLY, false);
    }
}
