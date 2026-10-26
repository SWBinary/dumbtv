package tv.dumbtv.home;

import android.graphics.Typeface;

/**
 * One look for the whole launcher: colours, shapes, focus style and fonts. {@link Ui} copies the
 * active theme into its fields, so views read {@code Ui.TEXT} and friends as before. Options ›
 * Theme picks one by {@link #key}; a new theme is one more constant here.
 */
final class Theme {
    /** How a focused tile is marked. */
    static final int RING = 0, GLOW = 1;

    final String key, name;
    int text, muted, faint, surface, inputSurface, page;
    /** Accent and background colour for Accent › Theme and Background › Theme. */
    int accent, ground;
    /** Tile and chip corner radius in dp; tile outline (0 = none); chip colour while unfocused. */
    float radius = 10, chipRadius = 18;
    int edge, chip = 0x14FFFFFF;
    int focus = RING;
    /** Apple TV layout without a wallpaper: soft blobs of these colours over the whole screen. */
    int[] aurora = {0xFF0B1E4A, 0xFF4A2370, 0xFF0F5566, 0xFF6A2058};
    /** Row titles and the date in capitals, as labels on a panel. */
    boolean caps;
    /** Font families, turned into Typefaces on first use (host-side tests have no fonts). */
    private String lightFamily = "sans-serif-light", mediumFamily = "sans-serif-medium",
            clockFamily = "sans-serif-light", labelFamily = "sans-serif-medium";
    private Typeface light, medium, clock, label;

    private Theme(String key, String name) {
        this.key = key;
        this.name = name;
    }

    private Theme colors(int text, int muted, int faint, int surface, int inputSurface, int page) {
        this.text = text;
        this.muted = muted;
        this.faint = faint;
        this.surface = surface;
        this.inputSurface = inputSurface;
        this.page = page;
        return this;
    }

    private Theme fonts(String clock, String label) {
        clockFamily = clock;
        labelFamily = label;
        return this;
    }

    Typeface light() {
        if (light == null) light = Typeface.create(lightFamily, Typeface.NORMAL);
        return light;
    }

    Typeface medium() {
        if (medium == null) medium = Typeface.create(mediumFamily, Typeface.NORMAL);
        return medium;
    }

    Typeface clock() {
        if (clock == null) clock = Typeface.create(clockFamily, Typeface.NORMAL);
        return clock;
    }

    Typeface label() {
        if (label == null) label = Typeface.create(labelFamily, Typeface.NORMAL);
        return label;
    }

    /** The look dumbtv home shipped with. */
    static final Theme ORIGINAL = new Theme("original", "dumbtv Original")
            .colors(0xFFF2F2F3, 0x99FFFFFF, 0x66FFFFFF, 0xFF1C1D21, 0xFF17181B, 0xFF0C0C0E);

    /**
     * The research-agent web cockpit: near-black panels with a hairline edge, warm white ink,
     * amber accent with a glow on focus, monospace labels in capitals.
     */
    static final Theme COCKPIT = new Theme("cockpit", "Cockpit")
            .colors(0xFFE8E6E1, 0xFF9A9DA3, 0xFF7C8087, 0xFF15171A, 0xFF111316, 0xFF0C0D0F)
            .fonts("monospace", "monospace");

    static {
        ORIGINAL.accent = 0xFF3D8BFF;
        ORIGINAL.ground = 0xFF000000;

        COCKPIT.accent = 0xFFFFB547;
        COCKPIT.ground = 0xFF0C0D0F;
        COCKPIT.edge = 0xFF2A2E34;
        COCKPIT.chip = 0xFF1F2227;
        COCKPIT.chipRadius = 12;
        COCKPIT.focus = GLOW;
        COCKPIT.caps = true;
        COCKPIT.aurora = new int[] {0xFF2A1A06, 0xFF4A2C08, 0xFF101114, 0xFF1E2228};
    }

    static final Theme[] ALL = {ORIGINAL, COCKPIT};

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
