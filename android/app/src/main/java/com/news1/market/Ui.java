package com.news1.market;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;

public final class Ui {
    private Ui() {}

    public static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static float sp(Context c, float compact, float normal) {
        return Prefs.compact(c) ? compact : normal;
    }

    public static TextView text(Context c, String value, float sp, int color, boolean bold) {
        TextView tv = new TextView(c);
        tv.setText(value == null ? "" : value);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        if (bold) tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        tv.setLineSpacing(0, 1.08f);
        return tv;
    }

    public static TextView twoLines(Context c, String value, float sp, int color, boolean bold) {
        TextView tv = text(c, value, sp, color, bold);
        tv.setMaxLines(2);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        return tv;
    }

    public static MaterialCardView card(Context c) {
        MaterialCardView card = new MaterialCardView(c);
        card.setCardBackgroundColor(Color.rgb(18, 24, 33));
        card.setRadius(dp(c, Prefs.compact(c) ? 11 : 16));
        card.setStrokeWidth(dp(c, 1));
        card.setStrokeColor(Color.rgb(38, 48, 61));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(0, 0, 0, dp(c, Prefs.compact(c) ? 7 : 12));
        card.setLayoutParams(lp);
        return card;
    }

    public static LinearLayout vertical(Context c, int padding) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(c, padding), dp(c, padding), dp(c, padding), dp(c, padding));
        return l;
    }

    public static int impactColor(String impact) {
        String i = impact == null ? "" : impact.toUpperCase();
        if (i.contains("HIGH") || i.contains("TINGGI")) return Color.rgb(255,107,107);
        if (i.contains("MEDIUM") || i.contains("SEDANG")) return Color.rgb(247,201,72);
        return Color.rgb(108,168,255);
    }

    public static int changeColor(String change) {
        if (change == null) return Color.rgb(247,201,72);
        if (change.trim().startsWith("+")) return Color.rgb(87,214,141);
        if (change.trim().startsWith("-")) return Color.rgb(255,107,107);
        return Color.rgb(247,201,72);
    }

    public static int biasColor(String value) {
        String v = value == null ? "" : value.toUpperCase();
        if (v.contains("BULL")) return Color.rgb(87,214,141);
        if (v.contains("BEAR")) return Color.rgb(255,107,107);
        return Color.rgb(247,201,72);
    }
}
