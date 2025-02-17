package me.levitate.hiveChat;

import me.levitate.hiveChat.cache.PlayerCache;
import me.levitate.hiveChat.chain.MessageChain;
import me.levitate.hiveChat.message.ParsedMessage;
import me.levitate.hiveChat.parser.MessageParser;
import me.levitate.hiveChat.placeholder.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

public final class HiveChat {
    private static HiveChat instance;
    private final Plugin plugin;
    private final MessageParser messageParser;
    private final PlayerCache playerCache;
    private boolean papiEnabled = false;

    private HiveChat(Plugin plugin) {
        this.plugin = plugin;
        this.messageParser = new MessageParser();
        this.playerCache = new PlayerCache(plugin);
    }

    public static void init(Plugin plugin) {
        if (instance == null) {
            instance = new HiveChat(plugin);
        }
    }

    public static void enablePAPI() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            instance.papiEnabled = true;
        } else {
            instance.plugin.getLogger().warning("Attempted to enable PlaceholderAPI support but PlaceholderAPI is not installed!");
        }
    }

    public static MessageChain createChain() {
        checkInitialized();
        return new MessageChain();
    }

    public static void send(Player player, String message, Placeholder... placeholders) {
        checkInitialized();
        if (player == null || !player.isOnline()) return;

        instance.messageParser.parseAsync(message).thenAccept(parsed -> {
            if (player.isOnline()) {
                Bukkit.getScheduler().runTask(instance.plugin, () ->
                        parsed.send(player, placeholders));
            }
        });
    }

    public static void send(UUID playerId, String message, Placeholder... placeholders) {
        checkInitialized();
        Player player = instance.playerCache.getPlayer(playerId);
        if (player != null) {
            send(player, message, placeholders);
        }
    }

    public static void broadcast(String message, Placeholder... placeholders) {
        checkInitialized();
        instance.messageParser.parseAsync(message).thenAccept(parsed -> {
            Bukkit.getScheduler().runTask(instance.plugin, () -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    parsed.send(player, placeholders);
                }
            });
        });
    }

    public static void broadcast(int radius, Location center, String message, Placeholder... placeholders) {
        checkInitialized();
        if (center == null || center.getWorld() == null) return;

        instance.messageParser.parseAsync(message).thenAccept(parsed -> {
            Bukkit.getScheduler().runTask(instance.plugin, () -> {
                center.getWorld().getPlayers().stream()
                        .filter(p -> p.getLocation().distance(center) <= radius)
                        .forEach(player -> parsed.send(player, placeholders));
            });
        });
    }

    public static void saveMessage(String key, String message) {
        checkInitialized();
        instance.messageParser.parseAsync(message).thenAccept(parsed ->
                instance.messageParser.cacheMessage(key, parsed));
    }

    public static void sendSaved(String key, Player player, Placeholder... placeholders) {
        checkInitialized();
        ParsedMessage saved = instance.messageParser.getCachedMessage(key);
        if (saved != null && player != null && player.isOnline()) {
            Bukkit.getScheduler().runTask(instance.plugin, () ->
                    saved.send(player, placeholders));
        }
    }

    private static void checkInitialized() {
        if (instance == null) {
            throw new IllegalStateException("HiveChat has not been initialized! Call HiveChat.init(plugin) first!");
        }
    }

    // Internal getters
    public static Plugin getPlugin() {
        return instance.plugin;
    }

    public static boolean isPapiEnabled() {
        return instance.papiEnabled;
    }

    public static MessageParser getParser() {
        return instance.messageParser;
    }
}