package tv.dumbtv.home;

/** Host-side checks for the Apple TV backdrop blur, without a device or test framework. */
public final class BackdropTest {
    private static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
    }

    public static void main(String[] args) {
        int w = 8, h = 4;
        int[] flat = new int[w * h];
        java.util.Arrays.fill(flat, 0xFF336699);
        Backdrop.blur(flat, w, h, 3, 2);
        for (int c : flat) check(c == 0xFF336699, "a flat colour stays the same");

        int[] edge = new int[w * h];
        for (int i = 0; i < edge.length; i++) edge[i] = i % w < w / 2 ? 0xFF000000 : 0xFFFFFFFF;
        Backdrop.blur(edge, w, h, 1, 1);
        int mid = edge[w / 2] & 255;
        check(mid > 0 && mid < 255, "a hard edge turns soft");
        check((edge[0] & 255) < (edge[w - 1] & 255), "dark stays left, light stays right");
        check((edge[3] >>> 24) == 255, "the result is opaque");
        int[] white = {0xFFFFFFFF, 0xFFFFFFFF};
        Backdrop.tone(white, 0xFF5A0A0A);
        int r = white[0] >> 16 & 255, g = white[0] >> 8 & 255;
        check(r <= Backdrop.CEILING && g <= Backdrop.CEILING, "white art is dimmed for white text");
        check(r > g, "the app colour shows through");
        int[] sky = new int[48 * 27];
        Backdrop.aurora(sky, 48, 27, new int[] {0xFF0000FF, 0xFFFF0000});
        check((sky[0] & 255) > (sky[0] >> 16 & 255), "the first colour glows top left");
        check((sky[47] >> 16 & 255) > (sky[47] & 255), "the second colour glows top right");
        check(sky[26 * 48 + 24] >>> 24 == 255, "the aurora is opaque");
        System.out.println("BackdropTest passed");
    }
}
