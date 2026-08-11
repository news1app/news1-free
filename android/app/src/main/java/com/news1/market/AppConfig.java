package com.news1.market;

public final class AppConfig {
    private AppConfig() {}
    public static final String PREFS = "news1_prefs";
    public static final String KEY_ENDPOINT = "endpoint";
    public static final String KEY_AUTO_REFRESH = "auto_refresh";
    public static final String KEY_INTERVAL_MINUTES = "interval_minutes";
    public static final String KEY_LATEST_JSON = "latest_json";
    public static final String KEY_HISTORY_JSON = "history_json";
    public static final String KEY_COMPACT = "compact_mode";
    public static final String KEY_TRANSLATE = "translate_id";
    public static final String KEY_SHOW_SOURCE = "show_source";
    public static final String KEY_ANALYSIS_ONLY = "analysis_only";
    public static final String KEY_MOOMOO_ENABLED = "moomoo_enabled";
    public static final String KEY_MOOMOO_URL = "moomoo_url";
    public static final String KEY_MOOMOO_TOKEN = "moomoo_token";
    public static final String KEY_PRICE_REFRESH_SECONDS = "price_refresh_seconds";
    public static final String DEFAULT_ENDPOINT = "https://raw.githubusercontent.com/USERNAME/REPOSITORY/main/data/latest.json";
    public static final long DEFAULT_INTERVAL_MINUTES = 30L;
    public static final int DEFAULT_PRICE_REFRESH_SECONDS = 10;
    public static final String WORK_NAME = "news1_periodic_refresh";
}
