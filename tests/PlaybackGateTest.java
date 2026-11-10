package dev.zsk.wechatalert;

public class PlaybackGateTest {
    private static void check(boolean condition, String caseName) {
        if (!condition) throw new AssertionError(caseName);
    }
    public static void main(String[] args) {
        PlaybackGate g = new PlaybackGate();
        check(g.accept(100), "first accepted");
        check(!g.accept(101), "debounce waiting drop");
        check(g.beginPlayback(), "begin");
        check(!g.accept(500), "playing drop");
        g.playbackFinished(2000, 3000);
        check(!g.accept(4999), "cooldown drop");
        check(g.accept(5000), "cooldown expiry accepted");
        check(g.beginPlayback(), "second started");
        g.playbackFinished(7000, 0);
        check(g.accept(7000), "zero cooldown");
        g.reset();
        check(g.state(8000) == PlaybackGate.State.IDLE, "reset");
        System.out.println("PlaybackGateTest: PASS (9 assertions)");
    }
}
