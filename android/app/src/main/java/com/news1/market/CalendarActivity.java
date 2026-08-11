package com.news1.market;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.card.MaterialCardView;
import org.json.JSONArray;
import org.json.JSONObject;

public class CalendarActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        Screen.Holder s=Screen.build(this,"Kalender Ekonomi","7 hari • waktu WIB",true);
        try{
            JSONObject data=new JSONObject(News1Repository.loadLatest(this)); JSONArray a=data.optJSONArray("calendar");
            if(a==null||a.length()==0){s.root.addView(Ui.text(this,"Kalender belum tersedia pada update ini.",11,Color.LTGRAY,false));return;}
            for(int i=0;i<a.length();i++){JSONObject e=a.optJSONObject(i);if(e!=null)add(s.root,e);}
        }catch(Exception e){s.root.addView(Ui.text(this,"Gagal membaca kalender: "+e.getMessage(),11,Color.RED,false));}
    }
    private void add(LinearLayout root,JSONObject e){
        MaterialCardView card=Ui.card(this);LinearLayout box=Ui.vertical(this,Prefs.compact(this)?8:12);
        box.addView(Ui.text(this,e.optString("datetimeWib","-")+" • "+e.optString("currency","-")+" • "+e.optString("impact","LOW"),Ui.sp(this,8,9.5f),Ui.impactColor(e.optString("impact")),true));
        String ev=Prefs.translate(this)&&!e.optString("eventId","").isEmpty()?e.optString("eventId"):e.optString("event","-");
        box.addView(Ui.twoLines(this,ev,Ui.sp(this,10,12),Color.WHITE,true));
        String values="Actual "+e.optString("actual","—")+"   Forecast "+e.optString("forecast","—")+"\nPrevious "+e.optString("previous","—")+"   Revision "+e.optString("revision","—");
        box.addView(Ui.text(this,values,Ui.sp(this,8.5f,10.5f),Color.rgb(194,205,217),false));
        if(Prefs.showSource(this)){TextViewLink(box,"Sumber: "+e.optString("source","-"),e.optString("url",""));TextViewLink(box,"Referensi: "+e.optString("referenceSource","Trading Economics"),e.optString("referenceUrl",""));}
        card.addView(box);root.addView(card);
    }
    private void TextViewLink(LinearLayout box,String text,String url){android.widget.TextView t=Ui.text(this,text,Ui.sp(this,7.5f,9),Color.rgb(108,168,255),false);if(url.startsWith("https://"))t.setOnClickListener(v->startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))));box.addView(t);}
}
