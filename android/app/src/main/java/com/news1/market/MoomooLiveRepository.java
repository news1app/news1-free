package com.news1.market;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class MoomooLiveRepository {
    private MoomooLiveRepository() {}

    public static final class Result {
        public final boolean success;
        public final String message;
        public final JSONObject json;
        Result(boolean success, String message, JSONObject json) {
            this.success = success; this.message = message; this.json = json;
        }
    }

    public static Result fetch(Context context) {
        String base = Prefs.moomooUrl(context).trim();
        if (base.isEmpty()) return new Result(false, "URL bridge Moomoo belum diisi.", null);
        try {
            String target = base;
            if (!target.endsWith("/api/prices")) {
                while (target.endsWith("/")) target = target.substring(0, target.length()-1);
                target += "/api/prices";
            }
            HttpURLConnection con = (HttpURLConnection) new URL(target).openConnection();
            con.setRequestMethod("GET");
            con.setConnectTimeout(3500);
            con.setReadTimeout(5000);
            con.setUseCaches(false);
            String token = Prefs.moomooToken(context).trim();
            if (!token.isEmpty()) con.setRequestProperty("Authorization", "Bearer " + token);
            int code = con.getResponseCode();
            InputStream stream = code >= 200 && code < 300 ? con.getInputStream() : con.getErrorStream();
            String body = readAll(stream);
            con.disconnect();
            JSONObject obj = new JSONObject(body.isEmpty() ? "{}" : body);
            if (code >= 200 && code < 300 && obj.optBoolean("ok", false)) {
                return new Result(true, "OK", obj);
            }
            return new Result(false, obj.optString("error", "HTTP " + code), obj);
        } catch (Exception e) {
            return new Result(false, e.getMessage() == null ? e.toString() : e.getMessage(), null);
        }
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }
}
