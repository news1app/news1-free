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
    private TextInputEditText endpoint, moomooUrl, moomooToken;
    private Switch auto, compact, translate, source, analyzed, moomooEnabled;
    private Spinner interval, priceInterval;
    private final String[] labels={"15 menit","30 menit","1 jam","3 jam","6 jam","12 jam"};
    private final long[] mins={15,30,60,180,360,720};
    private final String[] priceLabels={"5 detik","10 detik","15 detik","30 detik","1 menit"};
    private final int[] priceSecs={5,10,15,30,60};

    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        Screen.Holder s=Screen.build(this,"Pengaturan","Refresh • tampilan • Moomoo Live",true);

        s.root.addView(Ui.text(this,"DATA NEWS1 GITHUB",Ui.sp(this,9,11),Color.rgb(108,168,255),true));
        TextInputLayout wrap=new TextInputLayout(this);wrap.setHint("URL RAW data/latest.json");endpoint=new TextInputEditText(this);endpoint.setSingleLine(true);endpoint.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);endpoint.setText(Prefs.endpoint(this));wrap.addView(endpoint);s.root.addView(wrap);

        auto=sw("Refresh otomatis NEWS1 di HP",Prefs.autoRefresh(this));s.root.addView(auto);
        s.root.addView(Ui.text(this,"Interval refresh laporan NEWS1",Ui.sp(this,8.5f,10.5f),Color.rgb(169,180,194),true));
        interval=new Spinner(this);interval.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels));long current=Prefs.intervalMinutes(this);int sel=1;for(int i=0;i<mins.length;i++)if(mins[i]==current)sel=i;interval.setSelection(sel);s.root.addView(interval);

        s.root.addView(Ui.text(this,"MOOMOO LIVE — UJI COBA",Ui.sp(this,9,11),Color.rgb(89,214,142),true));
        moomooEnabled=sw("Aktifkan harga Moomoo Live",Prefs.moomooEnabled(this));s.root.addView(moomooEnabled);

        TextInputLayout mWrap=new TextInputLayout(this);mWrap.setHint("URL bridge PC, contoh http://192.168.1.5:8765");moomooUrl=new TextInputEditText(this);moomooUrl.setSingleLine(true);moomooUrl.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);moomooUrl.setText(Prefs.moomooUrl(this));mWrap.addView(moomooUrl);s.root.addView(mWrap);

        TextInputLayout tokenWrap=new TextInputLayout(this);tokenWrap.setHint("Token bridge (opsional)");moomooToken=new TextInputEditText(this);moomooToken.setSingleLine(true);moomooToken.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);moomooToken.setText(Prefs.moomooToken(this));tokenWrap.addView(moomooToken);s.root.addView(tokenWrap);

        s.root.addView(Ui.text(this,"Refresh harga saat layar Moomoo dibuka",Ui.sp(this,8.5f,10.5f),Color.rgb(169,180,194),true));
        priceInterval=new Spinner(this);priceInterval.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,priceLabels));int pc=Prefs.priceRefreshSeconds(this);int psel=1;for(int i=0;i<priceSecs.length;i++)if(priceSecs[i]==pc)psel=i;priceInterval.setSelection(psel);s.root.addView(priceInterval);

        MaterialButton test=new MaterialButton(this);test.setText("TEST KONEKSI MOOMOO");test.setOnClickListener(v->{
            if(!savePrefs(false))return;
            test.setEnabled(false);test.setText("MENGUJI...");
            new Thread(()->{
                MoomooLiveRepository.Result r=MoomooLiveRepository.fetch(this);
                runOnUiThread(()->{test.setEnabled(true);test.setText("TEST KONEKSI MOOMOO");Toast.makeText(this,r.success?"Moomoo tersambung ✅":"Belum tersambung: "+r.message,Toast.LENGTH_LONG).show();});
            }).start();
        });s.root.addView(test);

        s.root.addView(Ui.text(this,"Catatan: Moomoo OpenAPI untuk uji ini dibaca melalui OpenD di PC yang sama Wi-Fi. Bridge hanya membuat Quote Context, tidak membuat Trade Context dan tidak dapat mengirim order.",Ui.sp(this,8,10),Color.rgb(247,201,72),false));

        s.root.addView(Ui.text(this,"TAMPILAN",Ui.sp(this,9,11),Color.rgb(108,168,255),true));
        compact=sw("Mode tampilan ringkas / teks kecil",Prefs.compact(this));s.root.addView(compact);
        translate=sw("Tampilkan terjemahan Indonesia ringan",Prefs.translate(this));s.root.addView(translate);
        source=sw("Tampilkan sumber data",Prefs.showSource(this));s.root.addView(source);
        analyzed=sw("Menu Berita: hanya yang punya analisis",Prefs.analysisOnly(this));s.root.addView(analyzed);

        MaterialButton save=new MaterialButton(this);save.setText("Simpan Pengaturan");LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,Ui.dp(this,52));lp.setMargins(0,Ui.dp(this,14),0,0);save.setLayoutParams(lp);s.root.addView(save);
        save.setOnClickListener(v->{if(savePrefs(true)){WorkScheduler.apply(this);Toast.makeText(this,"Pengaturan tersimpan.",Toast.LENGTH_SHORT).show();finish();}});
    }

    private boolean savePrefs(boolean validateEndpoint){
        String url=endpoint.getText()==null?"":endpoint.getText().toString().trim();
        if(validateEndpoint&&!url.isEmpty()&&!url.startsWith("https://")){Toast.makeText(this,"URL GitHub harus HTTPS.",Toast.LENGTH_LONG).show();return false;}
        String murl=moomooUrl.getText()==null?"":moomooUrl.getText().toString().trim();
        if(moomooEnabled.isChecked()&&!murl.isEmpty()&&!(murl.startsWith("http://")||murl.startsWith("https://"))){Toast.makeText(this,"URL Moomoo harus diawali http:// atau https://",Toast.LENGTH_LONG).show();return false;}
        String tok=moomooToken.getText()==null?"":moomooToken.getText().toString();
        int pos=interval.getSelectedItemPosition();int ppos=priceInterval.getSelectedItemPosition();
        Prefs.get(this).edit()
                .putString(AppConfig.KEY_ENDPOINT,url)
                .putBoolean(AppConfig.KEY_AUTO_REFRESH,auto.isChecked())
                .putLong(AppConfig.KEY_INTERVAL_MINUTES,mins[pos])
                .putBoolean(AppConfig.KEY_COMPACT,compact.isChecked())
                .putBoolean(AppConfig.KEY_TRANSLATE,translate.isChecked())
                .putBoolean(AppConfig.KEY_SHOW_SOURCE,source.isChecked())
                .putBoolean(AppConfig.KEY_ANALYSIS_ONLY,analyzed.isChecked())
                .putBoolean(AppConfig.KEY_MOOMOO_ENABLED,moomooEnabled.isChecked())
                .putString(AppConfig.KEY_MOOMOO_URL,murl)
                .putString(AppConfig.KEY_MOOMOO_TOKEN,tok)
                .putInt(AppConfig.KEY_PRICE_REFRESH_SECONDS,priceSecs[ppos])
                .apply();
        return true;
    }

    private Switch sw(String text,boolean checked){Switch s=new Switch(this);s.setText(text);s.setTextColor(Color.WHITE);s.setChecked(checked);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);lp.setMargins(0,Ui.dp(this,11),0,Ui.dp(this,5));s.setLayoutParams(lp);return s;}
}
