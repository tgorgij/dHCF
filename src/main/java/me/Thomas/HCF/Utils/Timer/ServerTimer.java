package me.Thomas.HCF.Utils.Timer;

public class ServerTimer {

    public enum Type {
        SOTW,
        KOTH,
        CONQUEST,
        EOTW,
        SALE,
        KEYSALE,
        CUSTOM
    }

    private final Type type;
    private final String name;
    private long expires;
    private long pausedTime;

    public ServerTimer(Type type, long expires) {
        this.type = type;
        this.name = type.name();
        this.expires = System.currentTimeMillis() + expires * 1000L;
        this.pausedTime = 0L;
    }

    public ServerTimer(String name, long seconds) {
        this.type = Type.CUSTOM;
        this.name = name;
        this.expires = Math.addExact(System.currentTimeMillis(), Math.multiplyExact(seconds, 1000L));
        this.pausedTime = 0L;
    }

    public String getName() { return name; }

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

    public void addTime(long seconds) {
        long adjustedExpires = Math.addExact(expires, Math.multiplyExact(seconds, 1000L));
        long now = isPaused() ? pausedTime : System.currentTimeMillis();
        expires = Math.max(now, adjustedExpires);
    }

    public long getRemainingTime() {
        return getRemainingTimeMillis() / 1000L;
    }
}
