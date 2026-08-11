package com.news1.market;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.google.android.material.button.MaterialButton;

public final class Screen {
    private Screen() {}

    public static final class Holder {
        public final LinearLayout outer;
        public final LinearLayout root;
        public Holder(LinearLayout outer, LinearLayout root) {
            this.outer = outer;
            this.root = root;
        }
    }

    public static Holder build(Activity a, String title, String subtitle, boolean showMenu) {
        LinearLayout outer = new LinearLayout(a);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(Color.rgb(11,15,20));

        LinearLayout top = new LinearLayout(a);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(Ui.dp(a,14), Ui.dp(a,14), Ui.dp(a,10), Ui.dp(a,8));

        LinearLayout titles = new LinearLayout(a);
        titles.setOrientation(LinearLayout.VERTICAL);
        top.addView(titles, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        titles.addView(Ui.text(a, title, Ui.sp(a,19,22), Color.WHITE, true));
        if (subtitle != null && !subtitle.trim().isEmpty()) {
            titles.addView(Ui.text(a, subtitle, Ui.sp(a,10,12), Color.rgb(169,180,194), false));
        }

        MaterialButton menu = new MaterialButton(a);
        menu.setText(showMenu ? "☰" : "←");
        menu.setTextSize(16);
        menu.setMinWidth(0); menu.setMinimumWidth(0); menu.setInsetTop(0); menu.setInsetBottom(0);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(Ui.dp(a,48), Ui.dp(a,42));
        menu.setLayoutParams(mlp);
        menu.setOnClickListener(v -> {
            if (showMenu) a.startActivity(new Intent(a, MenuActivity.class)); else a.finish();
        });
        top.addView(menu);
        outer.addView(top);

        View line = new View(a);
        line.setBackgroundColor(Color.rgb(31,41,54));
        outer.addView(line, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(a,1)));

        ScrollView scroll = new ScrollView(a);
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = Prefs.compact(a) ? 11 : 16;
        root.setPadding(Ui.dp(a,p), Ui.dp(a,8), Ui.dp(a,p), Ui.dp(a,24));
        scroll.addView(root);
        outer.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        a.setContentView(outer);
        return new Holder(outer, root);
    }
}
