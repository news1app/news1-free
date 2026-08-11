package com.news1.market;

public final class AppConfig {
    private AppConfig() {}
    public static final String PREFS = "news1_prefs";
    public static final String KEY_ENDPOINT = "endpoint";
    public static final String KEY_AUTO_REFRESH = "auto_refresh";
    public static final String KEY_INTERVAL_HOURS = "interval_hours";
    public static final String KEY_LATEST_JSON = "latest_json";
    public static final String KEY_HISTORY_JSON = "history_json";
    public static final String DEFAULT_ENDPOINT = "https://raw.githubusercontent.com/USERNAME/REPOSITORY/main/data/latest.json";
    public static final long DEFAULT_INTERVAL_HOURS = 1L;
    public static final String WORK_NAME = "news1_periodic_refresh";
}
