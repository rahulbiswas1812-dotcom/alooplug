package com.aloo.pinata.config;

import com.aloo.pinata.AlooPinataParty;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Loads the five YAML files. Missing files are created from the jar, existing files are never overwritten,
 * and keys missing from an old file fall back to the built-in defaults.
 */
public final class ConfigManager {

    private static final String[] FILES = {"config.yml", "arena.yml", "mobs.yml", "messages.yml", "rewards.yml"};

    private final AlooPinataParty plugin;
    private final Map<String, YamlConfiguration> loaded = new HashMap<>();
    private volatile Settings settings;

    public ConfigManager(AlooPinataParty plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File dir = plugin.getDataFolder();
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("Could not create the plugin data folder.");
        }
        for (String name : FILES) {
            loadFile(name);
        }
        settings = new Settings(config(), plugin.getLogger());
    }

    private void loadFile(String name) {
        File f = new File(plugin.getDataFolder(), name);
        if (!f.exists()) {
            plugin.saveResource(name, false);
        }
        YamlConfiguration cfg = new YamlConfiguration();
        try {
            cfg.load(f);
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not parse " + name + " - using built-in defaults. "
                    + "A copy of the broken file was saved as " + name + ".broken. Error: " + e.getMessage());
            try {
                Files.copy(f.toPath(), new File(plugin.getDataFolder(), name + ".broken").toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // best effort
            }
            cfg = new YamlConfiguration();
        }
        try (InputStream in = plugin.getResource(name)) {
            if (in != null) {
                cfg.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
            }
        } catch (IOException ignored) {
            // defaults are optional
        }
        loaded.put(name, cfg);
    }

    public boolean save(String name) {
        YamlConfiguration cfg = loaded.get(name);
        if (cfg == null) {
            return false;
        }
        try {
            cfg.save(new File(plugin.getDataFolder(), name));
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save " + name, e);
            return false;
        }
    }

    public Settings settings() {
        return settings;
    }

    public FileConfiguration config() {
        return loaded.get("config.yml");
    }

    public FileConfiguration arena() {
        return loaded.get("arena.yml");
    }

    public FileConfiguration mobs() {
        return loaded.get("mobs.yml");
    }

    public FileConfiguration messages() {
        return loaded.get("messages.yml");
    }

    public FileConfiguration rewards() {
        return loaded.get("rewards.yml");
    }
}
