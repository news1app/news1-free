package com.news1.market;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.card.MaterialCardView;
import org.json.JSONObject;

public class SourcesActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);Screen.Holder s=Screen.build(this,"Sumber Data","Transparansi sumber NEWS1 Free",true);
        add(s.root,"Trading Economics","Prioritas berita/referensi market. V2 menjalankan beberapa query khusus TE untuk komoditas, FX, yield, saham, makro dan geopolitik.","https://tradingeconomics.com/markets");
        add(s.root,"Reuters","Headline/metadata publik yang terindeks. Isi paywall tidak direkonstruksi.","https://www.reuters.com/markets/");
        add(s.root,"Bloomberg","Headline/metadata publik yang terindeks. Isi paywall tidak direkonstruksi.","https://www.bloomberg.com/markets");
        add(s.root,"Yahoo Finance","Sumber harga pasar publik utama pada mode gratis, plus headline publik.","https://finance.yahoo.com/markets/");
        add(s.root,"Forex Factory / FairEconomy","Feed kalender ekonomi publik 7 hari; Trading Economics dipakai sebagai referensi kalender.","https://www.forexfactory.com/calendar");
        try{JSONObject d=new JSONObject(News1Repository.loadLatest(this));JSONObject st=d.optJSONObject("sourceStats");if(st!=null){String txt="Trading Economics "+st.optInt("Trading Economics",0)+" • Reuters "+st.optInt("Reuters",0)+" • Bloomberg "+st.optInt("Bloomberg",0)+" • Yahoo Finance "+st.optInt("Yahoo Finance",0)+" • Forex Factory "+st.optInt("Forex Factory",0);s.root.addView(Ui.text(this,"Komposisi update terakhir:\n"+txt,Ui.sp(this,8.5f,10.5f),Color.rgb(247,201,72),false));}}catch(Exception ignored){}
    }
    private void add(LinearLayout root,String title,String body,String url){MaterialCardView c=Ui.card(this);LinearLayout b=Ui.vertical(this,Prefs.compact(this)?9:13);b.addView(Ui.text(this,title,Ui.sp(this,10,12),Color.WHITE,true));b.addView(Ui.twoLines(this,body,Ui.sp(this,8.5f,10.5f),Color.rgb(194,205,217),false));android.widget.TextView l=Ui.text(this,"Buka sumber ↗",Ui.sp(this,8,10),Color.rgb(108,168,255),true);l.setOnClickListener(v->startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))));b.addView(l);c.addView(b);root.addView(c);}
}
