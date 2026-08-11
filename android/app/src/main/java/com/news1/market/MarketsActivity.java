package com.news1.market;

import android.graphics.Color;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;

import com.google.android.material.button.MaterialButton;

import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.card.MaterialCardView;
import org.json.JSONArray;
import org.json.JSONObject;

public class MarketsActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        Screen.Holder s=Screen.build(this,"Harga Pasar","Ringkas • satu baris per simbol",true);
        try{
            JSONObject data=new JSONObject(News1Repository.loadLatest(this));
            s.root.addView(Ui.text(this,"Update: "+data.optString("generatedAtWib","-"),Ui.sp(this,8.5f,10),Color.rgb(169,180,194),false));
            MaterialCardView card=Ui.card(this); LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
            JSONArray a=data.optJSONArray("marketSnapshot");
            if(a!=null){ for(int i=0;i<a.length();i++){ JSONObject m=a.optJSONObject(i); if(m==null)continue; box.addView(MarketRow.create(this,m,false)); if(i<a.length()-1)box.addView(divider()); }}
            card.addView(box); s.root.addView(card);
            MaterialButton live=new MaterialButton(this); live.setText("Buka Moomoo Live / Bandingkan Harga"); live.setOnClickListener(v->startActivity(new Intent(this,MoomooLiveActivity.class))); s.root.addView(live);
            s.root.addView(Ui.text(this,"Harga di halaman ini adalah reference snapshot GitHub. Untuk uji harga Moomoo langsung, buka tombol di atas. Futures tidak akan disamarkan sebagai XAUUSD spot.",Ui.sp(this,8,10),Color.rgb(150,163,178),false));
        }catch(Exception e){s.root.addView(Ui.text(this,"Gagal membaca data: "+e.getMessage(),11,Color.RED,false));}
    }
    private View divider(){ View v=new View(this);v.setBackgroundColor(Color.rgb(34,44,57));v.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,Ui.dp(this,1)));return v;}
}
