package tv.dumbtv.home;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Once a week, the ten best-liked Unsplash photos for each time of day, into the wallpapers
 * folder as {@code morning-top-01.jpg} and so on, with the photographers in {@code credits.txt}.
 * The only network access in the launcher, and only when {@code unsplash.key} (an Unsplash API
 * access key) is set in home.conf. As the API guidelines ask, each download is reported to
 * Unsplash and each photo is credited where it is shown.
 */
final class Fetch {
    static final String KEY = "unsplash.key", WHEN = "wallpapers.fetched";
    static final String CREDITS = "credits.txt";
    private static final long WEEK_MS = 7L * 24 * 3_600_000;
    private static final int TOP = 10;
    /** What each time of day searches for; the file names carry the period word. */
    private static final String[][] PERIODS = {
        {"morning", "sunrise mountains landscape"},
        {"afternoon", "sunset landscape"},
        {"night", "night sky stars landscape"},
    };
    private static volatile boolean running;

    private Fetch() {}

    /** Starts a fetch on its own thread when a key is set and the last one is a week old. */
    static void maybe(Config cfg) {
        String key = cfg.get(KEY, "");
        if (key.isEmpty() || running) return;
        long last;
        try {
            last = Long.parseLong(cfg.get(WHEN, "0"));
        } catch (NumberFormatException e) {
            last = 0;
        }
        if (System.currentTimeMillis() - last < WEEK_MS) return;
        running = true;
        File folder = new File(cfg.dir, Config.WALLPAPERS);
        Handler ui = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            boolean ok = false;
            try {
                ok = run(key, folder);
            } catch (IOException | JSONException | RuntimeException e) {
                Log.w("dumbtv", "wallpaper fetch failed", e);
            } finally {
                running = false;
            }
            if (ok) ui.post(() -> cfg.set(WHEN, String.valueOf(System.currentTimeMillis())));
        }, "wallpaper-fetch").start();
    }

    private static boolean run(String key, File folder) throws IOException, JSONException {
        if (!folder.isDirectory() && !folder.mkdirs()) return false;
        List<String> credits = new ArrayList<>();
        List<String> kept = new ArrayList<>();
        for (String[] period : PERIODS) {
            String q = URLEncoder.encode(period[1], "UTF-8");
            JSONArray results = new JSONObject(get("https://api.unsplash.com/search/photos?per_page=30"
                    + "&orientation=landscape&content_filter=high&query=" + q, key)).getJSONArray("results");
            List<JSONObject> photos = new ArrayList<>();
            for (int i = 0; i < results.length(); i++) {
                JSONObject p = results.getJSONObject(i);
                if (p.optInt("width") >= 3000 && !p.optBoolean("premium") && !p.optBoolean("plus")) photos.add(p);
            }
            Collections.sort(photos, (a, b) -> Integer.compare(b.optInt("likes"), a.optInt("likes")));
            for (int i = 0; i < Math.min(TOP, photos.size()); i++) {
                JSONObject p = photos.get(i);
                String name = String.format(java.util.Locale.ROOT, "%s-top-%02d.jpg", period[0], i + 1);
                // The guidelines' download event, then the picture itself at TV size.
                get(p.getJSONObject("links").getString("download_location"), key);
                save(p.getJSONObject("urls").getString("raw") + "&w=1920&h=1080&fit=crop&q=80&fm=jpg",
                        new File(folder, name));
                kept.add(name);
                credits.add(name + "\t" + p.getJSONObject("user").optString("name", "Unsplash"));
            }
        }
        if (kept.isEmpty()) return false;
        // Last week's top pictures that dropped out go; pictures you added stay.
        File[] old = folder.listFiles();
        if (old != null) {
            for (File f : old) {
                if (f.getName().contains("-top-") && !kept.contains(f.getName())) f.delete();
            }
        }
        try (Writer w = new OutputStreamWriter(new FileOutputStream(new File(folder, CREDITS)), StandardCharsets.UTF_8)) {
            for (String line : credits) w.write(line + "\n");
        }
        return true;
    }

    private static String get(String url, String key) throws IOException {
        HttpURLConnection c = open(url, key);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            copy(in, out);
            return out.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }

    /** Downloads to a temporary name first, so a broken download never shows as a wallpaper. */
    private static void save(String url, File to) throws IOException {
        File tmp = new File(to.getPath() + ".part");
        HttpURLConnection c = open(url, null);
        try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(tmp)) {
            copy(in, out);
        } finally {
            c.disconnect();
        }
        if (!tmp.renameTo(to)) throw new IOException("could not replace " + to);
    }

    private static HttpURLConnection open(String url, String key) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15_000);
        c.setReadTimeout(30_000);
        if (key != null) {
            c.setRequestProperty("Authorization", "Client-ID " + key);
            c.setRequestProperty("Accept-Version", "v1");
        }
        if (c.getResponseCode() / 100 != 2) {
            int code = c.getResponseCode();
            c.disconnect();
            throw new IOException("HTTP " + code + " for " + url.replaceAll("\\?.*", ""));
        }
        return c;
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[16 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }

    /** The photographer of {@code picture}, from credits.txt next to it; null when unknown. */
    static String credit(File picture) {
        if (picture == null) return null;
        File list = new File(picture.getParentFile(), CREDITS);
        if (!list.isFile()) return null;
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(
                new java.io.FileInputStream(list), StandardCharsets.UTF_8))) {
            for (String line; (line = r.readLine()) != null; ) {
                int tab = line.indexOf('\t');
                if (tab > 0 && line.substring(0, tab).equals(picture.getName())) return line.substring(tab + 1);
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }
}
