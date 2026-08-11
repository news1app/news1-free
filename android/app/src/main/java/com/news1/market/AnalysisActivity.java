package com.news1.market;

import android.graphics.Color;
import android.os.Bundle;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.card.MaterialCardView;
import org.json.JSONArray;
import org.json.JSONObject;

public class AnalysisActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        Screen.Holder s=Screen.build(this,"Analisis NEWS1","Hanya konten yang benar-benar memiliki analisis",true);
        try{
            JSONObject data=new JSONObject(News1Repository.loadLatest(this)); JSONObject sum=data.optJSONObject("summary");
            if(sum!=null){
                add(s.root,"Bias XAUUSD",sum.optString("xauusd",""),Ui.biasColor(sum.optString("xauusd")));
                add(s.root,"Bias DXY",sum.optString("dxy",""),Ui.biasColor(sum.optString("dxy")));
                add(s.root,"Bias Yield",sum.optString("yield",""),Ui.biasColor(sum.optString("yield")));
                add(s.root,"Bias Oil",sum.optString("oil",""),Ui.biasColor(sum.optString("oil")));
                add(s.root,"Aset dominan",sum.optString("dominantAsset",""),Color.WHITE);
                add(s.root,"Katalis berikutnya",sum.optString("nextCatalyst",""),Color.WHITE);
                add(s.root,"Skenario bullish",sum.optString("bullScenario",""),Color.rgb(87,214,141));
                add(s.root,"Skenario bearish",sum.optString("bearScenario",""),Color.rgb(255,107,107));
                add(s.root,"Keyakinan",sum.optInt("confidencePct",0)+"% • "+sum.optString("confidenceLabel","RULE-BASED"),Color.rgb(247,201,72));
            }
            JSONArray news=data.optJSONArray("news");int count=0;
            if(news!=null){for(int i=0;i<news.length();i++){JSONObject n=news.optJSONObject(i);if(n==null||n.optString("analysis","").trim().isEmpty())continue;count++;MaterialCardView card=Ui.card(this);LinearLayout box=Ui.vertical(this,Prefs.compact(this)?9:13);box.addView(Ui.text(this,n.optString("source")+" • "+n.optString("category")+" • "+n.optString("impact"),Ui.sp(this,7.5f,9),Ui.impactColor(n.optString("impact")),true));String title=Prefs.translate(this)&&!n.optString("titleId","").isEmpty()?n.optString("titleId"):n.optString("title");box.addView(Ui.twoLines(this,title,Ui.sp(this,9.5f,11.5f),Color.WHITE,true));box.addView(Ui.twoLines(this,n.optString("analysis"),Ui.sp(this,8.5f,10.5f),Color.rgb(205,213,222),false));String c=n.optString("contradictions","");if(!c.isEmpty())box.addView(Ui.twoLines(this,"⚠ "+c,Ui.sp(this,8,10),Color.rgb(247,201,72),true));card.addView(box);s.root.addView(card);}}
            if(count==0)s.root.addView(Ui.text(this,"Belum ada berita yang memenuhi rule analisis pada update ini.",10,Color.LTGRAY,false));
        }catch(Exception e){s.root.addView(Ui.text(this,"Gagal: "+e.getMessage(),11,Color.RED,false));}
    }
    private void add(LinearLayout root,String title,String value,int color){if(value==null||value.trim().isEmpty()||"—".equals(value.trim()))return;MaterialCardView c=Ui.card(this);LinearLayout b=Ui.vertical(this,Prefs.compact(this)?8:12);b.addView(Ui.text(this,title,Ui.sp(this,8,10),Color.rgb(169,180,194),true));b.addView(Ui.twoLines(this,value,Ui.sp(this,10,12),color,true));c.addView(b);root.addView(c);}
}
