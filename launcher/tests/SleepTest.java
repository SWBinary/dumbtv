package tv.dumbtv.home;

/** Host-side checks for the Control Center's Sleep Timer steps, without a device. */
public final class SleepTest {
    private static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
    }

    public static void main(String[] args) {
        check(Config.nextSleep(0) == 15, "off steps to 15 minutes");
        check(Config.nextSleep(15) == 30, "15 steps to 30");
        check(Config.nextSleep(22) == 30, "a running timer steps to the next longer one");
        check(Config.nextSleep(120) == 0, "after the longest it turns off");
        check(Config.nextSleep(500) == 0, "anything longer turns off");
        System.out.println("SleepTest passed");
    }
}
