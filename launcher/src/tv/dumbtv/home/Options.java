package tv.dumbtv.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.StateListDrawable;
import android.media.tv.TvInputManager;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Every setting is a row: left/right or OK cycles it, and the change is saved at once. */
public final class Options extends Activity {
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;

    private final List<Runnable> refreshers = new ArrayList<>();
    private Config cfg;
    /** Apple TV layout: a glass card over the softened wallpaper, rows that light up white. */
    private boolean glass;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        cfg = Config.get(this);
        Ui.use(cfg.theme());
        glass = "tv".equals(cfg.get(Config.LAYOUT));

        ScrollView scroll = new ScrollView(this);
        scroll.setFocusable(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        if (!glass) scroll.setBackgroundColor(Ui.PAGE);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setClipChildren(false);
        list.setClipToPadding(false);
        if (glass) {
            // One card of glass in the middle of the screen; the page scrolls inside the margins.
            list.setPadding(dp(28), dp(28), dp(28), dp(28));
            list.setBackground(Ui.menu(dp(32)));
            FrameLayout.LayoutParams card = new FrameLayout.LayoutParams(dp(680), WRAP, Gravity.CENTER_HORIZONTAL);
            card.topMargin = dp(48);
            card.bottomMargin = dp(48);
            scroll.addView(list, card);
        } else {
            list.setPadding(dp(56), dp(40), dp(56), dp(40));
            scroll.addView(list);
        }

        TextView title = Ui.text(this, glass ? 32 : 30, Ui.TEXT, glass ? Ui.MEDIUM : Ui.LIGHT);
        title.setText("Options");
        title.setPadding(dp(16), 0, 0, dp(20));
        list.addView(title);

        for (Config.Choice c : Config.CHOICES) list.addView(choice(c));
        list.addView(action("Next wallpaper", () -> {
            cfg.nextWallpaper();
            refresh();
        }));
        list.addView(gap());
        list.addView(action("Apps on home screen", () -> pick(Catalog.APPS)));
        list.addView(action("Inputs on home screen", () -> pick(Catalog.INPUTS)));
        list.addView(action("Reset order, names and hidden tiles", this::reset));
        list.addView(action("Open Amazon's home screen", () -> {
            Guard.amazon(this);
            finish();
        }));

        TextView about = Ui.text(this, 13, Ui.FAINT, Ui.MEDIUM);
        about.setLineSpacing(0, 1.4f);
        about.setPadding(dp(16), dp(28), 0, 0);
        about.setText("Settings file: " + cfg.dir + "/" + Config.FILE
                + "\nOne wallpaper: name it exactly wallpaper.jpg (or .png) and put it in that folder"
                + "\nSeveral wallpapers: put them in " + Config.WALLPAPERS + "/ with any names, and pick a rotation"
                + "\nThen pick Background › Image file or Adaptive wallpaper tint. No permission is needed"
                + "\nOn Fire OS 8 adb cannot reach that folder; use /sdcard/Android/media/"
                + getPackageName() + "/ instead"
                + "\nAdaptive wallpaper tint shows graphite until you add a wallpaper"
                + "\nNo internet permission. No tracking, no ads, no accounts.");
        list.addView(about);

        setContentView(glass ? backdrop(scroll) : scroll);
        refresh();
    }

    /**
     * Behind the card: the home screen's wallpaper, shrunk and blurred like frosted glass, or the
     * theme's aurora when there is none; darkened so the card and its text stand out.
     */
    private View backdrop(View page) {
        FrameLayout frame = new FrameLayout(this);
        ImageView back = new ImageView(this);
        back.setScaleType(ImageView.ScaleType.FIT_XY);
        Bitmap soft = null;
        java.io.File wall = "image".equals(cfg.get(Config.BACKGROUND)) ? cfg.wallpaper() : null;
        if (wall != null) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 16;
            Bitmap small = BitmapFactory.decodeFile(wall.getPath(), o);
            if (small != null) soft = Backdrop.soften(small);
        }
        back.setImageBitmap(soft != null ? soft : Backdrop.aurora(Ui.theme.aurora));
        frame.addView(back, new FrameLayout.LayoutParams(MATCH, MATCH));
        View shade = new View(this);
        shade.setBackgroundColor(0x33000000);
        frame.addView(shade, new FrameLayout.LayoutParams(MATCH, MATCH));
        frame.addView(page, new FrameLayout.LayoutParams(MATCH, MATCH));
        return frame;
    }

    @Override
    protected void onResume() {
        super.onResume();
        cfg.refresh();
        refresh();
    }

    @Override
    public void finish() {
        super.finish();
        Home.fade(this, cfg);
    }

    private void refresh() {
        for (Runnable r : refreshers) r.run();
    }

    private View choice(Config.Choice c) {
        LinearLayout row = row();
        TextView name = label(c.title);
        TextView value = Ui.text(this, 16, Ui.MUTED, Ui.MEDIUM);
        value.setGravity(Gravity.END);
        row.addView(name, new LinearLayout.LayoutParams(0, WRAP, 1f));
        row.addView(value, new LinearLayout.LayoutParams(WRAP, WRAP));
        row.setOnClickListener(v -> step(c, 1));
        row.setOnKeyListener((v, code, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (code == KeyEvent.KEYCODE_DPAD_LEFT) return step(c, -1);
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT) return step(c, 1);
            return false;
        });
        Runnable show = () -> {
            boolean focused = row.isFocused();
            String text = c.name(cfg.get(c));
            if (c == Config.ACCENT) {
                SpannableString s = new SpannableString("●  " + text);
                s.setSpan(new ForegroundColorSpan(cfg.accent()), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                value.setText(focused ? "‹  " : "");
                value.append(s);
                if (focused) value.append("  ›");
            } else {
                value.setText(focused ? "‹  " + text + "  ›" : text);
            }
            value.setTextColor(focused ? Ui.TEXT : Ui.MUTED);
            if (glass) lit(row, focused);
            boolean off = (c == Config.DIM || c == Config.ROTATE)
                    && !"image".equals(cfg.get(Config.BACKGROUND))
                    && !"adaptive-tint".equals(cfg.get(Config.BACKGROUND))
                    || c == Config.SWITCH && "off".equals(cfg.get(Config.INPUTS))
                    || c == Config.TILES && "tv".equals(cfg.get(Config.LAYOUT))
                    || (c == Config.ANIM_FOCUS || c == Config.ANIM_FADES || c == Config.ANIM_SHINE
                    || c == Config.ANIM_RETRO)
                    && "off".equals(cfg.get(Config.ANIM));
            row.setAlpha(off ? 0.4f : 1f);
        };
        row.setOnFocusChangeListener((v, has) -> show.run());
        refreshers.add(show);
        refreshers.add(() -> style(row));
        return row;
    }

    private boolean step(Config.Choice c, int by) {
        int i = Arrays.asList(c.values).indexOf(cfg.get(c));
        int n = c.values.length;
        cfg.set(c.key, c.values[((i + by) % n + n) % n]);
        if (c == Config.LAYOUT && glass != "tv".equals(cfg.get(Config.LAYOUT))) {
            recreate();
            return true;
        }
        if (c == Config.THEME) {
            // A new theme brings its own accent and background; both can be changed afterwards.
            // Your own pictures (Background › Image file) stay: they are not part of a theme.
            cfg.set(Config.ACCENT.key, null);
            if (!"image".equals(cfg.get(Config.BACKGROUND))) cfg.set(Config.BACKGROUND.key, null);
            recreate();
            return true;
        }
        refresh();
        if (c == Config.LOCK && "on".equals(cfg.get(c))) {
            Toast.makeText(this, "Options and Settings are hidden on the home screen now. "
                    + "Hold \u2630 Menu for " + Home.UNLOCK_MS / 1000 + " seconds to come back here.",
                    Toast.LENGTH_LONG).show();
        }
        return true;
    }

    private View action(String text, Runnable run) {
        LinearLayout row = row();
        row.addView(label(text), new LinearLayout.LayoutParams(0, WRAP, 1f));
        TextView chevron = Ui.text(this, 16, Ui.MUTED, Ui.MEDIUM);
        chevron.setText("›");
        row.addView(chevron);
        row.setOnClickListener(v -> run.run());
        if (glass) row.setOnFocusChangeListener((v, has) -> lit(row, has));
        refreshers.add(() -> style(row));
        return row;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setClickable(true);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(glass ? MATCH : dp(600), WRAP);
        lp.bottomMargin = dp(glass ? 6 : 4);
        row.setLayoutParams(lp);
        return row;
    }

    private void style(View row) {
        if (glass) {
            // tvOS settings: the focused row turns white, the others are faint glass pills.
            StateListDrawable pill = new StateListDrawable();
            pill.addState(new int[] {android.R.attr.state_focused}, Ui.round(0xF2FFFFFF, dp(18)));
            pill.addState(new int[0], Ui.round(0x14FFFFFF, dp(18)));
            row.setBackground(pill);
            return;
        }
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[] {android.R.attr.state_focused},
                Ui.round((cfg.accent() & 0x00FFFFFF) | 0x33000000, dp(10)));
        bg.addState(new int[0], Ui.round(0x00000000, dp(10)));
        row.setBackground(bg);
    }

    /** A focused glass row: dark text on white, lifted a little (gliding with Focus zoom). */
    private void lit(LinearLayout row, boolean on) {
        for (int i = 0; i < row.getChildCount(); i++) {
            View v = row.getChildAt(i);
            if (!(v instanceof TextView)) continue;
            boolean name = i == 0;
            ((TextView) v).setTextColor(on ? (name ? 0xFF111114 : 0xFF3A3A40) : (name ? Ui.TEXT : Ui.MUTED));
        }
        float scale = on ? 1.03f : 1f;
        row.animate().cancel();
        if (cfg.anim(Config.ANIM_FOCUS)) {
            row.animate().scaleX(scale).scaleY(scale).setInterpolator(new DecelerateInterpolator(2f))
                    .setDuration(180).start();
        } else {
            row.setScaleX(scale);
            row.setScaleY(scale);
        }
    }

    private TextView label(String text) {
        TextView v = Ui.text(this, 16, Ui.TEXT, Ui.MEDIUM);
        v.setText(text);
        return v;
    }

    private View gap() {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(18)));
        return v;
    }

    private void pick(String kind) {
        List<Item> items = Catalog.APPS.equals(kind)
                ? Catalog.apps(this)
                : Catalog.inputs(this, (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE));
        CharSequence[] names = new CharSequence[items.size()];
        boolean[] on = new boolean[items.size()];
        for (int i = 0; i < items.size(); i++) {
            names[i] = Catalog.label(cfg, items.get(i));
            on[i] = Catalog.shown(cfg, kind, items.get(i));
        }
        new AlertDialog.Builder(this, Ui.DIALOG)
                .setTitle(Catalog.APPS.equals(kind) ? "Apps on home screen" : "Inputs on home screen")
                .setMultiChoiceItems(names, on, (d, which, checked) ->
                        Catalog.setShown(cfg, kind, items.get(which), checked))
                .setPositiveButton("Done", null)
                .show();
    }

    private void reset() {
        new AlertDialog.Builder(this, Ui.DIALOG)
                .setTitle("Reset the home screen layout?")
                .setMessage("Order, names and hidden tiles go back to default. Colors and sizes stay.")
                .setPositiveButton("Reset", (d, w) -> cfg.removePrefix("name.", "apps.", "inputs."))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }
}
