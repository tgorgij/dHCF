package me.Thomas.HCF.Utils.Combat;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class CombatLoggerEffects {
    private CombatLoggerEffects() {
    }

    public static String serialize(Collection<PotionEffect> effects) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("effects", new ArrayList<>(effects));
        return config.saveToString();
    }

    public static List<PotionEffect> deserialize(String data) {
        if (data.isEmpty()) return List.of();
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.loadFromString(data);
        } catch (InvalidConfigurationException exception) {
            throw new IllegalArgumentException("Invalid combat-logger potion effects", exception);
        }
        List<PotionEffect> effects = new ArrayList<>();
        for (Object value : config.getList("effects", List.of())) {
            if (!(value instanceof PotionEffect effect)) throw new IllegalArgumentException("Invalid potion effect");
            effects.add(effect);
        }
        return effects;
    }
}
