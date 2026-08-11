package com.news1.market;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private LinearLayout root;
    private ProgressBar progress;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestNotificationPermission();
        NotificationHelper.ensureChannel(this);
        WorkScheduler.apply(this);
        buildShell();
        render(News1Repository.loadLatest(this));
    }

    @Override
    protected void onResume() {
        super.onResume();
        WorkScheduler.apply(this);
    }

    private void buildShell() {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(Color.rgb(11, 15, 20));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 10));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        top.addView(titles, titleLp);
        titles.addView(Ui.text(this, "NEWS1", 22, Color.WHITE, true));
        titles.addView(Ui.text(this, "Market Intelligence • WIB", 12, Color.rgb(169,180,194), false));

        MaterialButton refresh = smallButton("Refresh");
        refresh.setOnClickListener(v -> refreshNow());
        top.addView(refresh);

        MaterialButton settings = smallButton("⚙");
        settings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        top.addView(settings);

        outer.addView(top);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        outer.addView(progress, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 2)));

        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 24));
        scroll.addView(root);
        outer.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(outer);
    }

    private MaterialButton smallButton(String text) {
        MaterialButton b = new MaterialButton(this);
        b.setText(text);
        b.setTextSize(12);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setInsetTop(0);
        b.setInsetBottom(0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(this, 44));
        lp.setMargins(Ui.dp(this, 6), 0, 0, 0);
        b.setLayoutParams(lp);
        return b;
    }

    private void refreshNow() {
        if (Prefs.endpoint(this).trim().isEmpty()) {
            Toast.makeText(this, "Atur URL data GitHub di menu ⚙ terlebih dahulu.", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, SettingsActivity.class));
            return;
        }
        progress.setVisibility(View.VISIBLE);
        executor.submit(() -> {
            News1Repository.FetchResult result = News1Repository.refresh(this);
            runOnUiThread(() -> {
                progress.setVisibility(View.GONE);
                render(result.json);
                Toast.makeText(this, result.success ? "NEWS1 diperbarui." : "Gagal: " + result.message, Toast.LENGTH_LONG).show();
            });
        });
    }

    private void render(String json) {
        root.removeAllViews();
        try {
            JSONObject data = new JSONObject(json);
            boolean demo = data.optBoolean("demo", false);
            String generated = data.optString("generatedAtWib", "Belum ada data live");
            TextView status = Ui.text(this, (demo ? "DEMO • " : "") + "Update: " + generated, 12, demo ? Color.rgb(247,201,72) : Color.rgb(169,180,194), true);
            status.setPadding(0, 0, 0, Ui.dp(this, 12));
            root.addView(status);

            JSONObject summary = data.optJSONObject("summary");
            if (summary != null) addBiasStrip(summary);

            String next = summary == null ? "-" : summary.optString("nextCatalyst", "-");
            addSectionCard("Katalis berikutnya", next);

            JSONArray warnings = data.optJSONArray("warnings");
            if (warnings != null && warnings.length() > 0) {
                StringBuilder w = new StringBuilder();
                for (int i = 0; i < warnings.length(); i++) w.append("• ").append(warnings.optString(i)).append("\n");
                addSectionCard("Catatan verifikasi", w.toString().trim());
            }

            JSONArray snapshot = data.optJSONArray("marketSnapshot");
            addHeader("Market Snapshot", "Harga/reaksi terakhir yang berhasil diverifikasi");
            if (snapshot != null && snapshot.length() > 0) {
                for (int i = 0; i < snapshot.length(); i++) {
                    JSONObject m = snapshot.optJSONObject(i);
                    if (m != null) addMarketCard(m);
                }
            } else {
                addSectionCard("Snapshot belum tersedia", "Feed GitHub belum mengembalikan snapshot pasar yang dapat dibaca.");
            }

            JSONArray news = data.optJSONArray("news");
            addHeader("Berita & Fundamental", "24 jam terakhir");
            if (news == null || news.length() == 0) {
                addSectionCard("Belum ada laporan live", "Hubungkan aplikasi ke data/latest.json di repository GitHub melalui menu pengaturan.");
            } else {
                for (int i = 0; i < news.length(); i++) {
                    JSONObject n = news.optJSONObject(i);
                    if (n != null) addNewsCard(n);
                }
            }

            JSONArray cal = data.optJSONArray("calendar");
            addHeader("Kalender Ekonomi", "7 hari ke depan • WIB");
            if (cal != null) {
                for (int i = 0; i < cal.length(); i++) {
                    JSONObject e = cal.optJSONObject(i);
                    if (e != null) addCalendarCard(e);
                }
            }

            if (summary != null) {
                addHeader("Skenario", "Ringkasan NEWS1");
                addSectionCard("Bullish", summary.optString("bullScenario", "-"));
                addSectionCard("Bearish", summary.optString("bearScenario", "-"));
                addSectionCard("Keyakinan", summary.optInt("confidencePct", 0) + "% • " + summary.optString("confidenceLabel", "-"));
            }

            MaterialButton history = new MaterialButton(this);
            history.setText("Buka Riwayat Laporan");
            history.setOnClickListener(v -> startActivity(new Intent(this, HistoryActivity.class)));
            root.addView(history);
        } catch (Exception e) {
            addSectionCard("Data tidak dapat dibaca", e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    private void addBiasStrip(JSONObject s) {
        addHeader("Bias Utama", "Snapshot fundamental");
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(biasChip("XAUUSD", s.optString("xauusd", "-")));
        row.addView(biasChip("DXY", s.optString("dxy", "-")));
        row.addView(biasChip("YIELD", s.optString("yield", "-")));
        row.addView(biasChip("OIL", s.optString("oil", "-")));
        row.addView(biasChip("DOMINAN", s.optString("dominantAsset", "-")));
        hsv.addView(row);
        hsv.setPadding(0, 0, 0, Ui.dp(this, 12));
        root.addView(hsv);
    }

    private MaterialCardView biasChip(String label, String value) {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(Color.rgb(24,33,44));
        card.setRadius(Ui.dp(this, 14));
        card.setStrokeWidth(Ui.dp(this, 1));
        card.setStrokeColor(Color.rgb(50,62,78));
        LinearLayout box = Ui.vertical(this, 12);
        box.addView(Ui.text(this, label, 10, Color.rgb(169,180,194), true));
        box.addView(Ui.text(this, value, 14, biasColor(value), true));
        card.addView(box);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 132), LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, Ui.dp(this, 10), 0);
        card.setLayoutParams(lp);
        return card;
    }

    private int biasColor(String value) {
        String v = value.toUpperCase();
        if (v.contains("BULL")) return Color.rgb(87,214,141);
        if (v.contains("BEAR")) return Color.rgb(255,107,107);
        return Color.rgb(247,201,72);
    }

    private void addHeader(String title, String subtitle) {
        TextView t = Ui.text(this, title, 18, Color.WHITE, true);
        t.setPadding(0, Ui.dp(this, 10), 0, 0);
        root.addView(t);
        TextView s = Ui.text(this, subtitle, 11, Color.rgb(169,180,194), false);
        s.setPadding(0, 0, 0, Ui.dp(this, 10));
        root.addView(s);
    }

    private void addSectionCard(String title, String body) {
        MaterialCardView card = Ui.card(this);
        LinearLayout box = Ui.vertical(this, 14);
        box.addView(Ui.text(this, title, 14, Color.WHITE, true));
        TextView b = Ui.text(this, body == null || body.trim().isEmpty() ? "-" : body, 13, Color.rgb(205,213,222), false);
        b.setPadding(0, Ui.dp(this, 6), 0, 0);
        box.addView(b);
        card.addView(box);
        root.addView(card);
    }

    private void addNewsCard(JSONObject n) {
        MaterialCardView card = Ui.card(this);
        LinearLayout box = Ui.vertical(this, 14);
        String impact = n.optString("impact", "LOW");
        box.addView(Ui.text(this, n.optString("publishedAtWib", "-") + " • " + n.optString("source", "-") + " • " + impact, 10, impactColor(impact), true));
        TextView title = Ui.text(this, n.optString("title", "Tanpa judul"), 15, Color.WHITE, true);
        title.setPadding(0, Ui.dp(this, 5), 0, Ui.dp(this, 8));
        box.addView(title);
        appendField(box, "FAKTA", n.optString("fact", "-"));
        appendField(box, "PEJABAT", n.optString("officialStatement", "-"));
        appendField(box, "KONSENSUS", n.optString("consensus", "-"));
        appendField(box, "INTERPRETASI", n.optString("interpretation", "-"));
        appendField(box, "REAKSI HARGA", n.optString("priceResponse", "-"));
        JSONArray impacts = n.optJSONArray("assetImpacts");
        if (impacts != null && impacts.length() > 0) {
            StringBuilder imp = new StringBuilder();
            for (int i = 0; i < impacts.length(); i++) {
                JSONObject a = impacts.optJSONObject(i);
                if (a == null) continue;
                if (imp.length() > 0) imp.append("\n");
                imp.append("• ").append(a.optString("asset", "-")).append(" ")
                        .append(a.optString("direction", "")).append(": ")
                        .append(a.optString("explanation", "-"));
            }
            appendField(box, "DAMPAK ASET", imp.toString());
        }
        String contradiction = n.optString("contradictions", "");
        if (!contradiction.trim().isEmpty() && !contradiction.equals("-")) appendField(box, "⚠ KONTRADIKSI", contradiction);
        String url = n.optString("url", "");
        if (url.startsWith("https://")) {
            TextView link = Ui.text(this, "Buka sumber ↗", 12, Color.rgb(108,168,255), true);
            link.setPadding(0, Ui.dp(this, 10), 0, 0);
            link.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))));
            box.addView(link);
        }
        card.addView(box);
        root.addView(card);
    }

    private int impactColor(String impact) {
        String i = impact.toUpperCase();
        if (i.contains("HIGH") || i.contains("TINGGI")) return Color.rgb(255,107,107);
        if (i.contains("MEDIUM") || i.contains("SEDANG")) return Color.rgb(247,201,72);
        return Color.rgb(108,168,255);
    }

    private void appendField(LinearLayout box, String label, String value) {
        TextView l = Ui.text(this, label, 10, Color.rgb(215,181,90), true);
        l.setPadding(0, Ui.dp(this, 5), 0, 0);
        box.addView(l);
        box.addView(Ui.text(this, value == null || value.trim().isEmpty() ? "-" : value, 12, Color.rgb(205,213,222), false));
    }


    private void addMarketCard(JSONObject m) {
        MaterialCardView card = Ui.card(this);
        LinearLayout box = Ui.vertical(this, 12);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView asset = Ui.text(this, m.optString("asset", "-"), 14, Color.WHITE, true);
        row.addView(asset, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(Ui.text(this, m.optString("last", "—"), 14, Color.WHITE, true));
        box.addView(row);
        String change = m.optString("change", "—");
        int changeColor = change.trim().startsWith("+") ? Color.rgb(87,214,141) :
                (change.trim().startsWith("-") ? Color.rgb(255,107,107) : Color.rgb(247,201,72));
        TextView c = Ui.text(this, change, 12, changeColor, true);
        c.setPadding(0, Ui.dp(this, 4), 0, 0);
        box.addView(c);
        box.addView(Ui.text(this, m.optString("priceResponse", "-"), 12, Color.rgb(169,180,194), false));
        String source = m.optString("source", "");
        if (!source.trim().isEmpty()) box.addView(Ui.text(this, "Sumber: " + source, 10, Color.rgb(108,168,255), false));
        card.addView(box);
        root.addView(card);
    }

    private void addCalendarCard(JSONObject e) {
        MaterialCardView card = Ui.card(this);
        LinearLayout box = Ui.vertical(this, 12);
        box.addView(Ui.text(this, e.optString("datetimeWib", "-") + " • " + e.optString("currency", "-"), 10, impactColor(e.optString("impact", "LOW")), true));
        box.addView(Ui.text(this, e.optString("event", "-"), 14, Color.WHITE, true));
        String line = "Actual " + e.optString("actual", "—") + "   |   Forecast " + e.optString("forecast", "—") +
                "\nPrevious " + e.optString("previous", "—") + "   |   Revision " + e.optString("revision", "—");
        TextView metrics = Ui.text(this, line, 12, Color.rgb(205,213,222), false);
        metrics.setPadding(0, Ui.dp(this, 7), 0, 0);
        box.addView(metrics);
        String source = e.optString("source", "");
        if (!source.trim().isEmpty()) {
            TextView src = Ui.text(this, "Sumber: " + source, 10, Color.rgb(108,168,255), false);
            src.setPadding(0, Ui.dp(this, 6), 0, 0);
            box.addView(src);
        }
        card.addView(box);
        root.addView(card);
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 2001);
        }
    }
}
