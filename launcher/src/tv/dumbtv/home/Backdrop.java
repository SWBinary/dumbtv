package tv.dumbtv.home;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

/**
 * The full-screen backdrop of the Apple TV layout: an app's banner shrunk to 48 x 27 pixels,
 * box-blurred and washed in the app's own colour. An ImageView stretches it to the screen and the
 * GPU's bilinear filter smooths the rest, so the blur costs a few thousand pixel reads instead of
 * a full-screen pass.
 */
final class Backdrop {
    static final int W = 48, H = 27;
    private static final int RADIUS = 3, PASSES = 2;
    /** Brightest channel allowed, so white text and the clock read on any app's art. */
    static final int CEILING = 120;

    private Backdrop() {}

    /** {@code art} drawn over {@code ground} and blurred; {@code art}'s bounds are restored. */
    static Bitmap of(Drawable art, int ground) {
        Bitmap b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        b.eraseColor(ground);
        Canvas c = new Canvas(b);
        android.graphics.Rect was = art.copyBounds();
        art.setBounds(0, 0, W, H);
        art.draw(c);
        art.setBounds(was);
        int[] px = new int[W * H];
        b.getPixels(px, 0, W, 0, 0, W, H);
        // A white banner with a red logo should give a red backdrop, not a grey one.
        int color = FocusColor.representative(px);
        blur(px, W, H, RADIUS, PASSES);
        tone(px, color);
        b.setPixels(px, 0, W, 0, 0, W, H);
        return b;
    }

    /** {@code src} shrunk to the backdrop size and blurred: frosted glass of a wallpaper. */
    static Bitmap soften(Bitmap src) {
        Bitmap b = Bitmap.createScaledBitmap(src, W, H, true).copy(Bitmap.Config.ARGB_8888, true);
        int[] px = new int[W * H];
        b.getPixels(px, 0, W, 0, 0, W, H);
        blur(px, W, H, RADIUS, PASSES);
        b.setPixels(px, 0, W, 0, 0, W, H);
        return b;
    }

    /** A soft aurora of {@code colors} over the whole screen, for the Apple TV layout. */
    static Bitmap aurora(int[] colors) {
        int[] px = new int[W * H];
        aurora(px, W, H, colors);
        blur(px, W, H, RADIUS, PASSES);
        Bitmap b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        b.setPixels(px, 0, W, 0, 0, W, H);
        return b;
    }

    /**
     * Each colour is a round glow at a fixed spot (top left, top right, bottom left, bottom
     * right, then the middle), fading out over about half the width; the glows add up over black.
     */
    static void aurora(int[] px, int w, int h, int[] colors) {
        float[][] at = {{0.15f, 0.2f}, {0.85f, 0.15f}, {0.25f, 0.95f}, {0.8f, 0.85f}, {0.5f, 0.5f}};
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float r = 0, g = 0, b = 0;
                for (int i = 0; i < colors.length && i < at.length; i++) {
                    float dx = x / (float) w - at[i][0], dy = (y / (float) h - at[i][1]) * h / w;
                    float k = Math.max(0f, 1f - (dx * dx + dy * dy) / 0.22f);
                    k *= k;
                    r += (colors[i] >> 16 & 255) * k;
                    g += (colors[i] >> 8 & 255) * k;
                    b += (colors[i] & 255) * k;
                }
                px[y * w + x] = 0xFF000000 | Math.min(255, (int) r) << 16
                        | Math.min(255, (int) g) << 8 | Math.min(255, (int) b);
            }
        }
    }

    /** 40% art, 60% {@code color}, then dimmed until no channel passes {@link #CEILING}. */
    static void tone(int[] px, int color) {
        int cr = color >> 16 & 255, cg = color >> 8 & 255, cb = color & 255;
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            int r = ((c >> 16 & 255) * 4 + cr * 6) / 10;
            int g = ((c >> 8 & 255) * 4 + cg * 6) / 10;
            int b = ((c & 255) * 4 + cb * 6) / 10;
            int max = Math.max(r, Math.max(g, b));
            if (max > CEILING) {
                r = r * CEILING / max;
                g = g * CEILING / max;
                b = b * CEILING / max;
            }
            px[i] = 0xFF000000 | r << 16 | g << 8 | b;
        }
    }

    /** Separable box blur, {@code passes} times; edges repeat the border pixel. Opaque result. */
    static void blur(int[] px, int w, int h, int radius, int passes) {
        int[] tmp = new int[px.length];
        for (int p = 0; p < passes; p++) {
            box(px, tmp, w, h, radius, true);
            box(tmp, px, w, h, radius, false);
        }
    }

    private static void box(int[] in, int[] out, int w, int h, int r, boolean horizontal) {
        int lines = horizontal ? h : w, len = horizontal ? w : h;
        int div = 2 * r + 1;
        for (int line = 0; line < lines; line++) {
            for (int i = 0; i < len; i++) {
                int rs = 0, gs = 0, bs = 0;
                for (int k = -r; k <= r; k++) {
                    int j = Math.max(0, Math.min(len - 1, i + k));
                    int c = in[horizontal ? line * w + j : j * w + line];
                    rs += c >> 16 & 255;
                    gs += c >> 8 & 255;
                    bs += c & 255;
                }
                out[horizontal ? line * w + i : i * w + line] =
                        0xFF000000 | rs / div << 16 | gs / div << 8 | bs / div;
            }
        }
    }
}
