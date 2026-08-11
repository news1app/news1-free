package com.news1.market;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class News1Repository {
    private News1Repository() {}

    public static String loadLatest(Context context) {
        String saved = Prefs.get(context).getString(AppConfig.KEY_LATEST_JSON, "");
        if (!saved.trim().isEmpty()) return saved;
        try (InputStream in = context.getAssets().open("sample_news1.json")) {
            return readAll(in);
        } catch (Exception e) {
            return "{}";
        }
    }

    public static FetchResult refresh(Context context) {
        String endpoint = Prefs.endpoint(context);
        if (endpoint.trim().isEmpty()) {
            return new FetchResult(false, loadLatest(context), "URL data GitHub belum diatur.");
        }
        if (!endpoint.startsWith("https://")) {
            return new FetchResult(false, loadLatest(context), "Endpoint harus menggunakan HTTPS.");
        }
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(180000);
            conn.setRequestProperty("Accept", "application/json");
            int code = conn.getResponseCode();
            InputStream stream = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
            String body = stream == null ? "" : readAll(stream);
            conn.disconnect();
            if (code < 200 || code >= 300) {
                return new FetchResult(false, loadLatest(context), "HTTP " + code + ": " + body);
            }
            new JSONObject(body); // validate JSON
            saveLatestAndHistory(context, body);
            return new FetchResult(true, body, "OK");
        } catch (Exception e) {
            return new FetchResult(false, loadLatest(context), e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    private static String readAll(InputStream in) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
        }
        return sb.toString().trim();
    }

    private static void saveLatestAndHistory(Context context, String json) {
        SharedPreferences prefs = Prefs.get(context);
        String previous = prefs.getString(AppConfig.KEY_LATEST_JSON, "");
        try {
            JSONObject nextObj = new JSONObject(json);
            String nextTime = nextObj.optString("generatedAtWib", "");
            String prevTime = previous.trim().isEmpty() ? "" : new JSONObject(previous).optString("generatedAtWib", "");
            if (!nextTime.equals(prevTime)) {
                JSONArray history = new JSONArray(prefs.getString(AppConfig.KEY_HISTORY_JSON, "[]"));
                JSONArray updated = new JSONArray();
                updated.put(nextObj);
                for (int i = 0; i < history.length() && updated.length() < 10; i++) {
                    JSONObject item = history.optJSONObject(i);
                    if (item != null && !nextTime.equals(item.optString("generatedAtWib", ""))) updated.put(item);
                }
                prefs.edit()
                        .putString(AppConfig.KEY_LATEST_JSON, json)
                        .putString(AppConfig.KEY_HISTORY_JSON, updated.toString())
                        .apply();
            } else {
                prefs.edit().putString(AppConfig.KEY_LATEST_JSON, json).apply();
            }
        } catch (Exception ignored) {
            prefs.edit().putString(AppConfig.KEY_LATEST_JSON, json).apply();
        }
    }

    public static final class FetchResult {
        public final boolean success;
        public final String json;
        public final String message;
        public FetchResult(boolean success, String json, String message) {
            this.success = success;
            this.json = json;
            this.message = message;
        }
    }
}
