package tv.dumbtv.home;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.view.FocusFinder;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The Apple TV layout's Control Center: a glass panel that grows out of the clock in the top
 * right corner. Sleep, the Sleep Timer, the screensaver, the TV's Settings and the launcher's
 * Options. ☰ or a click on the clock opens it; Back or ☰ closes it. Focus stays inside while it
 * is open and returns to where it was when it closes.
 */
final class Control {
    private static final int FOCUSED = 0xF2FFFFFF, IDLE = 0x1FFFFFFF;
    private static final int DARK = 0xFF111114;

    private final Activity a;
    private final Config cfg;
    private final FrameLayout root;
    private final View anchor;
    private final Runnable settings, options;
    private Panel panel;
    private TextView timer;
    private View before;

    Control(Activity a, Config cfg, FrameLayout root, View anchor, Runnable settings, Runnable options) {
        this.a = a;
        this.cfg = cfg;
        this.root = root;
        this.anchor = anchor;
        this.settings = settings;
        this.options = options;
    }

    boolean isOpen() {
        return panel != null;
    }

    void toggle() {
        if (isOpen()) close(true);
        else open();
    }

    void open() {
        if (isOpen()) return;
        before = a.getCurrentFocus();
        panel = build();
        FrameLayout.LayoutParams at = new FrameLayout.LayoutParams(dp(392), ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        at.topMargin = anchor.getBottom() + dp(14);
        at.rightMargin = root.getWidth() - anchor.getRight();
        root.addView(panel, at);
        View first = panel.findViewWithTag(FIRST);
        if (first != null) first.requestFocus();
        if (cfg.anim(Config.ANIM_FADES)) {
            // Grows out of the clock: from its corner, slightly small and clear, easing out.
            panel.setAlpha(0f);
            panel.setScaleX(0.88f);
            panel.setScaleY(0.88f);
            panel.post(() -> {
                panel.setPivotX(panel.getWidth());
                panel.setPivotY(0);
                panel.animate().alpha(1f).scaleX(1f).scaleY(1f)
                        .setInterpolator(new DecelerateInterpolator(2f)).setDuration(220).start();
            });
        }
    }

    void close(boolean animate) {
        if (!isOpen()) return;
        Panel p = panel;
        panel = null;
        timer = null;
        if (before != null && before.isAttachedToWindow()) before.requestFocus();
        before = null;
        if (animate && cfg.anim(Config.ANIM_FADES)) {
            p.animate().alpha(0f).scaleX(0.92f).scaleY(0.92f).setDuration(150)
                    .withEndAction(() -> root.removeView(p)).start();
        } else {
            root.removeView(p);
        }
    }

    private static final String FIRST = "first";

    private Panel build() {
        Panel p = new Panel(a);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(14), dp(14), dp(14), dp(14));
        p.setBackground(Ui.glass(dp(30)));

        LinearLayout top = new LinearLayout(a);
        TextView sleep = button("Sleep", android.R.drawable.ic_lock_power_off, true, v -> {
            close(false);
            Sleep.now(a);
        });
        sleep.setTag(FIRST);
        top.addView(sleep, new LinearLayout.LayoutParams(dp(150), dp(150)));

        LinearLayout side = new LinearLayout(a);
        side.setOrientation(LinearLayout.VERTICAL);
        timer = button("", android.R.drawable.ic_lock_idle_alarm, false, v -> {
            Sleep.set(a, cfg, Config.nextSleep(Sleep.minutesLeft(cfg, System.currentTimeMillis())));
            showTimer();
        });
        showTimer();
        side.addView(timer, pill(0, true));
        side.addView(button("Screensaver", android.R.drawable.ic_menu_gallery, false, v -> {
            close(false);
            Sleep.screensaver(a);
        }), pill(dp(10), true));
        LinearLayout.LayoutParams sideAt = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        sideAt.leftMargin = dp(10);
        top.addView(side, sideAt);
        p.addView(top);

        LinearLayout bottom = new LinearLayout(a);
        bottom.addView(button("Settings", android.R.drawable.ic_menu_preferences, false, v -> {
            close(false);
            settings.run();
        }), pill(0, false));
        bottom.addView(button("Options", android.R.drawable.ic_menu_manage, false, v -> {
            close(false);
            options.run();
        }), pill(dp(10), false));
        LinearLayout.LayoutParams bottomAt = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        bottomAt.topMargin = dp(10);
        p.addView(bottom, bottomAt);
        return p;
    }

    private void showTimer() {
        if (timer == null) return;
        int left = Sleep.minutesLeft(cfg, System.currentTimeMillis());
        timer.setText(left > 0 ? "Sleep Timer · " + left + " min" : "Sleep Timer · Off");
    }

    /** A pill in a column ({@code tall}: 70 dp, full width) or half of the bottom row. */
    private LinearLayout.LayoutParams pill(int gap, boolean column) {
        LinearLayout.LayoutParams lp = column
                ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(70))
                : new LinearLayout.LayoutParams(0, dp(58), 1f);
        if (column) lp.topMargin = gap;
        else lp.leftMargin = gap;
        return lp;
    }

    /**
     * A glass button that turns white with dark ink when focused and lifts a little, as in the
     * tvOS Control Center. {@code big}: icon above the label, centered.
     */
    private TextView button(String label, int icon, boolean big, View.OnClickListener click) {
        TextView b = Ui.text(a, big ? 17 : 14, Ui.TEXT, Ui.MEDIUM);
        b.setText(label);
        b.setFocusable(true);
        b.setClickable(true);
        b.setOnClickListener(click);
        ColorStateList ink = new ColorStateList(new int[][] {{android.R.attr.state_focused}, {}},
                new int[] {DARK, Ui.TEXT});
        b.setTextColor(ink);
        Drawable d = a.getDrawable(icon).mutate();
        int size = dp(big ? 44 : 26);
        d.setBounds(0, 0, size, size);
        d.setTintList(ink);
        if (big) {
            b.setCompoundDrawables(null, d, null, null);
            b.setGravity(Gravity.CENTER);
        } else {
            b.setCompoundDrawables(d, null, null, null);
            b.setGravity(Gravity.CENTER_VERTICAL);
        }
        b.setCompoundDrawablePadding(dp(big ? 10 : 10));
        b.setPadding(dp(16), dp(8), dp(12), dp(8));
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[] {android.R.attr.state_focused}, Ui.round(FOCUSED, dp(big ? 26 : 20)));
        bg.addState(new int[0], Ui.round(IDLE, dp(big ? 26 : 20)));
        b.setBackground(bg);
        b.setOnFocusChangeListener((v, has) -> {
            float scale = has ? 1.05f : 1f;
            if (cfg.anim(Config.ANIM_FOCUS)) {
                v.animate().scaleX(scale).scaleY(scale).setInterpolator(new DecelerateInterpolator(2f))
                        .setDuration(160).start();
            } else {
                v.setScaleX(scale);
                v.setScaleY(scale);
            }
        });
        return b;
    }

    private int dp(float v) {
        return Ui.dp(a, v);
    }

    /** Keeps the remote inside the panel while it is open. */
    private static final class Panel extends LinearLayout {
        Panel(Context c) {
            super(c);
            setClipChildren(false);
            setClipToPadding(false);
        }

        @Override
        public View focusSearch(View focused, int direction) {
            View next = FocusFinder.getInstance().findNextFocus(this, focused, direction);
            return next != null ? next : focused;
        }
    }
}
