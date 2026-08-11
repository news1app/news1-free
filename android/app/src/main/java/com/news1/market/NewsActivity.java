package com.news1.market;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.card.MaterialCardView;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

public class NewsActivity extends AppCompatActivity {
    private LinearLayout list;
    private JSONArray news;
    private Spinner sourceSpinner, categorySpinner;

    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        Screen.Holder s=Screen.build(this,"Berita","Trading • makro • geopolitik • per sumber",true);
        try{ news=new JSONObject(News1Repository.loadLatest(this)).optJSONArray("news"); }catch(Exception e){ news=new JSONArray(); }
        if(news==null) news=new JSONArray();

        LinearLayout filters=new LinearLayout(this); filters.setOrientation(LinearLayout.HORIZONTAL);
        sourceSpinner=new Spinner(this); categorySpinner=new Spinner(this);
        sourceSpinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,sourceValues()));
        categorySpinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,categoryValues()));
        filters.addView(sourceSpinner,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1f));
        filters.addView(categorySpinner,new LinearLayout.LayoutParams(0,Ui.dp(this,48),1f));
        s.root.addView(filters);
        TextView hint=Ui.text(this,"Default menampilkan semua sumber. Trading Economics diprioritaskan dalam collector V2.",Ui.sp(this,8,10),Color.rgb(150,163,178),false); hint.setPadding(0,0,0,Ui.dp(this,8));s.root.addView(hint);
        list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);s.root.addView(list);
        android.widget.AdapterView.OnItemSelectedListener listener=new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?>p,android.view.View v,int pos,long id){render();} public void onNothingSelected(android.widget.AdapterView<?>p){}};
        sourceSpinner.setOnItemSelectedListener(listener);categorySpinner.setOnItemSelectedListener(listener);
        render();
    }

    private String[] sourceValues(){Set<String>s=new LinkedHashSet<>();s.add("Semua Sumber");s.add("Trading Economics");for(int i=0;i<news.length();i++){JSONObject n=news.optJSONObject(i);if(n!=null&&!n.optString("source").isEmpty())s.add(n.optString("source"));}return s.toArray(new String[0]);}
    private String[] categoryValues(){Set<String>s=new LinkedHashSet<>();s.add("Semua Kategori");for(int i=0;i<news.length();i++){JSONObject n=news.optJSONObject(i);if(n!=null&&!n.optString("category").isEmpty())s.add(n.optString("category"));}return s.toArray(new String[0]);}

    private void render(){
        list.removeAllViews();String sf=String.valueOf(sourceSpinner.getSelectedItem());String cf=String.valueOf(categorySpinner.getSelectedItem());int shown=0;
        for(int i=0;i<news.length();i++){
            JSONObject n=news.optJSONObject(i);if(n==null)continue;
            if(!"Semua Sumber".equals(sf)&&!sf.equals(n.optString("source")))continue;
            if(!"Semua Kategori".equals(cf)&&!cf.equals(n.optString("category")))continue;
            if(Prefs.analysisOnly(this)&&n.optString("analysis","").trim().isEmpty())continue;
            addNews(n);shown++;
        }
        if(shown==0)list.addView(Ui.text(this,"Tidak ada berita untuk filter ini.",Ui.sp(this,10,12),Color.rgb(169,180,194),false));
    }

    private void addNews(JSONObject n){
        MaterialCardView card=Ui.card(this);LinearLayout box=Ui.vertical(this,Prefs.compact(this)?9:13);
        String meta=n.optString("publishedAtWib","-")+" • "+n.optString("source","-")+" • "+n.optString("category","-")+" • "+n.optString("impact","LOW");
        box.addView(Ui.twoLines(this,meta,Ui.sp(this,7.8f,9.5f),Ui.impactColor(n.optString("impact")),true));
        String title=(Prefs.translate(this)&&!n.optString("titleId","").trim().isEmpty())?n.optString("titleId"):n.optString("title","-");
        TextView t=Ui.twoLines(this,title,Ui.sp(this,10,12.5f),Color.WHITE,true);t.setPadding(0,Ui.dp(this,4),0,0);box.addView(t);
        if(Prefs.translate(this)&&!n.optString("titleId","").isEmpty()){
            TextView original=Ui.twoLines(this,n.optString("title",""),Ui.sp(this,7.5f,9),Color.rgb(130,145,161),false);box.addView(original);
        }
        addIf(box,"Analisis",n.optString("analysis",""),Color.rgb(205,213,222));
        addIf(box,"Reaksi",n.optString("priceResponse",""),Color.rgb(108,168,255));
        addIf(box,"⚠ Konflik",n.optString("contradictions",""),Color.rgb(247,201,72));
        JSONArray imp=n.optJSONArray("assetImpacts");if(imp!=null&&imp.length()>0){StringBuilder b=new StringBuilder();for(int i=0;i<imp.length()&&i<5;i++){JSONObject x=imp.optJSONObject(i);if(x==null)continue;if(b.length()>0)b.append("  •  ");b.append(x.optString("asset")).append(" ").append(x.optString("direction"));}addIf(box,"Aset",b.toString(),Color.rgb(180,191,204));}
        String url=n.optString("url","");if(url.startsWith("https://")){TextView open=Ui.text(this,"Buka sumber ↗",Ui.sp(this,8.5f,10.5f),Color.rgb(108,168,255),true);open.setPadding(0,Ui.dp(this,6),0,0);open.setOnClickListener(v->startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))));box.addView(open);}
        card.addView(box);list.addView(card);
    }
    private void addIf(LinearLayout box,String label,String value,int color){if(value==null||value.trim().isEmpty()||"—".equals(value.trim()))return;TextView l=Ui.text(this,label,Ui.sp(this,7.5f,9),Color.rgb(215,181,90),true);l.setPadding(0,Ui.dp(this,5),0,0);box.addView(l);box.addView(Ui.twoLines(this,value,Ui.sp(this,8.5f,10.5f),color,false));}
}
