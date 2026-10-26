package tv.dumbtv.home;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

/**
 * Control Center's Sleep and Sleep Timer. Sleep asks {@link Guard} to lock the screen and falls
 * back to the TV's screensaver; the timer is one exact alarm that lands here. When it is due is
 * kept in home.conf ({@code sleep.at}, wall-clock millis), so the panel can show what is left.
 */
public final class Sleep extends BroadcastReceiver {
    static final String KEY = "sleep.at";
    private static final String SCREENSAVER = Catalog.SCREENSAVER;

    @Override
    public void onReceive(Context c, Intent i) {
        Config.get(c).set(KEY, null);
        now(c);
    }

    static void now(Context c) {
        if (!Guard.sleep()) screensaver(c);
    }

    /** Our screensaver, the wallpaper view; Amazon's when ours cannot start. */
    static boolean screensaver(Context c) {
        if (Showcase.start(c)) return true;
        PackageManager pm = c.getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(SCREENSAVER);
        if (i == null) i = pm.getLaunchIntentForPackage(SCREENSAVER);
        if (i == null) return false;
        try {
            c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }

    /** Whole minutes until the timer fires, rounded up; 0 when it is off or has passed. */
    static int minutesLeft(Config cfg, long now) {
        long at;
        try {
            at = Long.parseLong(cfg.get(KEY, "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
        return at > now ? (int) ((at - now + 59_999) / 60_000) : 0;
    }

    /** Sets the timer to {@code minutes} from now, or turns it off with 0. */
    static void set(Context c, Config cfg, int minutes) {
        AlarmManager alarms = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent due = PendingIntent.getBroadcast(c, 0, new Intent(c, Sleep.class),
                PendingIntent.FLAG_UPDATE_CURRENT);
        alarms.cancel(due);
        if (minutes <= 0) {
            cfg.set(KEY, null);
            return;
        }
        long at = System.currentTimeMillis() + minutes * 60_000L;
        cfg.set(KEY, String.valueOf(at));
        alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, due);
    }
}
