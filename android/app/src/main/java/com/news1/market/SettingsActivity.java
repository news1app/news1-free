package com.news1.market;

import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class SettingsActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        Screen.Holder s=Screen.build(this,"Pengaturan","Refresh • tampilan • berita",true);

        TextInputLayout wrap=new TextInputLayout(this);wrap.setHint("URL RAW data/latest.json");TextInputEditText endpoint=new TextInputEditText(this);endpoint.setSingleLine(true);endpoint.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);endpoint.setText(Prefs.endpoint(this));wrap.addView(endpoint);s.root.addView(wrap);

        Switch auto=sw("Refresh otomatis di HP",Prefs.autoRefresh(this));s.root.addView(auto);
        s.root.addView(Ui.text(this,"Interval refresh aplikasi",Ui.sp(this,8.5f,10.5f),Color.rgb(169,180,194),true));
        Spinner interval=new Spinner(this);String[] labels={"15 menit","30 menit","1 jam","3 jam","6 jam","12 jam"};long[] mins={15,30,60,180,360,720};interval.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels));long current=Prefs.intervalMinutes(this);int sel=1;for(int i=0;i<mins.length;i++)if(mins[i]==current)sel=i;interval.setSelection(sel);s.root.addView(interval);

        Switch compact=sw("Mode tampilan ringkas / teks kecil",Prefs.compact(this));s.root.addView(compact);
        Switch translate=sw("Tampilkan terjemahan Indonesia ringan",Prefs.translate(this));s.root.addView(translate);
        Switch source=sw("Tampilkan sumber data",Prefs.showSource(this));s.root.addView(source);
        Switch analyzed=sw("Menu Berita: hanya yang punya analisis",Prefs.analysisOnly(this));s.root.addView(analyzed);

        MaterialButton save=new MaterialButton(this);save.setText("Simpan Pengaturan");LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,Ui.dp(this,52));lp.setMargins(0,Ui.dp(this,14),0,0);save.setLayoutParams(lp);s.root.addView(save);
        s.root.addView(Ui.text(this,"Refresh 15/30 menit di HP hanya mengambil latest.json. Collector GitHub V2 dijadwalkan setiap 30 menit agar data server tetap gratis dan cukup segar.",Ui.sp(this,8,10),Color.rgb(247,201,72),false));
        save.setOnClickListener(v->{String url=endpoint.getText()==null?"":endpoint.getText().toString().trim();if(!url.isEmpty()&&!url.startsWith("https://")){Toast.makeText(this,"URL harus HTTPS.",Toast.LENGTH_LONG).show();return;}int pos=interval.getSelectedItemPosition();Prefs.get(this).edit().putString(AppConfig.KEY_ENDPOINT,url).putBoolean(AppConfig.KEY_AUTO_REFRESH,auto.isChecked()).putLong(AppConfig.KEY_INTERVAL_MINUTES,mins[pos]).putBoolean(AppConfig.KEY_COMPACT,compact.isChecked()).putBoolean(AppConfig.KEY_TRANSLATE,translate.isChecked()).putBoolean(AppConfig.KEY_SHOW_SOURCE,source.isChecked()).putBoolean(AppConfig.KEY_ANALYSIS_ONLY,analyzed.isChecked()).apply();WorkScheduler.apply(this);Toast.makeText(this,"Pengaturan tersimpan.",Toast.LENGTH_SHORT).show();finish();});
    }
    private Switch sw(String text,boolean checked){Switch s=new Switch(this);s.setText(text);s.setTextColor(Color.WHITE);s.setChecked(checked);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);lp.setMargins(0,Ui.dp(this,11),0,Ui.dp(this,5));s.setLayoutParams(lp);return s;}
}
