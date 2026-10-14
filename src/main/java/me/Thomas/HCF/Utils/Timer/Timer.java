package me.Thomas.HCF.Utils.Timer;

import me.Thomas.HCF.Main.Main;

public class Timer {

    public enum Type {
        PVP("timers.pvp"),
        COMBAT("timers.combat"),
        HOME("timers.home"),
        STUCK("timers.stuck"),
        ARCHERTAG("timers.archertag"),
        APPLE("timers.apple"),
        GAPPLE("timers.gapple"),
        ENDERPEARL("timers.enderpearl");

        private final String configPath;

        Type(String configPath) {
            this.configPath = configPath;
        }

        public int getLength() {
            return Main.getInstance().getConfig().getInt(this.configPath, this == STUCK ? 60 : 0);
        }
    }

    private final Type type;
    private long expires;
    private long pausedTime;
    private final boolean pauseOnLogout;

    public Timer(Type type) {
        this(type, false);
    }

    public Timer(Type type, boolean pauseOnLogout) {
        this.type = type;
        this.pauseOnLogout = pauseOnLogout;
        this.expires = System.currentTimeMillis() + type.getLength() * 1000L;
        pausedTime = 0L;
    }

    public Timer(Type type, long expires, long pausedTime, boolean pauseOnLogout) {
        this.type = type;
        this.expires = expires;
        this.pausedTime = pausedTime;
        this.pauseOnLogout = pauseOnLogout;
    }

    public Type getType() {
        return type;
    }

    public boolean isPauseOnLogout() {
        return pauseOnLogout;
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
