package me.Thomas.HCF.IO.Locale;

import me.Thomas.HCF.Utils.Timer.Timer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public class LocaleConfig extends YamlConfiguration {

    public @NotNull String getTimerMessage(@NotNull String stage, Timer.@NotNull Type type) {
        String name = type.name().toLowerCase(Locale.ROOT);
        String section = "events.player.timer." + stage + ".";
        String path = section + (isString(section + name) ? name : "default");
        return isString(path) ? getString(path).replace("%timer%", name) : "";
    }

    @Override
    public @NotNull String getString(@NotNull String path) {
        Object fallback = getDefault(path);
        return getString(path, fallback == null ? null : fallback.toString());
    }

    @Override
    public @NotNull String getString(@NotNull String path, @Nullable String fallback) {
        String value = super.getString(path, fallback);
        return value == null ? "&cMissing locale message: " + path : value;
    }
}
