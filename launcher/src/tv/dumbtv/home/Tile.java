package tv.dumbtv.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.media.tv.TvInputManager;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewOutlineProvider;

/** A card drawn in one pass: banner, or icon plus name, or an input name; focus ring on top. */
final class Tile extends View {
    private static final float FOCUS_SCALE = 1.08f;
    /** Apple TV layout: a focused tile grows and rises on a shadow instead of wearing a ring. */
    private static final float LIFT_SCALE = 1.12f;
    /** tvOS-like ease-out: quick to start, long soft landing. */
    private static final android.view.animation.Interpolator EASE =
            new android.view.animation.PathInterpolator(0.25f, 0.1f, 0f, 1f);

    final Item item;
    final String kind;
    private final CharSequence title;
    private final int cardW, cardH, labelH;
    private final boolean labelAlways, labelOnFocus;
    private final float radius, stroke;
    private final RectF card = new RectF();
    private final Path clip = new Path();
    private final Path arrow = new Path();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Theme outline while unfocused, and the soft halo of a GLOW focus. */
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final boolean glow;
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint inner = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint under = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final boolean zoom, lift, shine;
    /** The light sweeping over a focused Apple TV icon: its position, and what moves it. */
    private float sweepAt = Float.NaN;
    private android.animation.ValueAnimator sweep;
    private final Paint gloss = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.Matrix sweepMatrix = new android.graphics.Matrix();
    private boolean moving;

    Tile(Context c, Item item, String kind, CharSequence title, int cardW, int cardH,
            String labels, int accent, boolean zoom, boolean lift, boolean shine) {
        super(c);
        this.zoom = zoom;
        this.lift = lift;
        this.shine = shine && lift;
        this.item = item;
        this.kind = kind;
        this.title = title;
        this.cardW = cardW;
        this.cardH = cardH;
        boolean app = Catalog.isApp(kind);
        labelH = app ? Ui.dp(c, 26) : 0;
        labelAlways = "always".equals(labels);
        labelOnFocus = "focus".equals(labels);
        radius = lift ? iconRadius(cardW) : Ui.dp(c, Ui.theme.radius);
        stroke = Ui.dp(c, 2.5f);

        fill.setColor(app ? Ui.SURFACE : Ui.INPUT_SURFACE);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(stroke);
        ring.setColor(accent);
        glow = Ui.theme.focus == Theme.GLOW;
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(Ui.dp(c, 1));
        edge.setColor(Ui.theme.edge);
        halo.setStyle(Paint.Style.STROKE);
        halo.setColor(accent);
        dot.setColor(0xFF34C759);
        inner.setColor(Ui.TEXT);
        inner.setTypeface(Ui.MEDIUM);
        inner.setTextAlign(Paint.Align.CENTER);
        inner.setTextSize(Ui.dp(c, app ? 14 : 17));
        under.setColor(Ui.TEXT);
        under.setTypeface(Ui.MEDIUM);
        under.setTextAlign(Paint.Align.CENTER);
        under.setTextSize(Ui.dp(c, 13));

        if (lift) {
            // The shadow follows the card only, not the name drawn under it.
            setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View v, Outline o) {
                    o.setRoundRect(0, 0, Tile.this.cardW, Tile.this.cardH, radius);
                }
            });
        }
        setFocusable(true);
        setClickable(true);
        setLongClickable(true);
        setContentDescription(title);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (sweep != null) sweep.cancel();
    }

    void setMoving(boolean on) {
        moving = on;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(cardW, cardH + labelH);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        card.set(0, 0, cardW, cardH);
        clip.reset();
        clip.addRoundRect(card, radius, radius, Path.Direction.CW);
        setPivotX(cardW / 2f);
        setPivotY(cardH / 2f);
        if (lift) {
            // A diagonal band of light for the sweep, and a soft glow on the top of the glass.
            gloss.setShader(new android.graphics.LinearGradient(0, 0, cardW * 0.4f, cardH,
                    new int[] {0x00FFFFFF, 0x4DFFFFFF, 0x00FFFFFF}, null,
                    android.graphics.Shader.TileMode.CLAMP));
            sheen.setShader(new android.graphics.LinearGradient(0, 0, 0, cardH * 0.55f,
                    0x26FFFFFF, 0x00FFFFFF, android.graphics.Shader.TileMode.CLAMP));
        }
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previous) {
        super.onFocusChanged(focused, direction, previous);
        animate().cancel();
        // Lift always grows (it is how the focus shows); Focus zoom only decides whether it glides.
        float scale = focused && lift ? LIFT_SCALE : focused && zoom ? FOCUS_SCALE : 1f;
        float rise = focused && lift ? Ui.dp(getContext(), 18) : 0;
        if (zoom) {
            // Eases out like tvOS: fast start, soft landing; losing focus glides back the same way.
            // Alpha and position finish too, in case the dock's entrance was still running.
            animate().scaleX(scale).scaleY(scale).translationZ(rise).alpha(1f).translationY(0f)
                    .setStartDelay(0).setInterpolator(EASE).setDuration(focused ? 240 : 180).start();
        } else {
            setScaleX(scale);
            setScaleY(scale);
            setTranslationZ(rise);
            setAlpha(1f);
            setTranslationY(0f);
        }
        if (sweep != null) sweep.cancel();
        sweepAt = Float.NaN;
        if (focused && shine) {
            sweep = android.animation.ValueAnimator.ofFloat(-0.5f, 1.3f);
            sweep.setStartDelay(160);
            sweep.setDuration(800);
            sweep.setInterpolator(EASE);
            sweep.addUpdateListener(a -> {
                sweepAt = (Float) a.getAnimatedValue();
                invalidate();
            });
            sweep.start();
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Drawable banner = item.banner, icon = item.icon;
        canvas.save();
        canvas.clipPath(clip);
        canvas.drawRect(card, fill);
        if (banner != null) {
            banner.setBounds(0, 0, cardW, cardH);
            banner.draw(canvas);
        } else if (icon != null) {
            int size = cardH * 46 / 100;
            int left = (cardW - size) / 2, top = cardH * 12 / 100;
            icon.setBounds(left, top, left + size, top + size);
            icon.draw(canvas);
            drawLine(canvas, inner, cardW / 2f, cardH - cardH * 13 / 100f);
        } else {
            drawLine(canvas, inner, cardW / 2f, cardH / 2f - (inner.ascent() + inner.descent()) / 2);
        }
        if (lift && isFocused() && !moving) {
            canvas.drawRect(card, sheen);
            if (!Float.isNaN(sweepAt)) {
                sweepMatrix.setTranslate(sweepAt * cardW * 1.2f - cardW * 0.2f, 0);
                gloss.getShader().setLocalMatrix(sweepMatrix);
                canvas.drawRect(card, gloss);
            }
        }
        if (moving) canvas.drawColor(0x99000000);
        canvas.restore();

        if (item.state == TvInputManager.INPUT_STATE_CONNECTED) {
            float r = Ui.dp(getContext(), 3.5f);
            canvas.drawCircle(cardW - r * 3, r * 3, r, dot);
        }
        if (Ui.theme.edge != 0 && !isFocused() && !moving) {
            float half = edge.getStrokeWidth() / 2;
            canvas.drawRoundRect(half, half, cardW - half, cardH - half, radius - half, radius - half, edge);
        }
        if (glow && isFocused() && !moving) drawHalo(canvas);
        if (moving || isFocused() && (!lift || glow)) {
            float half = stroke / 2;
            ring.setPathEffect(moving ? new DashPathEffect(new float[] {stroke * 3, stroke * 2}, 0) : null);
            canvas.drawRoundRect(half, half, cardW - half, cardH - half, radius - half, radius - half, ring);
        }
        if (moving) drawArrows(canvas);
        boolean label = banner != null && (labelAlways || labelOnFocus && isFocused());
        if (labelH > 0 && label) {
            drawLine(canvas, under, cardW / 2f, cardH + labelH - Ui.dp(getContext(), 6));
        }
    }

    /** Corner radius of an Apple TV icon {@code width} wide; the dock capsule builds on it. */
    static float iconRadius(int width) {
        return width * 0.1f;
    }

    /**
     * A glow around the card from three widening, fading outlines: no blur and no software
     * layer, so it costs the same as the focus ring. The rows do not clip their children.
     */
    private void drawHalo(Canvas canvas) {
        int base = ring.getColor() & 0x00FFFFFF;
        int[] alpha = {0x55, 0x2A, 0x12};
        for (int i = 0; i < alpha.length; i++) {
            float w = stroke * (i + 1) * 1.6f;
            float out = stroke + w / 2 - stroke / 2;
            halo.setStrokeWidth(w);
            halo.setColor(base | alpha[i] << 24);
            canvas.drawRoundRect(-out, -out, cardW + out, cardH + out, radius + out, radius + out, halo);
        }
    }

    private void drawLine(Canvas canvas, TextPaint paint, float x, float baseline) {
        float room = cardW - Ui.dp(getContext(), 16);
        CharSequence s = TextUtils.ellipsize(title, paint, room, TextUtils.TruncateAt.END);
        canvas.drawText(s, 0, s.length(), x, baseline, paint);
    }

    private void drawArrows(Canvas canvas) {
        float h = cardH / 6f, w = h * 0.7f, mid = cardH / 2f, pad = h * 0.8f;
        inner.setStyle(Paint.Style.FILL);
        arrow.reset();
        arrow.moveTo(pad, mid);
        arrow.lineTo(pad + w, mid - h / 2);
        arrow.lineTo(pad + w, mid + h / 2);
        arrow.close();
        arrow.moveTo(cardW - pad, mid);
        arrow.lineTo(cardW - pad - w, mid - h / 2);
        arrow.lineTo(cardW - pad - w, mid + h / 2);
        arrow.close();
        canvas.drawPath(arrow, inner);
    }
}
