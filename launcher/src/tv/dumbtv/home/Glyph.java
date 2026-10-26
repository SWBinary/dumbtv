package tv.dumbtv.home;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * The Control Center's line icons, drawn rather than taken from Android: Fire OS draws several
 * system icons alike (Settings and Manage are both a wrench). Thin rounded strokes as on tvOS;
 * the colour follows the button's state, white on glass and dark when focused.
 */
final class Glyph extends Drawable {
    static final int POWER = 0, TIMER = 1, PICTURE = 2, GEAR = 3, SLIDERS = 4, BAG = 5, MOON = 6;

    private final int kind;
    private final ColorStateList ink;
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF box = new RectF();

    Glyph(int kind, int color) {
        this(kind, ColorStateList.valueOf(color));
    }

    Glyph(int kind, ColorStateList ink) {
        this.kind = kind;
        this.ink = ink;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        fill.setStyle(Paint.Style.FILL);
        color(ink.getDefaultColor());
    }

    int kind() {
        return kind;
    }

    private void color(int c) {
        line.setColor(c);
        fill.setColor(c);
    }

    @Override
    public boolean isStateful() {
        return true;
    }

    @Override
    protected boolean onStateChange(int[] state) {
        int c = ink.getColorForState(state, ink.getDefaultColor());
        if (c == line.getColor()) return false;
        color(c);
        invalidateSelf();
        return true;
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        float s = Math.min(b.width(), b.height());
        float x = b.left + (b.width() - s) / 2f, y = b.top + (b.height() - s) / 2f;
        float cx = x + s / 2f, cy = y + s / 2f;
        line.setStrokeWidth(s * 0.085f);
        switch (kind) {
            case POWER:
                // A ring open at the top, and the stroke through the gap.
                box.set(cx - s * 0.36f, cy - s * 0.32f, cx + s * 0.36f, cy + s * 0.40f);
                canvas.drawArc(box, -55, 290, false, line);
                canvas.drawLine(cx, y + s * 0.06f, cx, cy, line);
                break;
            case TIMER:
                // A clock face at ten past.
                canvas.drawCircle(cx, cy, s * 0.40f, line);
                canvas.drawLine(cx, cy, cx, cy - s * 0.24f, line);
                canvas.drawLine(cx, cy, cx + s * 0.17f, cy + s * 0.07f, line);
                break;
            case PICTURE:
                // A frame with a mountain and a sun.
                box.set(x + s * 0.08f, y + s * 0.18f, x + s * 0.92f, y + s * 0.82f);
                canvas.drawRoundRect(box, s * 0.1f, s * 0.1f, line);
                path.reset();
                path.moveTo(x + s * 0.16f, y + s * 0.74f);
                path.lineTo(x + s * 0.42f, y + s * 0.44f);
                path.lineTo(x + s * 0.60f, y + s * 0.62f);
                path.lineTo(x + s * 0.70f, y + s * 0.52f);
                path.lineTo(x + s * 0.84f, y + s * 0.70f);
                canvas.drawPath(path, line);
                canvas.drawCircle(x + s * 0.70f, y + s * 0.34f, s * 0.06f, fill);
                break;
            case GEAR:
                // A cog: a disc, eight rounded teeth around it, and a hole through the middle.
                path.reset();
                path.addCircle(cx, cy, s * 0.31f, Path.Direction.CW);
                android.graphics.Matrix turn = new android.graphics.Matrix();
                for (int i = 0; i < 8; i++) {
                    Path tooth = new Path();
                    box.set(cx - s * 0.075f, cy - s * 0.46f, cx + s * 0.075f, cy - s * 0.24f);
                    tooth.addRoundRect(box, s * 0.03f, s * 0.03f, Path.Direction.CW);
                    turn.setRotate(45 * i, cx, cy);
                    tooth.transform(turn);
                    path.op(tooth, Path.Op.UNION);
                }
                Path hole = new Path();
                hole.addCircle(cx, cy, s * 0.13f, Path.Direction.CW);
                path.op(hole, Path.Op.DIFFERENCE);
                canvas.drawPath(path, fill);
                break;
            case SLIDERS:
                // Three rails with their knobs at different places.
                float[] rail = {0.26f, 0.5f, 0.74f}, knob = {0.34f, 0.66f, 0.46f};
                for (int i = 0; i < 3; i++) {
                    float ry = y + s * rail[i];
                    canvas.drawLine(x + s * 0.1f, ry, x + s * 0.9f, ry, line);
                    canvas.drawCircle(x + s * knob[i], ry, s * 0.09f, fill);
                }
                break;
            case BAG:
                // A shopping bag: the body, and the handle above it.
                box.set(x + s * 0.18f, y + s * 0.36f, x + s * 0.82f, y + s * 0.88f);
                canvas.drawRoundRect(box, s * 0.08f, s * 0.08f, line);
                box.set(cx - s * 0.17f, y + s * 0.14f, cx + s * 0.17f, y + s * 0.52f);
                canvas.drawArc(box, 180, 180, false, line);
                break;
            case MOON:
                // A crescent and two stars.
                path.reset();
                path.addCircle(cx - s * 0.04f, cy + s * 0.04f, s * 0.34f, Path.Direction.CW);
                Path bite = new Path();
                bite.addCircle(cx + s * 0.14f, cy - s * 0.12f, s * 0.30f, Path.Direction.CW);
                path.op(bite, Path.Op.DIFFERENCE);
                canvas.drawPath(path, fill);
                canvas.drawCircle(x + s * 0.80f, y + s * 0.20f, s * 0.05f, fill);
                canvas.drawCircle(x + s * 0.88f, y + s * 0.46f, s * 0.035f, fill);
                break;
            default:
                break;
        }
    }

    @Override
    public void setAlpha(int alpha) {
        line.setAlpha(alpha);
        fill.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter filter) {
        line.setColorFilter(filter);
        fill.setColorFilter(filter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
