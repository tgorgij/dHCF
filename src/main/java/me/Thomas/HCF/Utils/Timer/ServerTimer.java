package me.Thomas.HCF.Utils.Timer;

public class ServerTimer {

    public enum Type {
        SOTW,
        EOTW,
        SALE,
        KEYSALE
    }

    private final Type type;
    private long expires;
    private long pausedTime;

    public ServerTimer(Type type, long expires) {
        this.type = type;
        this.expires = System.currentTimeMillis() + expires * 1000L;
        this.pausedTime = 0L;
    }

    public ServerTimer.Type getType() {
        return type;
    }


    public boolean isPaused() {
        return pausedTime > 0L;
    }

    public void pause() {
        if (isPaused()) {
            expires += (System.currentTimeMillis() - pausedTime);
            pausedTime = 0L;
        } else {
            pausedTime = System.currentTimeMillis();
        }
    }

    public long getRemainingTimeMillis() {
        long now = isPaused() ? pausedTime : System.currentTimeMillis();

        return Math.max(0L, expires - now);
    }

    public long getPausedTime() {
        return pausedTime;
    }

    public long getExpires() {
        return expires;
    }

    public long getRemainingTime() {
        return getRemainingTimeMillis() / 1000L;
    }
}
