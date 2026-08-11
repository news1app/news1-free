package com.news1.market;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

public class MoomooLiveActivity extends AppCompatActivity {
    private LinearLayout root;
    private ProgressBar progress;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private JSONObject lastData;
    private boolean running;

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (!running) return;
            fetch(false);
            handler.postDelayed(this, Math.max(5, Prefs.priceRefreshSeconds(MoomooLiveActivity.this)) * 1000L);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Screen.Holder s = Screen.build(this, "Moomoo Live", "Uji harga langsung • read-only", true);
        root = s.root;
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        s.outer.addView(progress, 2, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this,2)));
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        running = true;
        handler.removeCallbacks(poll);
        handler.post(poll);
    }

    @Override protected void onPause() {
        running = false;
        handler.removeCallbacks(poll);
        super.onPause();
    }

    private void fetch(boolean toast) {
        if (!Prefs.moomooEnabled(this)) {
            render();
            return;
        }
        progress.setVisibility(View.VISIBLE);
        executor.submit(() -> {
            MoomooLiveRepository.Result result = MoomooLiveRepository.fetch(this);
            runOnUiThread(() -> {
                progress.setVisibility(View.GONE);
                if (result.success) lastData = result.json;
                render();
                if (toast || !result.success) {
                    Toast.makeText(this, result.success ? "Moomoo diperbarui." : "Moomoo: " + result.message, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void render() {
        root.removeAllViews();
        if (!Prefs.moomooEnabled(this)) {
            root.addView(Ui.text(this, "Moomoo Live belum diaktifkan.", 12, Color.WHITE, true));
            root.addView(Ui.text(this, "Buka Pengaturan → aktifkan Moomoo Live → isi URL bridge PC.", 10, Color.rgb(169,180,194), false));
            return;
        }

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        TextView state = Ui.text(this,
                lastData == null ? "Menunggu koneksi..." : "LIVE • " + lastData.optString("generatedAtWib", "-"),
                Ui.sp(this,9,11), lastData == null ? Color.rgb(247,201,72) : Color.rgb(89,214,142), true);
        top.addView(state, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        MaterialButton b = new MaterialButton(this); b.setText("REFRESH"); b.setTextSize(9);
        b.setOnClickListener(v -> fetch(true)); top.addView(b);
        root.addView(top);

        root.addView(Ui.text(this, "Bridge: " + Prefs.moomooUrl(this), Ui.sp(this,8,9.5f), Color.rgb(150,163,178), false));
        root.addView(Ui.text(this, "Refresh: " + Prefs.priceRefreshSeconds(this) + " detik", Ui.sp(this,8,9.5f), Color.rgb(150,163,178), false));

        if (lastData == null) return;
        JSONArray q = lastData.optJSONArray("quotes");
        MaterialCardView card = Ui.card(this);
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        if (q != null && q.length() > 0) {
            for (int i=0;i<q.length();i++) {
                JSONObject m=q.optJSONObject(i); if(m==null) continue;
                box.addView(MarketRow.create(this,m,false));
                if(i<q.length()-1) box.addView(divider());
            }
        } else {
            box.addView(Ui.text(this, "Tidak ada quote. Kemungkinan OpenD belum aktif, kontrak tidak ditemukan, atau quote right Moomoo belum tersedia.", 10, Color.rgb(247,201,72), false));
        }
        card.addView(box); root.addView(card);

        String note=lastData.optString("note","");
        if(!note.isEmpty()) root.addView(Ui.text(this,note,Ui.sp(this,8,10),Color.rgb(247,201,72),false));
        JSONArray warnings=lastData.optJSONArray("warnings");
        if(warnings!=null && warnings.length()>0){
            root.addView(Ui.text(this,"PERINGATAN",Ui.sp(this,9,11),Color.rgb(247,201,72),true));
            for(int i=0;i<warnings.length();i++) root.addView(Ui.text(this,"• "+warnings.optString(i),Ui.sp(this,8,10),Color.rgb(190,201,213),false));
        }
    }

    private View divider(){
        View v=new View(this);v.setBackgroundColor(Color.rgb(34,44,57));
        v.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,Ui.dp(this,1)));
        return v;
    }
}
