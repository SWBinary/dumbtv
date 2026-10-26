package tv.dumbtv.home;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.view.FocusFinder;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The Apple TV layout's Control Center. At rest it is a small glass capsule with the Options
 * symbol next to the clock; ☰ turns that capsule into the panel (its shape grows, corners and
 * all, then the buttons fade in) and back. Sleep, Sleep Timer, Screensaver, Settings, Options.
 * The Sleep Timer opens a small menu of its own over the panel. Focus stays inside while open.
 */
final class Control {
    private static final int FOCUSED = 0xF2FFFFFF, IDLE = 0x24FFFFFF;
    private static final int DARK = 0xFF111114;
    private static final Interpolator EASE = new PathInterpolator(0.25f, 0.1f, 0f, 1f);
    private static final String FIRST = "first";
    /** Portrait: narrower than tall, like the tvOS Control Center. */
    private static final int WIDTH = 216;

    private final Activity a;
    private final Config cfg;
    private final FrameLayout root;
    private final View corner;
    private final Runnable settings, options;
    /** Blur the screen around the panel while it is open, and clear it again. */
    private final Runnable veilOn, veilOff;
    private final View capsule;
    private Panel panel, menu;
    private TextView timer;
    private View before;
    private ValueAnimator morph;

    Control(Activity a, Config cfg, FrameLayout root, View corner, Runnable settings, Runnable options,
            Runnable veilOn, Runnable veilOff) {
        this.a = a;
        this.veilOn = veilOn;
        this.veilOff = veilOff;
        this.cfg = cfg;
        this.root = root;
        this.corner = corner;
        this.settings = settings;
        this.options = options;
        capsule = buildCapsule();
    }

    /** The capsule that stands for the Control Center; the caller puts it next to the clock. */
    View capsule() {
        return capsule;
    }

    boolean isOpen() {
        return panel != null;
    }

    void toggle() {
        if (isOpen()) close(true);
        else open();
    }

    /** Back: the timer menu first, then the panel. False when nothing was open. */
    boolean back() {
        if (menu != null) {
            closeMenu();
            return true;
        }
        if (isOpen()) {
            close(true);
            return true;
        }
        return false;
    }

    void open() {
        if (isOpen()) return;
        before = a.getCurrentFocus();
        veilOn.run();
        panel = build();
        FrameLayout.LayoutParams at = new FrameLayout.LayoutParams(dp(WIDTH), ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        at.topMargin = corner.getBottom() + dp(12);
        at.rightMargin = root.getWidth() - corner.getRight();
        root.addView(panel, at);
        View first = panel.findViewWithTag(FIRST);
        if (first != null) first.requestFocus();
        if (cfg.anim(Config.ANIM_FADES)) {
            // Transparent until the morph's first frame; INVISIBLE would take the focus away.
            Panel p = panel;
            p.setAlpha(0f);
            p.post(() -> {
                if (panel == p) grow(p, true, null);
            });
        } else {
            capsule.setAlpha(0f);
        }
    }

    void close(boolean animate) {
        if (!isOpen()) return;
        if (menu != null) {
            root.removeView(menu);
            menu = null;
        }
        Panel p = panel;
        panel = null;
        timer = null;
        veilOff.run();
        if (before != null && before.isAttachedToWindow()) before.requestFocus();
        before = null;
        if (animate && cfg.anim(Config.ANIM_FADES)) {
            grow(p, false, () -> root.removeView(p));
        } else {
            if (morph != null) morph.cancel();
            root.removeView(p);
            capsule.setAlpha(1f);
        }
    }

    /**
     * The morph: the panel's outline runs from the capsule's place and shape to its own (or
     * back), clipping the glass with it; the buttons fade in over the second half.
     */
    private void grow(Panel p, boolean opening, Runnable done) {
        if (morph != null) morph.cancel();
        int[] at = new int[2], from = new int[2];
        p.getLocationOnScreen(at);
        capsule.getLocationOnScreen(from);
        float sx = from[0] - at[0], sy = from[1] - at[1];
        float sw = capsule.getWidth(), sh = capsule.getHeight();
        float pw = p.getWidth(), ph = p.getHeight(), pr = dp(24);
        float[] shape = new float[5];
        // The morph's corner ends where the glass's own does.
        p.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(Math.round(shape[0]), Math.round(shape[1]), Math.round(shape[2]),
                        Math.round(shape[3]), shape[4]);
            }
        });
        p.setClipToOutline(true);
        ValueAnimator m = ValueAnimator.ofFloat(opening ? 0f : 1f, opening ? 1f : 0f);
        morph = m;
        m.setDuration(opening ? 340 : 260);
        m.setInterpolator(EASE);
        m.addUpdateListener(an -> {
            float f = (Float) an.getAnimatedValue();
            shape[0] = sx * (1 - f);
            shape[1] = sy * (1 - f);
            shape[2] = (sx + sw) * (1 - f) + pw * f;
            shape[3] = (sy + sh) * (1 - f) + ph * f;
            shape[4] = sh / 2 * (1 - f) + pr * f;
            p.invalidateOutline();
            float inside = Math.max(0f, (f - 0.4f) / 0.6f);
            for (int i = 0; i < p.getChildCount(); i++) p.getChildAt(i).setAlpha(inside);
            capsule.setAlpha(Math.max(0f, 1f - f * 3f));
            if (p.getAlpha() < 1f) p.setAlpha(1f);
        });
        m.addListener(new android.animation.AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(android.animation.Animator an) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(android.animation.Animator an) {
                if (opening && !cancelled) p.setClipToOutline(false);
                if (done != null) done.run();
            }
        });
        m.start();
    }

    private View buildCapsule() {
        TextView c = new TextView(a);
        Drawable d = new Glyph(Glyph.SLIDERS, 0xFFFFFFFF);
        int size = dp(22);
        d.setBounds(0, 0, size, size);
        c.setCompoundDrawables(d, null, null, null);
        c.setBackground(Ui.glass(dp(22)));
        c.setPadding(dp(16), dp(11), dp(16), dp(11));
        return c;
    }

    private Panel build() {
        Panel p = new Panel(a);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(10), dp(10), dp(10), dp(10));
        // Glass over the blurred screen behind it (the veil), so only the blur shows through.
        p.setBackground(Ui.glass(dp(24)));

        TextView sleep = button("Sleep", Glyph.POWER, true, v -> {
            close(false);
            Sleep.now(a);
        });
        sleep.setTag(FIRST);
        p.addView(sleep, row(dp(78), 0));

        timer = button("", Glyph.TIMER, false, v -> openMenu());
        showTimer();
        p.addView(timer, row(dp(42), dp(7)));
        p.addView(button("Screensaver", Glyph.PICTURE, false, v -> {
            close(false);
            Sleep.screensaver(a);
        }), row(dp(42), dp(7)));

        LinearLayout bottom = new LinearLayout(a);
        bottom.setClipChildren(false);
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, dp(70), 1f);
        bottom.addView(button("Settings", Glyph.GEAR, true, v -> {
            close(false);
            settings.run();
        }), half);
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, dp(70), 1f);
        right.leftMargin = dp(7);
        bottom.addView(button("Options", Glyph.SLIDERS, true, v -> {
            close(false);
            options.run();
        }), right);
        p.addView(bottom, row(ViewGroup.LayoutParams.WRAP_CONTENT, dp(7)));
        return p;
    }

    private static LinearLayout.LayoutParams row(int height, int gap) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
        lp.topMargin = gap;
        return lp;
    }

    /** The Sleep Timer's own small menu, laid over the panel at the timer button. */
    private void openMenu() {
        if (menu != null || panel == null) return;
        int left = Sleep.minutesLeft(cfg, System.currentTimeMillis());
        // The step the running timer was set to: the first one that covers what is left.
        int current = 0;
        if (left > 0) {
            for (int step : Config.SLEEP_STEPS) {
                if (step >= left) {
                    current = step;
                    break;
                }
            }
        }
        menu = new Panel(a);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(dp(8), dp(8), dp(8), dp(8));
        menu.setBackground(Ui.frosted(dp(20)));
        TextView title = Ui.text(a, 12, Ui.MUTED, Ui.MEDIUM);
        title.setText(left > 0 ? "SLEEP TIMER · " + left + " MIN LEFT" : "SLEEP TIMER");
        title.setLetterSpacing(0.1f);
        title.setPadding(dp(12), dp(4), 0, dp(4));
        menu.addView(title);
        View focus = null;
        for (int step : Config.SLEEP_STEPS) {
            String name = step == 0 ? "Off" : step + " min";
            TextView row = button((step == current ? "✓   " : "     ") + name, -1, false, v -> {
                Sleep.set(a, cfg, step);
                showTimer();
                closeMenu();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30));
            lp.topMargin = dp(3);
            menu.addView(row, lp);
            if (step == current) focus = row;
        }
        // Over the panel's right half, from its top: smaller than the panel and inside it.
        FrameLayout.LayoutParams at = new FrameLayout.LayoutParams(dp(WIDTH - 20), ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        FrameLayout.LayoutParams under = (FrameLayout.LayoutParams) panel.getLayoutParams();
        at.topMargin = under.topMargin + dp(10);
        at.rightMargin = under.rightMargin + dp(10);
        root.addView(menu, at);
        if (focus != null) focus.requestFocus();
        if (cfg.anim(Config.ANIM_FADES)) {
            Panel m = menu;
            m.setAlpha(0f);
            m.setScaleX(0.9f);
            m.setScaleY(0.9f);
            m.post(() -> {
                m.setPivotX(m.getWidth());
                m.setPivotY(0);
                m.animate().alpha(1f).scaleX(1f).scaleY(1f).setInterpolator(EASE).setDuration(220).start();
            });
        }
    }

    private void closeMenu() {
        Panel m = menu;
        menu = null;
        if (timer != null) timer.requestFocus();
        if (m == null) return;
        if (cfg.anim(Config.ANIM_FADES)) {
            m.animate().alpha(0f).scaleX(0.94f).scaleY(0.94f).setDuration(150)
                    .withEndAction(() -> root.removeView(m)).start();
        } else {
            root.removeView(m);
        }
    }

    private void showTimer() {
        if (timer == null) return;
        int left = Sleep.minutesLeft(cfg, System.currentTimeMillis());
        timer.setText(left > 0 ? "Timer · " + left + " min" : "Sleep Timer");
        centre(timer);
    }

    /**
     * A glass button that turns white with dark ink when focused and lifts a little, as in the
     * tvOS Control Center. {@code big}: icon above the label. {@code icon} -1: text only.
     */
    private TextView button(String label, int icon, boolean big, View.OnClickListener click) {
        TextView b = Ui.text(a, big ? 13 : 12, Ui.TEXT, Ui.MEDIUM);
        b.setText(label);
        b.setFocusable(true);
        b.setClickable(true);
        b.setOnClickListener(click);
        ColorStateList ink = new ColorStateList(new int[][] {{android.R.attr.state_focused}, {}},
                new int[] {DARK, Ui.TEXT});
        b.setTextColor(ink);
        if (icon >= 0) {
            Drawable d = new Glyph(icon, ink);
            int size = dp(big ? 24 : 17);
            d.setBounds(0, 0, size, size);
            if (big) b.setCompoundDrawables(null, d, null, null);
            else b.setCompoundDrawables(d, null, null, null);
            b.setCompoundDrawablePadding(dp(big ? 6 : 8));
        }
        // Icon and label centred in every button, as one group.
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(10), dp(4), dp(10), dp(4));
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[] {android.R.attr.state_focused}, Ui.round(FOCUSED, dp(big ? 18 : 16)));
        bg.addState(new int[0], Ui.round(IDLE, dp(big ? 18 : 16)));
        b.setBackground(bg);
        if (!big) b.setSingleLine(true);
        if (icon >= 0) {
            // Android keeps a compound icon at the edge (left, or top for the big buttons); pad
            // both sides so icon and label sit in the middle together.
            b.addOnLayoutChangeListener((v, l, t, r, bt, ol, ot, or, ob) -> centre(b));
        }
        b.setOnFocusChangeListener((v, has) -> {
            float scale = has ? 1.05f : 1f;
            if (cfg.anim(Config.ANIM_FOCUS)) {
                v.animate().scaleX(scale).scaleY(scale).setInterpolator(EASE).setDuration(160).start();
            } else {
                v.setScaleX(scale);
                v.setScaleY(scale);
            }
        });
        return b;
    }

    private void centre(TextView b) {
        Drawable above = b.getCompoundDrawables()[1];
        if (above != null && b.getHeight() > 0) {
            float group = above.getBounds().height() + b.getCompoundDrawablePadding() + b.getLineHeight();
            int top = Math.max(dp(4), (int) Math.floor((b.getHeight() - group) / 2f));
            if (b.getPaddingTop() != top) b.setPadding(b.getPaddingLeft(), top, b.getPaddingRight(), top);
            return;
        }
        Drawable d = b.getCompoundDrawables()[0];
        if (d == null || b.getWidth() == 0) return;
        float group = d.getBounds().width() + b.getCompoundDrawablePadding()
                + b.getPaint().measureText(b.getText().toString());
        // A few pixels short of exact, so rounding never pushes the label onto two lines.
        int side = Math.max(dp(10), (int) Math.floor((b.getWidth() - group) / 2f) - dp(2));
        if (b.getPaddingLeft() != side) b.setPadding(side, b.getPaddingTop(), side, b.getPaddingBottom());
    }

    private int dp(float v) {
        return Ui.dp(a, v);
    }

    /** Keeps the remote inside the panel (or the timer menu) while it is open. */
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
