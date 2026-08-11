package com.news1.market;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

public final class MarketRow {
    private MarketRow() {}

    public static LinearLayout create(Context c, JSONObject m, boolean linkEnabled) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int py = Prefs.compact(c) ? 8 : 11;
        row.setPadding(Ui.dp(c,8), Ui.dp(c,py), Ui.dp(c,8), Ui.dp(c,py));

        LinearLayout left = new LinearLayout(c);
        left.setOrientation(LinearLayout.VERTICAL);
        row.addView(left, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        String asset = m.optString("asset", "-");
        String label = m.optString("labelId", asset);
        left.addView(Ui.text(c, asset, Ui.sp(c,11,13), Color.WHITE, true));
        TextView desc = Ui.twoLines(c, label + " • " + m.optString("comment", ""), Ui.sp(c,8.5f,10.5f), Color.rgb(155,168,183), false);
        left.addView(desc);

        LinearLayout right = new LinearLayout(c);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.END);
        TextView last = Ui.text(c, m.optString("last", "—"), Ui.sp(c,11,13), Color.WHITE, true);
        last.setGravity(Gravity.END);
        right.addView(last);
        String ch = m.optString("change", "—");
        TextView change = Ui.text(c, ch, Ui.sp(c,9,11), Ui.changeColor(ch), true);
        change.setGravity(Gravity.END);
        right.addView(change);
        if (Prefs.showSource(c)) {
            TextView src = Ui.twoLines(c, m.optString("source", ""), Ui.sp(c,7.5f,9), Color.rgb(108,168,255), false);
            src.setGravity(Gravity.END);
            right.addView(src);
        }
        row.addView(right, new LinearLayout.LayoutParams(Ui.dp(c, Prefs.compact(c) ? 126 : 145), LinearLayout.LayoutParams.WRAP_CONTENT));
        return row;
    }
}
