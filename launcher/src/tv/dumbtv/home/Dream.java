package tv.dumbtv.home;

import android.service.dreams.DreamService;

/**
 * The launcher's screensaver for Fire OS's idle timer: the wallpapers of {@link Showcase},
 * chosen with {@code settings put secure screensaver_components tv.dumbtv.home/.Dream}.
 */
public final class Dream extends DreamService {
    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setContentView(new Showcase.Stage(this));
    }
}
