package com.news1.market;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;

public class MenuActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Screen.Holder s = Screen.build(this,"Menu NEWS1","Navigasi cepat",false);
        add(s.root,"Dashboard",MainActivity.class);
        add(s.root,"Harga Pasar",MarketsActivity.class);
        add(s.root,"Berita & Filter",NewsActivity.class);
        add(s.root,"Kalender Ekonomi",CalendarActivity.class);
        add(s.root,"Analisis",AnalysisActivity.class);
        add(s.root,"Update Data / Aplikasi",UpdateActivity.class);
        add(s.root,"Riwayat",HistoryActivity.class);
        add(s.root,"Sumber Data",SourcesActivity.class);
        add(s.root,"Pengaturan Tampilan & Refresh",SettingsActivity.class);
        s.root.addView(Ui.text(this,"Tip: menu Update dapat dipakai untuk refresh data sekarang atau membuka workflow GitHub tanpa mencari-cari halaman.",Ui.sp(this,9,11),Color.rgb(169,180,194),false));
    }
    private void add(LinearLayout root,String label,Class<?> cls){
        MaterialButton b=new MaterialButton(this); b.setText(label); b.setTextSize(Ui.sp(this,10,12));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,Ui.dp(this,48)); lp.setMargins(0,0,0,Ui.dp(this,7)); b.setLayoutParams(lp);
        b.setOnClickListener(v->startActivity(new Intent(this,cls))); root.addView(b);
    }
}
