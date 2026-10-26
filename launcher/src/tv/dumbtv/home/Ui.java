package tv.dumbtv.home;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.widget.TextView;

final class Ui {
    static final int DIALOG = android.R.style.Theme_DeviceDefault_Dialog_Alert;

    // Copied from the active Theme by use(); views read them when they are built.
    static Theme theme;
    static int TEXT, MUTED, FAINT, SURFACE, INPUT_SURFACE, PAGE;
    static Typeface LIGHT, MEDIUM;

    static {
        colors(Theme.ORIGINAL);
    }

    private Ui() {}

    /** Called by each screen before it builds its views. */
    static void use(Theme t) {
        colors(t);
        LIGHT = t.light();
        MEDIUM = t.medium();
    }

    private static void colors(Theme t) {
        theme = t;
        TEXT = t.text;
        MUTED = t.muted;
        FAINT = t.faint;
        SURFACE = t.surface;
        INPUT_SURFACE = t.inputSurface;
        PAGE = t.page;
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /** Black on light accents, white on dark ones. */
    static int ink(int color) {
        return Color.luminance(color) > 0.5f ? 0xFF000000 : 0xFFFFFFFF;
    }

    static TextView text(Context c, float sp, int color, Typeface face) {
        TextView v = new TextView(c);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setTextColor(color);
        v.setTypeface(face);
        v.setIncludeFontPadding(false);
        return v;
    }

    static GradientDrawable round(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    /** Accent fill when focused, a faint fill otherwise. */
    static void focusStyle(TextView v, int accent, int idle, float radius) {
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[] {android.R.attr.state_focused}, round(accent, radius));
        bg.addState(new int[0], round(idle, radius));
        v.setBackground(bg);
        v.setTextColor(new ColorStateList(
                new int[][] {{android.R.attr.state_focused}, {}},
                new int[] {ink(accent), 0xD9FFFFFF}));
    }
}
