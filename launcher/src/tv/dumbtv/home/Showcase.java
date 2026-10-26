package tv.dumbtv.home;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The screensaver: the wallpapers full screen, one a minute, each drifting in slowly (Ken Burns)
 * and cross-fading into the next, with the clock in glass type. Shown by {@link Dream} when the
 * TV idles, and as this activity from Control Center and the Screensaver tile. Any key ends it.
 */
public final class Showcase extends Activity {
    static boolean start(Context c) {
        try {
            c.startActivity(new Intent(c, Showcase.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(new Stage(this));
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_UP) {
            finish();
            overridePendingTransition(0, R.anim.fade_out);
        }
        return true;
    }

    /** What both the activity and the dream show. */
    static final class Stage extends FrameLayout {
        private static final long EACH_MS = 60_000, FADE_MS = 1_800;

        private final Config cfg;
        private final Handler ui = new Handler(Looper.getMainLooper());
        private final ExecutorService io = Executors.newSingleThreadExecutor();
        private final ImageView[] layers = new ImageView[2];
        private final TextView clock, credit;
        private final Runnable next = this::next;
        private final Runnable tick = this::tick;
        private List<File> pool;
        private int index = -1, front;

        Stage(Context c) {
            super(c);
            cfg = Config.get(c);
            Ui.use(cfg.theme());
            setBackgroundColor(0xFF000000);
            for (int i = 0; i < 2; i++) {
                layers[i] = new ImageView(c);
                layers[i].setScaleType(ImageView.ScaleType.CENTER_CROP);
                layers[i].setAlpha(0f);
                addView(layers[i], new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            }
            clock = Ui.text(c, 64, Ui.TEXT, Ui.rounded(c));
            Ui.glassType(clock);
            LayoutParams at = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                    Gravity.TOP | Gravity.END);
            at.topMargin = Ui.dp(c, 40);
            at.rightMargin = Ui.dp(c, 64);
            addView(clock, at);
            credit = Ui.text(c, 13, 0xCCFFFFFF, Ui.MEDIUM);
            credit.setShadowLayer(Ui.dp(c, 6), 0, Ui.dp(c, 1), 0x99000000);
            LayoutParams low = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.START);
            low.leftMargin = Ui.dp(c, 64);
            low.bottomMargin = Ui.dp(c, 40);
            addView(credit, low);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            pool = cfg.showcase();
            tick();
            next();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            ui.removeCallbacksAndMessages(null);
            io.shutdownNow();
        }

        private void tick() {
            clock.setText(DateFormat.format(DateFormat.is24HourFormat(getContext()) ? "HH:mm" : "h:mm", new Date()));
            ui.postDelayed(tick, 10_000);
        }

        private void next() {
            ui.postDelayed(next, EACH_MS);
            if (pool.isEmpty()) {
                if (index < 0) show(Backdrop.aurora(Ui.theme.aurora));
                index = 0;
                return;
            }
            index = (index + 1) % pool.size();
            File f = pool.get(index);
            int w = getResources().getDisplayMetrics().widthPixels;
            int h = getResources().getDisplayMetrics().heightPixels;
            io.execute(() -> {
                Bitmap b = decode(f, w, h);
                String who = Fetch.credit(f);
                if (b != null) ui.post(() -> {
                    show(b);
                    credit.setText(who == null ? "" : "Photo: " + who + " · Unsplash");
                });
            });
        }

        /** Cross-fades to {@code b} and lets it drift: a slow zoom and a little sideways. */
        private void show(Bitmap b) {
            ImageView old = layers[front];
            front ^= 1;
            ImageView v = layers[front];
            bringChildToFront(v);
            bringChildToFront(clock);
            bringChildToFront(credit);
            v.animate().cancel();
            v.setImageBitmap(b);
            v.setAlpha(0f);
            v.animate().alpha(1f).setDuration(FADE_MS).withEndAction(() -> old.setImageDrawable(null)).start();
            // The zoom keeps more picture beyond the edges than the drift uses: no bare border.
            float drift = (index % 2 == 0 ? 1 : -1) * Ui.dp(getContext(), 28);
            ObjectAnimator burns = ObjectAnimator.ofPropertyValuesHolder(v,
                    PropertyValuesHolder.ofFloat("scaleX", 1.10f, 1.18f),
                    PropertyValuesHolder.ofFloat("scaleY", 1.10f, 1.18f),
                    PropertyValuesHolder.ofFloat("translationX", -drift, drift));
            burns.setInterpolator(new LinearInterpolator());
            burns.setDuration(EACH_MS + FADE_MS);
            burns.start();
        }

        private static Bitmap decode(File f, int w, int h) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            o.inSampleSize = 1;
            while (o.outWidth / (o.inSampleSize * 2) >= w && o.outHeight / (o.inSampleSize * 2) >= h) {
                o.inSampleSize *= 2;
            }
            o.inJustDecodeBounds = false;
            return BitmapFactory.decodeFile(f.getPath(), o);
        }
    }
}
