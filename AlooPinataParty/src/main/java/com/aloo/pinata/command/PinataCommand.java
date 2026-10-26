package com.aloo.pinata.command;

import com.aloo.pinata.AlooPinataParty;
import com.aloo.pinata.arena.Arena;
import com.aloo.pinata.arena.ArenaManager.SaveResult;
import com.aloo.pinata.config.Messages;
import com.aloo.pinata.config.Settings;
import com.aloo.pinata.event.EventManager;
import com.aloo.pinata.event.EventManager.StartResult;
import com.aloo.pinata.event.EventManager.StopResult;
import com.aloo.pinata.event.EventState;
import com.aloo.pinata.mob.MobManager;
import com.aloo.pinata.mob.MobProfile;
import com.aloo.pinata.util.Strings;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class PinataCommand implements TabExecutor {

    private static final Map<String, String> PERMS = Map.ofEntries(
            Map.entry("help", ""),
            Map.entry("join", "aloopinata.participate"),
            Map.entry("status", "aloopinata.status"),
            Map.entry("wand", "aloopinata.wand"),
            Map.entry("setarena", "aloopinata.wand"),
            Map.entry("setspawn", "aloopinata.wand"),
            Map.entry("arena", "aloopinata.wand"),
            Map.entry("start", "aloopinata.start"),
            Map.entry("forcestart", "aloopinata.start"),
            Map.entry("stop", "aloopinata.stop"),
            Map.entry("reload", "aloopinata.reload"),
            Map.entry("schedule", "aloopinata.status"),
            Map.entry("sethealth", "aloopinata.admin"),
            Map.entry("testrewards", "aloopinata.admin"));

    private static final Map<String, String> USAGE = Map.ofEntries(
            Map.entry("help", "/pinata help"),
            Map.entry("join", "/pinata join"),
            Map.entry("status", "/pinata status"),
            Map.entry("wand", "/pinata wand"),
            Map.entry("setarena", "/pinata setarena [confirm]"),
            Map.entry("setspawn", "/pinata setspawn [confirm]"),
            Map.entry("arena", "/pinata arena"),
            Map.entry("start", "/pinata start [seconds]"),
            Map.entry("forcestart", "/pinata forcestart <mob>"),
            Map.entry("stop", "/pinata stop"),
            Map.entry("reload", "/pinata reload"),
            Map.entry("schedule", "/pinata schedule [...]"),
            Map.entry("sethealth", "/pinata sethealth <mob> <health>"),
            Map.entry("testrewards", "/pinata testrewards [player] [rank] [execute]"));

    private static final List<String> ORDER = List.of("help", "join", "status", "wand", "setarena", "setspawn", "arena",
            "start", "forcestart", "stop", "schedule", "reload", "sethealth", "testrewards");

    private final AlooPinataParty plugin;

    public PinataCommand(AlooPinataParty plugin) {
        this.plugin = plugin;
    }

    private Messages msg() {
        return plugin.messages();
    }

    private boolean allowed(CommandSender s, String sub) {
        String perm = PERMS.get(sub);
        return perm == null || perm.isEmpty() || s.hasPermission(perm);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        try {
            if (args.length == 0) {
                help(sender);
                return true;
            }
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (!PERMS.containsKey(sub)) {
                msg().send(sender, "unknown-subcommand");
                return true;
            }
            if (!allowed(sender, sub)) {
                msg().send(sender, "no-permission");
                return true;
            }
            switch (sub) {
                case "help" -> help(sender);
                case "join" -> join(sender);
                case "status" -> status(sender);
                case "wand" -> wand(sender);
                case "setarena" -> setArena(sender, args);
                case "setspawn" -> setSpawn(sender, args);
                case "arena" -> arenaInfo(sender);
                case "start" -> start(sender, args);
                case "forcestart" -> forceStart(sender, args);
                case "stop" -> stop(sender);
                case "schedule" -> schedule(sender, args);
                case "reload" -> reload(sender);
                case "sethealth" -> setHealth(sender, args);
                case "testrewards" -> testRewards(sender, args);
                default -> msg().send(sender, "unknown-subcommand");
            }
        } catch (Throwable t) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Command error", t);
            sender.sendMessage("An internal error occurred - see the console.");
        }
        return true;
    }

    // ------------------------------------------------------------------ simple commands

    private void help(CommandSender s) {
        s.sendMessage(plugin.messages().component("help.header"));
        for (String sub : ORDER) {
            if (allowed(s, sub)) {
                s.sendMessage(plugin.messages().component("help.line", "usage", USAGE.get(sub),
                        "desc", plugin.messages().raw("help.desc." + sub)));
            }
        }
    }

    private void join(CommandSender s) {
        if (!(s instanceof Player p)) {
            msg().send(s, "player-only");
            return;
        }
        plugin.teleports().request(p);
    }

    private void status(CommandSender s) {
        EventManager ev = plugin.events();
        MobProfile mp = ev.profile();
        EventState st = ev.state();
        String remaining = switch (st) {
            case ACTIVE -> Strings.formatTime(ev.activeRemainingSeconds());
            case COUNTDOWN -> Strings.formatTime(ev.countdownRemainingSeconds());
            default -> "-";
        };
        String hp = st == EventState.ACTIVE
                ? (int) Math.ceil(ev.pinataHealth()) + "/" + (int) Math.ceil(ev.pinataMaxHealth()) : "-";
        msg().sendRawLines(s, "status.lines",
                "state", st.name(),
                "eventid", st == EventState.ACTIVE ? ev.eventId() : "-",
                "mob", mp == null ? "-" : mp.key(),
                "hp", hp,
                "participants", Integer.toString(plugin.tracker().participants()),
                "total", Strings.formatDamage(plugin.tracker().total()),
                "remaining", remaining,
                "next", plugin.settings().scheduleEnabled ? ev.schedule().format(ev.schedule().next()) : "disabled",
                "arena", plugin.arena().describeArenaShort(),
                "spawn", plugin.arena().describeSpawn());
    }

    private void wand(CommandSender s) {
        if (!(s instanceof Player p)) {
            msg().send(s, "player-only");
            return;
        }
        p.getInventory().addItem(plugin.arena().createWand()).values()
                .forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
        msg().send(p, "wand.given");
    }

    private void setArena(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) {
            msg().send(s, "player-only");
            return;
        }
        boolean confirm = args.length > 1 && args[1].equalsIgnoreCase("confirm");
        SaveResult r = plugin.arena().saveArena(p, confirm);
        switch (r) {
            case NEED_BOTH -> msg().send(p, "arena.need-both");
            case DIFFERENT_WORLDS -> msg().send(p, "arena.different-worlds");
            case WORLD_UNLOADED -> msg().send(p, "arena.world-unloaded");
            case NEEDS_CONFIRM -> msg().send(p, "arena.needs-confirm");
            case OK -> {
                Arena a = plugin.arena().arena();
                msg().send(p, "arena.saved", "world", a.world(), "volume", Long.toString(a.volume()));
                if (!plugin.arena().spawnInsideArena()) {
                    msg().send(p, "arena.spawn-outside-warning");
                }
            }
            default -> { }
        }
    }

    private void setSpawn(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) {
            msg().send(s, "player-only");
            return;
        }
        boolean confirm = args.length > 1 && args[1].equalsIgnoreCase("confirm");
        SaveResult r = plugin.arena().saveSpawn(p, confirm);
        switch (r) {
            case OUTSIDE_ARENA -> msg().send(p, "spawn.outside-arena");
            case NEEDS_CONFIRM -> msg().send(p, "spawn.needs-confirm");
            case WORLD_UNLOADED -> msg().send(p, "arena.world-unloaded");
            case OK -> {
                var l = p.getLocation();
                msg().send(p, "spawn.saved", "world", l.getWorld().getName(),
                        "x", Strings.formatNumber(Math.round(l.getX() * 10) / 10.0),
                        "y", Strings.formatNumber(Math.round(l.getY() * 10) / 10.0),
                        "z", Strings.formatNumber(Math.round(l.getZ() * 10) / 10.0));
            }
            default -> { }
        }
    }

    private void arenaInfo(CommandSender s) {
        Arena a = plugin.arena().arena();
        if (a == null) {
            msg().send(s, "arena.not-set");
            return;
        }
        msg().sendRawLines(s, "arena.info",
                "world", a.world(),
                "min", a.minX() + ", " + a.minY() + ", " + a.minZ(),
                "max", a.maxX() + ", " + a.maxY() + ", " + a.maxZ(),
                "volume", Long.toString(a.volume()),
                "spawn", plugin.arena().describeSpawn());
    }

    // ------------------------------------------------------------------ start / stop

    private void start(CommandSender s, String[] args) {
        int seconds = plugin.settings().manualCountdownSeconds;
        if (args.length > 1) {
            try {
                seconds = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                msg().send(s, "usage.start");
                return;
            }
            if (seconds < 0 || seconds > 3600) {
                msg().send(s, "usage.start");
                return;
            }
        }
        reportStart(s, plugin.events().startManual(seconds, null), seconds);
    }

    private void forceStart(CommandSender s, String[] args) {
        if (args.length < 2) {
            msg().send(s, "usage.forcestart");
            return;
        }
        MobProfile mp = plugin.mobs().find(args[1]);
        if (mp == null) {
            msg().send(s, "sethealth.unknown-mob", "mobs", String.join(", ", plugin.mobs().allKeys()));
            return;
        }
        reportStart(s, plugin.events().startManual(0, mp), 0);
    }

    private void reportStart(CommandSender s, StartResult r, int seconds) {
        switch (r) {
            case OK -> {
                if (seconds > 0) {
                    msg().send(s, "event.countdown-started", "time", Strings.formatTime(seconds));
                } else {
                    msg().send(s, "event.started-manual");
                }
            }
            case ALREADY_RUNNING -> msg().send(s, "event.already-running");
            case NOT_READY -> msg().send(s, "event.not-ready");
            case NO_MOBS -> msg().send(s, "event.no-mobs");
        }
    }

    private void stop(CommandSender s) {
        StopResult r = plugin.events().stop();
        if (r == StopResult.NOT_RUNNING) {
            msg().send(s, "event.not-running");
        }
    }

    // ------------------------------------------------------------------ schedule

    private void schedule(CommandSender s, String[] args) {
        EventManager ev = plugin.events();
        Settings st = plugin.settings();
        if (args.length == 1) {
            long next = ev.schedule().next();
            msg().sendRawLines(s, "schedule.show",
                    "enabled", st.scheduleEnabled ? "yes" : "no",
                    "interval", Strings.formatNumber(st.intervalMs / 3_600_000.0),
                    "next", st.scheduleEnabled ? ev.schedule().format(next) : "-",
                    "remaining", st.scheduleEnabled ? Strings.formatTime(Math.max(0, (next - System.currentTimeMillis()) / 1000)) : "-");
            return;
        }
        if (!s.hasPermission("aloopinata.admin")) {
            msg().send(s, "no-permission");
            return;
        }
        String op = args[1].toLowerCase(Locale.ROOT);
        switch (op) {
            case "interval" -> {
                Double hours = args.length > 2 ? parseDouble(args[2]) : null;
                if (hours == null || hours < 0.02 || hours > 1440) {
                    msg().send(s, "schedule.invalid");
                    return;
                }
                plugin.configs().config().set("schedule.interval-hours", hours);
                plugin.configs().save("config.yml");
                plugin.reloadAll();
                ev.schedule().pushBack();
                msg().send(s, "schedule.interval-set", "hours", Strings.formatNumber(hours),
                        "next", ev.schedule().format(ev.schedule().next()));
            }
            case "next" -> {
                Double minutes = args.length > 2 ? parseDouble(args[2]) : null;
                if (minutes == null || minutes < 0 || minutes > 100_000) {
                    msg().send(s, "schedule.invalid");
                    return;
                }
                ev.schedule().setNextInMinutes(minutes);
                msg().send(s, "schedule.next-set", "minutes", Strings.formatNumber(minutes),
                        "next", ev.schedule().format(ev.schedule().next()));
            }
            case "enable", "disable" -> {
                boolean on = op.equals("enable");
                plugin.configs().config().set("schedule.enabled", on);
                plugin.configs().save("config.yml");
                plugin.reloadAll();
                if (on) {
                    ev.schedule().pushBack();
                    msg().send(s, "schedule.enabled", "next", ev.schedule().format(ev.schedule().next()));
                } else {
                    msg().send(s, "schedule.disabled");
                }
            }
            case "reset" -> {
                ev.schedule().pushBack();
                msg().send(s, "schedule.reset", "next", ev.schedule().format(ev.schedule().next()));
            }
            default -> msg().send(s, "usage.schedule");
        }
    }

    private static Double parseDouble(String in) {
        try {
            double d = Double.parseDouble(in);
            return Double.isFinite(d) ? d : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ reload / sethealth / testrewards

    private void reload(CommandSender s) {
        msg().send(s, plugin.reloadAll() ? "reload.done" : "reload.failed");
    }

    private void setHealth(CommandSender s, String[] args) {
        if (args.length < 3) {
            msg().send(s, "usage.sethealth");
            return;
        }
        MobProfile mp = plugin.mobs().find(args[1]);
        if (mp == null) {
            msg().send(s, "sethealth.unknown-mob", "mobs", String.join(", ", plugin.mobs().allKeys()));
            return;
        }
        Double hp = parseDouble(args[2]);
        if (hp == null || hp < 1 || hp > MobManager.MAX_HEALTH) {
            msg().send(s, "sethealth.invalid", "max", Strings.formatNumber(MobManager.MAX_HEALTH));
            return;
        }
        var cfg = plugin.configs().mobs();
        String path = "mobs." + mp.key();
        if (!cfg.isConfigurationSection(path)) {
            // key stored in different case in the file
            for (String k : cfg.getConfigurationSection("mobs").getKeys(false)) {
                if (k.equalsIgnoreCase(mp.key())) {
                    path = "mobs." + k;
                    break;
                }
            }
        }
        cfg.set(path + ".health", hp);
        if (cfg.contains(path + ".max-health") && cfg.getDouble(path + ".max-health") < hp) {
            cfg.set(path + ".max-health", hp);
        }
        plugin.configs().save("mobs.yml");
        plugin.mobs().reload(cfg);
        msg().send(s, "sethealth.done", "mob", mp.key(), "health", Strings.formatNumber(hp));
    }

    private void testRewards(CommandSender s, String[] args) {
        boolean execute = false;
        int rank = 1;
        String name = s instanceof Player ? s.getName() : null;
        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            if (a.equalsIgnoreCase("execute")) {
                execute = true;
            } else if (a.matches("\\d{1,2}")) {
                rank = Integer.parseInt(a);
            } else {
                name = a;
            }
        }
        if (name == null || !Strings.isSafeName(name) || rank < 1) {
            msg().send(s, "usage.testrewards");
            return;
        }
        java.util.UUID uuid;
        Player online = Bukkit.getPlayerExact(name);
        uuid = online != null ? online.getUniqueId() : java.util.UUID.nameUUIDFromBytes(("test:" + name).getBytes());
        List<String> cmds = plugin.rewards().commandsFor(rank, name, uuid.toString(), 100.0, "TEST",
                "test", 1.0);
        if (cmds.isEmpty()) {
            msg().send(s, "testrewards.none");
            return;
        }
        if (!execute) {
            msg().send(s, "testrewards.preview-header", "player", name, "rank", Integer.toString(rank));
            for (String c : cmds) {
                msg().send(s, "testrewards.preview-line", "command", c);
            }
            return;
        }
        plugin.rewards().enqueue(cmds);
        msg().send(s, "testrewards.executed", "count", Integer.toString(cmds.size()), "player", name);
    }

    // ------------------------------------------------------------------ tab completion

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : ORDER) {
                if (allowed(sender, sub)) {
                    out.add(sub);
                }
            }
        } else if (args.length >= 2 && allowed(sender, args[0].toLowerCase(Locale.ROOT))) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "forcestart" -> {
                    if (args.length == 2) {
                        out.addAll(plugin.mobs().allKeys());
                    }
                }
                case "sethealth" -> {
                    if (args.length == 2) {
                        out.addAll(plugin.mobs().allKeys());
                    } else if (args.length == 3) {
                        out.addAll(List.of("150", "200", "250", "300"));
                    }
                }
                case "setarena", "setspawn" -> {
                    if (args.length == 2) {
                        out.add("confirm");
                    }
                }
                case "start" -> {
                    if (args.length == 2) {
                        out.addAll(List.of("0", "30", "60", "300"));
                    }
                }
                case "schedule" -> {
                    if (args.length == 2) {
                        out.addAll(List.of("interval", "next", "enable", "disable", "reset"));
                    } else if (args.length == 3 && args[1].equalsIgnoreCase("interval")) {
                        out.addAll(List.of("1", "2", "3", "6"));
                    } else if (args.length == 3 && args[1].equalsIgnoreCase("next")) {
                        out.addAll(List.of("1", "5", "10", "30"));
                    }
                }
                case "testrewards" -> {
                    out.addAll(List.of("1", "2", "3", "4", "5", "execute"));
                    Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
                }
                default -> { }
            }
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return out.stream().filter(x -> x.toLowerCase(Locale.ROOT).startsWith(prefix)).distinct().collect(Collectors.toList());
    }
}
