package me.Thomas.HCF.Utils.Timer;

import me.Thomas.HCF.Main.Main;

public class Timer {

    public enum Type {
        PVP("timers.pvp"),
        STARTING("timers.starting"),
        COMBAT("timers.combat"),
        CHAT("timers.chat"),
        REQUEST("timers.request"),
        REPORT("timers.report"),
        LFF("timers.lff"),
        RANKREVIVE("timers.rankrevive"),
        HOME("timers.home"),
        LOGOUT("timers.logout"),
        STUCK("timers.stuck"),
        ARCHERTAG("timers.archertag"),
        ARCHERSPEED("timers.archerSpeed"),
        ARCHERJUMP("timers.archerJump"),
        BARD("timers.bard"),
        ROGUESPEED("timers.rogueSpeed"),
        ROGUEJUMP("timers.rogueJump"),
        ROGUEBACKSTAB("timers.rogueBackstab"),
        APPLE("timers.apple"),
        GAPPLE("timers.gapple"),
        ENDERPEARL("timers.enderpearl");

        private final String configPath;

        Type(String configPath) {
            this.configPath = configPath;
        }

        public int getLength() {
            int defaultLength = switch (this) {
                case REQUEST, REPORT, LFF -> 300;
                case BARD -> 10;
                case STUCK -> 60;
                case LOGOUT -> 30;
                case STARTING -> 3600;
                case ROGUEBACKSTAB -> 15;
                case ARCHERSPEED, ARCHERJUMP, ROGUESPEED, ROGUEJUMP -> 30;
                default -> 0;
            };
            return Main.getInstance().getConfig().getInt(this.configPath, defaultLength);
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
