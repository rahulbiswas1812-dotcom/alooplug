package com.aloo.pinata.event;

import com.aloo.pinata.AlooPinataParty;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/** Optional event history (history.yml). Written once per event, off the main thread. */
public final class HistoryManager {

    private final AlooPinataParty plugin;
    private final Object lock = new Object();

    public HistoryManager(AlooPinataParty plugin) {
        this.plugin = plugin;
    }

    public void record(Map<String, Object> entry) {
        if (!plugin.settings().historyEnabled) {
            return;
        }
        int max = plugin.settings().historyMaxEntries;
        File file = new File(plugin.getDataFolder(), "history.yml");
        Runnable job = () -> write(file, entry, max);
        if (plugin.isEnabled() && !plugin.isShuttingDown()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, job);
        } else {
            job.run();
        }
    }

    private void write(File file, Map<String, Object> entry, int max) {
        synchronized (lock) {
            try {
                YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
                List<Map<?, ?>> list = new ArrayList<>(y.getMapList("events"));
                list.add(entry);
                while (list.size() > max) {
                    list.remove(0);
                }
                y.set("events", list);
                y.save(file);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Could not write history.yml", e);
            }
        }
    }
}
