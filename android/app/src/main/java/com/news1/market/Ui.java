package com.news1.market;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;

public final class Ui {
    private Ui() {}

    public static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static TextView text(Context c, String value, float sp, int color, boolean bold) {
        TextView tv = new TextView(c);
        tv.setText(value);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        if (bold) tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        tv.setLineSpacing(0, 1.12f);
        return tv;
    }

    public static MaterialCardView card(Context c) {
        MaterialCardView card = new MaterialCardView(c);
        card.setCardBackgroundColor(Color.rgb(18, 24, 33));
        card.setRadius(dp(c, 16));
        card.setStrokeWidth(dp(c, 1));
        card.setStrokeColor(Color.rgb(38, 48, 61));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(0, 0, 0, dp(c, 12));
        card.setLayoutParams(lp);
        return card;
    }

    public static LinearLayout vertical(Context c, int padding) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(c, padding), dp(c, padding), dp(c, padding), dp(c, padding));
        return l;
    }
}
