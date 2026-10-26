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
    static Typeface LIGHT, MEDIUM, CLOCK, LABEL;

    static {
        colors(Theme.ORIGINAL);
    }

    private Ui() {}

    /** Called by each screen before it builds its views. */
    static void use(Theme t) {
        colors(t);
        LIGHT = t.light();
        MEDIUM = t.medium();
        CLOCK = t.clock();
        LABEL = t.label();
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

    /**
     * Liquid-glass surface: a light top-to-bottom sheen with a hairline edge. Android 9 cannot
     * blur what lies behind a view, so the glass shows the (already soft) backdrop through it.
     */
    static GradientDrawable glass(float radius) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0x3DFFFFFF, 0x1FFFFFFF});
        g.setCornerRadius(radius);
        g.setStroke(2, 0x40FFFFFF);
        return g;
    }

    private static Typeface rounded;

    /**
     * The Apple TV clock face: a rounded bold font when one ships in assets/fonts/clock.ttf,
     * else Android's medium sans in bold.
     */
    static Typeface rounded(Context c) {
        if (rounded == null) {
            try {
                rounded = Typeface.createFromAsset(c.getAssets(), "fonts/clock.ttf");
            } catch (RuntimeException e) {
                rounded = Typeface.create("sans-serif-medium", Typeface.BOLD);
            }
        }
        return rounded;
    }

    /**
     * Glass type: bright at the top, see-through towards the bottom, on a soft shadow. The
     * see-through is in the paint itself, never a view alpha: an alpha on the view would draw it
     * through a layer cut to its bounds, clipping the round digits and boxing in the shadow.
     */
    static void glassType(TextView v) {
        float d = v.getResources().getDisplayMetrics().density;
        v.setPadding(v.getPaddingLeft(), Math.round(8 * d), v.getPaddingRight(), Math.round(8 * d));
        glassStrength(v, 1f);
        v.addOnLayoutChangeListener((view, l, t, r, b, ol, ot, or, ob) -> {
            if (b - t != ob - ot) glassStrength(v, glassOf(v));
        });
    }

    /** How solid the glass type is: 1 as drawn, lower for more of the picture through it. */
    private static final java.util.Map<TextView, Float> GLASS = new java.util.WeakHashMap<>();

    static float glassOf(TextView v) {
        Float s = GLASS.get(v);
        return s == null ? 1f : s;
    }

    static void glassStrength(TextView v, float s) {
        GLASS.put(v, s);
        float d = v.getResources().getDisplayMetrics().density;
        v.setShadowLayer(10 * d, 0, 2 * d, Math.round(0x59 * s) << 24);
        int h = Math.max(1, v.getHeight());
        v.getPaint().setShader(new android.graphics.LinearGradient(0, 0, 0, h,
                Math.round(0xFA * s) << 24 | 0xFFFFFF, Math.round(0xA6 * s) << 24 | 0xFFFFFF,
                android.graphics.Shader.TileMode.CLAMP));
        v.invalidate();
    }

    /**
     * Menu glass: 85% solid at the top, 80% at the bottom, with a light hairline edge, so the
     * buttons read over a home screen that stays clearly visible behind it. For the Control
     * Center, its timer menu and the Options card.
     */
    static GradientDrawable menu(float radius) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0xD93A3D43, 0xCC26282D});
        g.setCornerRadius(radius);
        g.setStroke(2, 0x40FFFFFF);
        return g;
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
