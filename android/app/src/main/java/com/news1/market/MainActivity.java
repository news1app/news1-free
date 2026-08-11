package com.news1.market;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
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
        build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        WorkScheduler.apply(this);
        if (root != null) render(News1Repository.loadLatest(this));
    }

    private void build() {
        Screen.Holder s = Screen.build(this, "NEWS1", "Market Intelligence • Free V2 • WIB", true);
        root = s.root;
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        s.outer.addView(progress, 2, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this,2)));
        render(News1Repository.loadLatest(this));
    }

    private void render(String json) {
        root.removeAllViews();
        try {
            JSONObject data = new JSONObject(json);
            String generated = data.optString("generatedAtWib", "Belum ada data");
            LinearLayout statusRow = new LinearLayout(this);
            statusRow.setGravity(Gravity.CENTER_VERTICAL);
            TextView status = Ui.text(this, "Update data: " + generated, Ui.sp(this,9,11), Color.rgb(169,180,194), true);
            statusRow.addView(status, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            MaterialButton update = tinyButton("UPDATE");
            update.setOnClickListener(v -> refreshNow());
            statusRow.addView(update);
            root.addView(statusRow);

            JSONObject summary = data.optJSONObject("summary");
            if (summary != null) {
                section("BIAS UTAMA", null);
                LinearLayout bias = new LinearLayout(this);
                bias.setOrientation(LinearLayout.HORIZONTAL);
                bias.addView(biasBox("XAU", summary.optString("xauusd","-")), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT,1f));
                bias.addView(biasBox("DXY", summary.optString("dxy","-")), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT,1f));
                bias.addView(biasBox("YIELD", summary.optString("yield","-")), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT,1f));
                bias.addView(biasBox("OIL", summary.optString("oil","-")), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT,1f));
                root.addView(bias);
                compactInfo("Katalis berikutnya", summary.optString("nextCatalyst", "-"));
            }

            JSONArray market = data.optJSONArray("marketSnapshot");
            section("HARGA PASAR", "Satu baris per simbol • ±24 jam");
            MaterialCardView marketCard = Ui.card(this);
            LinearLayout marketBox = new LinearLayout(this);
            marketBox.setOrientation(LinearLayout.VERTICAL);
            if (market != null) {
                int max = Math.min(market.length(), 14);
                for (int i=0;i<max;i++) {
                    JSONObject m = market.optJSONObject(i);
                    if (m == null) continue;
                    marketBox.addView(MarketRow.create(this,m,false));
                    if (i < max-1) marketBox.addView(divider());
                }
            }
            marketCard.addView(marketBox);
            root.addView(marketCard);
            MaterialButton allMarket = lineButton("Lihat semua harga");
            allMarket.setOnClickListener(v -> startActivity(new Intent(this, MarketsActivity.class)));
            root.addView(allMarket);

            JSONArray news = data.optJSONArray("news");
            section("BERITA TERANALISIS", "Hanya item yang punya analisis • 24 jam");
            int shown = 0;
            if (news != null) {
                for (int i=0;i<news.length() && shown<5;i++) {
                    JSONObject n = news.optJSONObject(i);
                    if (n == null || n.optString("analysis","").trim().isEmpty()) continue;
                    addCompactNews(n);
                    shown++;
                }
            }
            if (shown == 0) compactInfo("Belum ada analisis", "Berita tetap tersedia di menu Berita; analisis kosong tidak ditampilkan di dashboard.");
            MaterialButton allNews = lineButton("Buka semua berita & filter sumber");
            allNews.setOnClickListener(v -> startActivity(new Intent(this, NewsActivity.class)));
            root.addView(allNews);

            JSONArray cal = data.optJSONArray("calendar");
            section("KALENDER TERDEKAT", "High impact diprioritaskan • WIB");
            int cshown = 0;
            if (cal != null) {
                for (int pass=0; pass<2 && cshown<5; pass++) {
                    for (int i=0;i<cal.length() && cshown<5;i++) {
                        JSONObject e = cal.optJSONObject(i);
                        if (e == null) continue;
                        boolean high = "HIGH".equalsIgnoreCase(e.optString("impact"));
                        if ((pass==0 && !high) || (pass==1 && high)) continue;
                        addCompactCalendar(e);
                        cshown++;
                    }
                }
            }
            MaterialButton allCal = lineButton("Buka kalender 7 hari");
            allCal.setOnClickListener(v -> startActivity(new Intent(this, CalendarActivity.class)));
            root.addView(allCal);

        } catch (Exception e) {
            compactInfo("Data tidak dapat dibaca", e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    private void refreshNow() {
        progress.setVisibility(View.VISIBLE);
        executor.submit(() -> {
            News1Repository.FetchResult r = News1Repository.refresh(this);
            runOnUiThread(() -> {
                progress.setVisibility(View.GONE);
                if (r.success) render(r.json);
                Toast.makeText(this, r.success ? "Data terbaru dimuat." : "Gagal: " + r.message, Toast.LENGTH_LONG).show();
            });
        });
    }

    private MaterialButton tinyButton(String text) {
        MaterialButton b = new MaterialButton(this);
        b.setText(text); b.setTextSize(9); b.setMinWidth(0); b.setMinimumWidth(0); b.setInsetTop(0); b.setInsetBottom(0);
        b.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(this,36)));
        return b;
    }

    private MaterialButton lineButton(String text) {
        MaterialButton b = new MaterialButton(this);
        b.setText(text); b.setTextSize(Ui.sp(this,9,11));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this,42));
        lp.setMargins(0,0,0,Ui.dp(this,8)); b.setLayoutParams(lp);
        return b;
    }

    private void section(String title, String sub) {
        TextView t = Ui.text(this,title,Ui.sp(this,12,15),Color.WHITE,true);
        t.setPadding(0,Ui.dp(this,10),0,sub==null?Ui.dp(this,6):0); root.addView(t);
        if (sub != null) {
            TextView s = Ui.text(this,sub,Ui.sp(this,8.5f,10.5f),Color.rgb(150,163,178),false);
            s.setPadding(0,0,0,Ui.dp(this,6)); root.addView(s);
        }
    }

    private LinearLayout biasBox(String label, String value) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setGravity(Gravity.CENTER);
        box.setPadding(Ui.dp(this,3),Ui.dp(this,8),Ui.dp(this,3),Ui.dp(this,8));
        box.addView(Ui.text(this,label,Ui.sp(this,8,9.5f),Color.rgb(150,163,178),true));
        TextView v = Ui.text(this,value,Ui.sp(this,9.5f,11.5f),Ui.biasColor(value),true); v.setGravity(Gravity.CENTER); box.addView(v);
        return box;
    }

    private View divider() {
        View v = new View(this); v.setBackgroundColor(Color.rgb(34,44,57));
        v.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this,1)));
        return v;
    }

    private void compactInfo(String title, String body) {
        MaterialCardView card=Ui.card(this); LinearLayout box=Ui.vertical(this,Prefs.compact(this)?9:13);
        box.addView(Ui.text(this,title,Ui.sp(this,10,12),Color.WHITE,true));
        TextView b=Ui.twoLines(this,body,Ui.sp(this,9,11),Color.rgb(197,207,218),false); b.setPadding(0,Ui.dp(this,3),0,0); box.addView(b);
        card.addView(box); root.addView(card);
    }

    private void addCompactNews(JSONObject n) {
        MaterialCardView card=Ui.card(this); LinearLayout box=Ui.vertical(this,Prefs.compact(this)?9:13);
        String meta=n.optString("source","-")+" • "+n.optString("category","Umum")+" • "+n.optString("impact","LOW");
        box.addView(Ui.text(this,meta,Ui.sp(this,8,9.5f),Ui.impactColor(n.optString("impact")),true));
        String title = Prefs.translate(this) && !n.optString("titleId","").isEmpty() ? n.optString("titleId") : n.optString("title","-");
        TextView t=Ui.twoLines(this,title,Ui.sp(this,10,12),Color.WHITE,true); t.setPadding(0,Ui.dp(this,3),0,Ui.dp(this,3)); box.addView(t);
        TextView a=Ui.twoLines(this,n.optString("analysis",""),Ui.sp(this,8.5f,10.5f),Color.rgb(194,205,217),false); box.addView(a);
        String pr=n.optString("priceResponse",""); if(!pr.isEmpty()) box.addView(Ui.twoLines(this,pr,Ui.sp(this,8,9.5f),Color.rgb(108,168,255),true));
        card.addView(box); root.addView(card);
    }

    private void addCompactCalendar(JSONObject e) {
        MaterialCardView card=Ui.card(this); LinearLayout box=Ui.vertical(this,Prefs.compact(this)?8:12);
        box.addView(Ui.text(this,e.optString("datetimeWib","-")+" • "+e.optString("currency","-"),Ui.sp(this,8,9.5f),Ui.impactColor(e.optString("impact")),true));
        String ev=Prefs.translate(this)&&!e.optString("eventId","").isEmpty()?e.optString("eventId"):e.optString("event","-");
        box.addView(Ui.twoLines(this,ev,Ui.sp(this,10,12),Color.WHITE,true));
        String line="A "+e.optString("actual","—")+" | F "+e.optString("forecast","—")+" | P "+e.optString("previous","—");
        box.addView(Ui.text(this,line,Ui.sp(this,8.5f,10),Color.rgb(190,201,213),false));
        card.addView(box); root.addView(card);
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 2001);
        }
    }
}
