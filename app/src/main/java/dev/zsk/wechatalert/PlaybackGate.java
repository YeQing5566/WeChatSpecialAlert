package dev.zsk.wechatalert;

/** Pure Java finite-state machine, separately unit-tested (see tests/). */
public final class PlaybackGate {
    public enum State { IDLE, WAITING, PLAYING, COOLDOWN }
    private State state = State.IDLE;
    private long allowedAt;

    public synchronized boolean accept(long nowElapsedMs) {
        if (state == State.COOLDOWN && nowElapsedMs >= allowedAt) state = State.IDLE;
        if (state != State.IDLE) return false;
        state = State.WAITING;
        return true;
    }
    public synchronized boolean beginPlayback() {
        if (state != State.WAITING) return false;
        state = State.PLAYING;
        return true;
    }
    public synchronized void playbackFinished(long nowElapsedMs, long cooldownMs) {
        if (state != State.PLAYING) return;
        allowedAt = nowElapsedMs + Math.max(0, cooldownMs);
        state = State.COOLDOWN;
    }
    public synchronized State state(long nowElapsedMs) {
        if (state == State.COOLDOWN && nowElapsedMs >= allowedAt) state = State.IDLE;
        return state;
    }
    public synchronized void reset() { state = State.IDLE; allowedAt = 0; }
}
