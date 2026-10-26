package com.aloo.pinata.config;

import com.aloo.pinata.util.Strings;
import com.aloo.pinata.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/** Access to messages.yml. */
public final class Messages {

    private volatile FileConfiguration cfg;

    public void reload(FileConfiguration cfg) {
        this.cfg = cfg;
    }

    public String raw(String key) {
        String s = cfg == null ? null : cfg.getString(key);
        return s == null ? "&c<missing message: " + key + ">" : s;
    }

    public List<String> rawList(String key) {
        return cfg == null ? List.of() : cfg.getStringList(key);
    }

    public Component component(String key, String... kv) {
        return Text.color(Strings.replace(raw(key), kv));
    }

    public Component prefixed(String key, String... kv) {
        return Text.color(raw("prefix") + Strings.replace(raw(key), kv));
    }

    public void send(CommandSender to, String key, String... kv) {
        to.sendMessage(prefixed(key, kv));
    }

    public void sendRawLines(CommandSender to, String listKey, String... kv) {
        for (String line : rawList(listKey)) {
            to.sendMessage(Text.color(Strings.replace(line, kv)));
        }
    }
}
