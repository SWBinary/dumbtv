package tv.dumbtv.home;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * An app icon in the Apple TV look for tiles whose own art does not fit it: a top-to-bottom
 * colour gradient, a white {@link Glyph} in the middle and a soft light on the upper half. The
 * tile clips the corners. Used for Options, Settings, the Appstore and the screensaver.
 */
final class Icon extends Drawable {
    private final int top, bottom;
    private final Glyph glyph;
    private final Paint ground = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gloss = new Paint(Paint.ANTI_ALIAS_FLAG);

    Icon(int top, int bottom, int glyph) {
        this.top = top;
        this.bottom = bottom;
        this.glyph = new Glyph(glyph, 0xFFFFFFFF);
    }

    @Override
    protected void onBoundsChange(Rect b) {
        ground.setShader(new LinearGradient(0, b.top, 0, b.bottom, top, bottom, Shader.TileMode.CLAMP));
        gloss.setShader(new LinearGradient(0, b.top, 0, b.top + b.height() * 0.5f,
                0x33FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        int size = Math.round(b.height() * 0.46f);
        int l = b.centerX() - size / 2, t = b.centerY() - size / 2;
        glyph.setBounds(l, t, l + size, t + size);
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.drawRect(getBounds(), ground);
        canvas.drawRect(getBounds(), gloss);
        glyph.draw(canvas);
    }

    @Override
    public void setAlpha(int alpha) {
        ground.setAlpha(alpha);
        glyph.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter filter) {
        ground.setColorFilter(filter);
        glyph.setColorFilter(filter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }

    @Override
    public ConstantState getConstantState() {
        return new ConstantState() {
            @Override
            public Drawable newDrawable() {
                return new Icon(top, bottom, glyphKind());
            }

            @Override
            public int getChangingConfigurations() {
                return 0;
            }
        };
    }

    private int glyphKind() {
        return glyph.kind();
    }
}
