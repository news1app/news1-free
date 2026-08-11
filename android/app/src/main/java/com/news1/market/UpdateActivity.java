package com.news1.market;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UpdateActivity extends AppCompatActivity {
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private LinearLayout root;
    @Override protected void onCreate(Bundle savedInstanceState){super.onCreate(savedInstanceState);Screen.Holder s=Screen.build(this,"Update","Data dan aplikasi",true);root=s.root;render();}
    private void render(){root.removeAllViews();String latest="-";try{latest=new JSONObject(News1Repository.loadLatest(this)).optString("generatedAtWib","-");}catch(Exception ignored){}root.addView(Ui.text(this,"Data lokal terakhir: "+latest,Ui.sp(this,9,11),Color.rgb(169,180,194),false));button("Refresh data dari GitHub sekarang",v->refresh());button("Buka workflow Update NEWS1",v->open(workflowUrl("update-news1.yml")));button("Buka data latest.json",v->open(Prefs.endpoint(this)));button("Buka workflow Build APK",v->open(workflowUrl("build-apk.yml")));root.addView(Ui.text(this,"Catatan: tombol Refresh mengambil latest.json terbaru. Workflow GitHub yang mengumpulkan data berjalan otomatis terjadwal; bila ingin memaksa collector berjalan saat itu juga, buka workflow Update NEWS1 lalu tekan Run workflow.",Ui.sp(this,8.5f,10.5f),Color.rgb(169,180,194),false));}
    private void refresh(){executor.submit(()->{News1Repository.FetchResult r=News1Repository.refresh(this);runOnUiThread(()->{Toast.makeText(this,r.success?"Data diperbarui.":"Gagal: "+r.message,Toast.LENGTH_LONG).show();if(r.success)render();});});}
    private void button(String text,android.view.View.OnClickListener l){MaterialButton b=new MaterialButton(this);b.setText(text);b.setTextSize(Ui.sp(this,10,12));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,Ui.dp(this,49));lp.setMargins(0,Ui.dp(this,8),0,0);b.setLayoutParams(lp);b.setOnClickListener(l);root.addView(b);}
    private void open(String url){if(url!=null&&url.startsWith("https://"))startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));else Toast.makeText(this,"URL belum valid.",Toast.LENGTH_SHORT).show();}
    private String workflowUrl(String file){String e=Prefs.endpoint(this);try{String p="https://raw.githubusercontent.com/";if(!e.startsWith(p))return "https://github.com";String rest=e.substring(p.length());String[] x=rest.split("/");if(x.length<2)return "https://github.com";return "https://github.com/"+x[0]+"/"+x[1]+"/actions/workflows/"+file;}catch(Exception ex){return "https://github.com";}}
}
