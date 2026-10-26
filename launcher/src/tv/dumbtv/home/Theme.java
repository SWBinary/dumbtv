package tv.dumbtv.home;

import android.graphics.Typeface;

/**
 * One look for the whole launcher: text colours, surfaces and fonts. {@link Ui} copies the active
 * theme into its fields, so views read {@code Ui.TEXT} and friends as before. Options › Theme
 * picks one by {@link #key}; a new theme is one more constant here and one more value there.
 */
final class Theme {
    final String key, name;
    final int text, muted, faint, surface, inputSurface, page;
    /** Font families, turned into Typefaces on first use (host-side tests have no fonts). */
    private final String lightFamily, mediumFamily;
    private Typeface light, medium;

    private Theme(String key, String name, int text, int muted, int faint, int surface,
            int inputSurface, int page, String lightFamily, String mediumFamily) {
        this.key = key;
        this.name = name;
        this.text = text;
        this.muted = muted;
        this.faint = faint;
        this.surface = surface;
        this.inputSurface = inputSurface;
        this.page = page;
        this.lightFamily = lightFamily;
        this.mediumFamily = mediumFamily;
    }

    Typeface light() {
        if (light == null) light = Typeface.create(lightFamily, Typeface.NORMAL);
        return light;
    }

    Typeface medium() {
        if (medium == null) medium = Typeface.create(mediumFamily, Typeface.NORMAL);
        return medium;
    }

    /** The look dumbtv home shipped with. */
    static final Theme ORIGINAL = new Theme("original", "dumbtv Original",
            0xFFF2F2F3, 0x99FFFFFF, 0x66FFFFFF, 0xFF1C1D21, 0xFF17181B, 0xFF0C0C0E,
            "sans-serif-light", "sans-serif-medium");

    static final Theme[] ALL = {ORIGINAL};

    static Theme of(String key) {
        for (Theme t : ALL) {
            if (t.key.equals(key)) return t;
        }
        return ORIGINAL;
    }

    static String[] keys() {
        String[] out = new String[ALL.length];
        for (int i = 0; i < ALL.length; i++) out[i] = ALL[i].key;
        return out;
    }

    static String[] names() {
        String[] out = new String[ALL.length];
        for (int i = 0; i < ALL.length; i++) out[i] = ALL[i].name;
        return out;
    }
}
