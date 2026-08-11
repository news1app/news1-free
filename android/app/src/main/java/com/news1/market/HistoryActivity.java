package com.news1.market;

import android.graphics.Color;
import android.os.Bundle;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.card.MaterialCardView;
import org.json.JSONArray;
import org.json.JSONObject;

public class HistoryActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle savedInstanceState){super.onCreate(savedInstanceState);Screen.Holder s=Screen.build(this,"Riwayat NEWS1","Maksimal 10 snapshot lokal",true);try{JSONArray h=new JSONArray(Prefs.get(this).getString(AppConfig.KEY_HISTORY_JSON,"[]"));if(h.length()==0)s.root.addView(Ui.text(this,"Belum ada riwayat live.",10,Color.LTGRAY,false));for(int i=0;i<h.length();i++){JSONObject x=h.optJSONObject(i);if(x==null)continue;JSONObject z=x.optJSONObject("summary");MaterialCardView c=Ui.card(this);LinearLayout b=Ui.vertical(this,Prefs.compact(this)?8:12);b.addView(Ui.text(this,x.optString("generatedAtWib","-"),Ui.sp(this,8,10),Color.rgb(215,181,90),true));if(z!=null){b.addView(Ui.text(this,"XAU "+z.optString("xauusd","-")+" • DXY "+z.optString("dxy","-")+" • Yield "+z.optString("yield","-")+" • Oil "+z.optString("oil","-"),Ui.sp(this,8.5f,10.5f),Color.WHITE,true));b.addView(Ui.twoLines(this,"Katalis: "+z.optString("nextCatalyst","-"),Ui.sp(this,8,10),Color.rgb(169,180,194),false));}c.addView(b);s.root.addView(c);}}catch(Exception e){s.root.addView(Ui.text(this,"Riwayat gagal dibaca: "+e.getMessage(),11,Color.RED,false));}}
}
