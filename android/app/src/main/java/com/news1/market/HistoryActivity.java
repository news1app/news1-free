package com.news1.market;

import android.graphics.Color;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;

import org.json.JSONArray;
import org.json.JSONObject;

public class HistoryActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = Ui.vertical(this, 16);
        root.setBackgroundColor(Color.rgb(11,15,20));
        root.addView(Ui.text(this, "Riwayat NEWS1", 22, Color.WHITE, true));
        root.addView(Ui.text(this, "Maksimal 10 laporan terakhir", 12, Color.rgb(169,180,194), false));

        try {
            JSONArray history = new JSONArray(Prefs.get(this).getString(AppConfig.KEY_HISTORY_JSON, "[]"));
            if (history.length() == 0) {
                root.addView(Ui.text(this, "\nBelum ada riwayat live.", 14, Color.rgb(205,213,222), false));
            }
            for (int i = 0; i < history.length(); i++) {
                JSONObject item = history.optJSONObject(i);
                if (item == null) continue;
                JSONObject s = item.optJSONObject("summary");
                MaterialCardView card = Ui.card(this);
                LinearLayout box = Ui.vertical(this, 14);
                box.addView(Ui.text(this, item.optString("generatedAtWib", "-"), 12, Color.rgb(215,181,90), true));
                String text = s == null ? "-" : "XAU " + s.optString("xauusd", "-") +
                        " • DXY " + s.optString("dxy", "-") +
                        " • Yield " + s.optString("yield", "-") +
                        " • Oil " + s.optString("oil", "-");
                box.addView(Ui.text(this, text, 13, Color.WHITE, true));
                if (s != null) box.addView(Ui.text(this, "Katalis: " + s.optString("nextCatalyst", "-"), 12, Color.rgb(169,180,194), false));
                card.addView(box);
                root.addView(card);
            }
        } catch (Exception e) {
            root.addView(Ui.text(this, "Riwayat gagal dibaca: " + e.getMessage(), 13, Color.rgb(255,107,107), false));
        }
        scroll.addView(root);
        setContentView(scroll);
    }
}
