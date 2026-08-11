package com.news1.market;

import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class SettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = Ui.vertical(this, 20);
        root.setBackgroundColor(Color.rgb(11,15,20));

        TextView title = Ui.text(this, "Pengaturan NEWS1", 22, Color.WHITE, true);
        root.addView(title);
        TextView note = Ui.text(this, "Masukkan URL RAW GitHub data/latest.json. Jika APK dibuild lewat GitHub Actions, alamat ini biasanya sudah terisi otomatis.", 12, Color.rgb(169,180,194), false);
        note.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 18));
        root.addView(note);

        TextInputLayout endpointWrap = new TextInputLayout(this);
        endpointWrap.setHint("URL data GitHub HTTPS");
        TextInputEditText endpoint = new TextInputEditText(this);
        endpoint.setSingleLine(true);
        endpoint.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        endpoint.setText(Prefs.endpoint(this));
        endpointWrap.addView(endpoint);
        root.addView(endpointWrap);

        Switch auto = new Switch(this);
        auto.setText("Refresh otomatis + notifikasi");
        auto.setTextColor(Color.WHITE);
        auto.setChecked(Prefs.autoRefresh(this));
        LinearLayout.LayoutParams autoLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        autoLp.setMargins(0, Ui.dp(this, 18), 0, Ui.dp(this, 8));
        root.addView(auto, autoLp);

        TextView intervalLabel = Ui.text(this, "Interval pengecekan", 12, Color.rgb(169,180,194), true);
        root.addView(intervalLabel);
        Spinner spinner = new Spinner(this);
        String[] values = {"1 jam", "3 jam", "6 jam", "12 jam"};
        long[] hours = {1,3,6,12};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values);
        spinner.setAdapter(adapter);
        long current = Prefs.intervalHours(this);
        int selected = 0;
        for (int i = 0; i < hours.length; i++) if (hours[i] == current) selected = i;
        spinner.setSelection(selected);
        root.addView(spinner);

        MaterialButton save = new MaterialButton(this);
        save.setText("Simpan Pengaturan");
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 54));
        saveLp.setMargins(0, Ui.dp(this, 24), 0, 0);
        root.addView(save, saveLp);

        TextView security = Ui.text(this,
                "NEWS1 Free tidak memakai OpenAI API atau Render. Tidak ada API key yang perlu disimpan.",
                11, Color.rgb(247,201,72), false);
        security.setGravity(Gravity.CENTER_HORIZONTAL);
        security.setPadding(0, Ui.dp(this, 18), 0, 0);
        root.addView(security);

        save.setOnClickListener(v -> {
            String url = endpoint.getText() == null ? "" : endpoint.getText().toString().trim();
            if (!url.trim().isEmpty() && !url.startsWith("https://")) {
                Toast.makeText(this, "Gunakan URL HTTPS GitHub.", Toast.LENGTH_LONG).show();
                return;
            }
            int pos = spinner.getSelectedItemPosition();
            Prefs.get(this).edit()
                    .putString(AppConfig.KEY_ENDPOINT, url)
                    .putBoolean(AppConfig.KEY_AUTO_REFRESH, auto.isChecked())
                    .putLong(AppConfig.KEY_INTERVAL_HOURS, hours[pos])
                    .apply();
            WorkScheduler.apply(this);
            Toast.makeText(this, "Pengaturan tersimpan.", Toast.LENGTH_SHORT).show();
            finish();
        });

        setContentView(root);
    }
}
