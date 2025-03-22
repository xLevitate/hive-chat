package me.levitate.hiveChat;

import me.levitate.hiveChat.cache.PlayerCache;
import me.levitate.hiveChat.chain.MessageChain;
import me.levitate.hiveChat.message.BossBarComponent;
import me.levitate.hiveChat.message.ParsedMessage;
import me.levitate.hiveChat.parser.MessageParser;
import me.levitate.hiveChat.placeholder.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@SuppressWarnings("unused")
public final class HiveChat {
    private static HiveChat instance;
    private final Plugin plugin;
    private final MessageParser messageParser;
    private final PlayerCache playerCache;
    private boolean papiEnabled = false;

    private HiveChat(Plugin plugin) {
        this.plugin = plugin;
        this.messageParser = new MessageParser(plugin);
        this.playerCache = new PlayerCache(plugin);

        // Schedule periodic cleanup
        Bukkit.getScheduler().runTaskTimer(plugin, this::performCleanup, 1200L, 1200L); // Every minute
    }

    /**
     * Initialize the HiveChat system
     * @param plugin The plugin instance
     */
    public static void init(Plugin plugin) {
        if (instance == null) {
            instance = new HiveChat(plugin);
        }

        enablePAPI();
    }

    private static void enablePAPI() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            instance.papiEnabled = true;
        }
    }

    /**
     * Create a new message chain
     * @return A new MessageChain instance
     */
    public static MessageChain createChain() {
        checkInitialized();
        return new MessageChain();
    }

    /**
     * Send a message to a player
     * @param player The player to send the message to
     * @param message The message content
     * @param placeholders Optional placeholders
     */
    public static void send(Player player, String message, Placeholder... placeholders) {
        checkInitialized();
        if (player == null || !player.isOnline()) return;

        // Parse message asynchronously
        instance.messageParser.parseAsync(message).thenAccept(parsed -> {
            if (player.isOnline()) {
                // Send on the main thread
                Bukkit.getScheduler().runTask(instance.plugin, () ->
                        parsed.send(player, placeholders));
            }
        });
    }

    /**
     * Send a message to a command sender
     * @param sender The command sender
     * @param message The message content
     * @param placeholders Optional placeholders
     */
    public static void send(CommandSender sender, String message, Placeholder... placeholders) {
        checkInitialized();
        if (sender == null) return;

        instance.messageParser.parseAsync(message).thenAccept(parsed -> {
            Bukkit.getScheduler().runTask(instance.plugin, () ->
                    parsed.send(sender, placeholders));
        });
    }

    /**
     * Send a message to a player by UUID
     * @param playerId The player UUID
     * @param message The message content
     * @param placeholders Optional placeholders
     */
    public static void send(UUID playerId, String message, Placeholder... placeholders) {
        checkInitialized();
        Player player = instance.playerCache.getPlayer(playerId);
        if (player != null) {
            send(player, message, placeholders);
        }
    }

    /**
     * Send a list of messages to a player in the exact order they appear in the list
     * @param player The player to send messages to
     * @param messages The list of messages
     * @param placeholders Optional placeholders
     */
    public static void sendOrdered(Player player, List<String> messages, Placeholder... placeholders) {
        checkInitialized();
        if (player == null || !player.isOnline() || messages == null || messages.isEmpty()) return;

        // Create a future for each message to be parsed
        List<CompletableFuture<ParsedMessage>> futures = messages.stream()
                .map(msg -> instance.messageParser.parseAsync(msg))
                .toList();

        // Wait for all messages to be parsed then send them in order
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenAccept(v -> {
                    if (player.isOnline()) {
                        Bukkit.getScheduler().runTask(instance.plugin, () -> {
                            for (CompletableFuture<ParsedMessage> future : futures) {
                                try {
                                    ParsedMessage parsed = future.get();
                                    parsed.send(player, placeholders);
                                } catch (Exception e) {
                                    instance.plugin.getLogger().warning("Error sending message: " + e.getMessage());
                                }
                            }
                        });
                    }
                });
    }

    /**
     * Send a list of messages to a command sender in the exact order they appear in the list
     * @param sender The command sender to receive messages
     * @param messages The list of messages
     * @param placeholders Optional placeholders
     */
    public static void sendOrdered(CommandSender sender, List<String> messages, Placeholder... placeholders) {
        checkInitialized();
        if (sender == null || messages == null || messages.isEmpty()) return;

        // Create a future for each message to be parsed
        List<CompletableFuture<ParsedMessage>> futures = messages.stream()
                .map(msg -> instance.messageParser.parseAsync(msg))
                .toList();

        // Wait for all messages to be parsed then send them in order
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenAccept(v -> {
                    Bukkit.getScheduler().runTask(instance.plugin, () -> {
                        for (CompletableFuture<ParsedMessage> future : futures) {
                            try {
                                ParsedMessage parsed = future.get();
                                parsed.send(sender, placeholders);
                            } catch (Exception e) {
                                instance.plugin.getLogger().warning("Error sending message: " + e.getMessage());
                            }
                        }
                    });
                });
    }

    /**
     * Send a list of messages to a player identified by UUID in the exact order they appear in the list
     * @param playerId The player's UUID
     * @param messages The list of messages
     * @param placeholders Optional placeholders
     */
    public static void sendOrdered(UUID playerId, List<String> messages, Placeholder... placeholders) {
        checkInitialized();
        Player player = instance.playerCache.getPlayer(playerId);
        if (player != null) {
            sendOrdered(player, messages, placeholders);
        }
    }

    /**
     * Broadcast a message to all online players
     * @param message The message content
     * @param placeholders Optional placeholders
     */
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

    /**
     * Broadcast a message to players within a radius
     * @param radius The radius in blocks
     * @param center The center location
     * @param message The message content
     * @param placeholders Optional placeholders
     */
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

    /**
     * Broadcast a list of messages to all online players in the exact order they appear in the list
     * @param messages The list of messages
     * @param placeholders Optional placeholders
     */
    public static void broadcastOrdered(List<String> messages, Placeholder... placeholders) {
        checkInitialized();
        if (messages == null || messages.isEmpty()) return;

        // Parse all messages asynchronously
        List<CompletableFuture<ParsedMessage>> futures = messages.stream()
                .map(msg -> instance.messageParser.parseAsync(msg))
                .toList();

        // Wait for all to complete then send in order
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenAccept(v -> {
                    Bukkit.getScheduler().runTask(instance.plugin, () -> {
                        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
                        for (CompletableFuture<ParsedMessage> future : futures) {
                            try {
                                ParsedMessage parsed = future.get();
                                for (Player player : players) {
                                    parsed.send(player, placeholders);
                                }
                            } catch (Exception e) {
                                instance.plugin.getLogger().warning("Error broadcasting message: " + e.getMessage());
                            }
                        }
                    });
                });
    }

    /**
     * Save a parsed message for later use
     * @param key The key to identify the message
     * @param message The message content
     */
    public static void saveMessage(String key, String message) {
        checkInitialized();
        instance.messageParser.parseAsync(message).thenAccept(parsed ->
                instance.messageParser.cacheMessage(key, parsed));
    }

    /**
     * Send a previously saved message
     * @param key The message key
     * @param player The player to send to
     * @param placeholders Optional placeholders
     */
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

    /**
     * Perform periodic cleanup operations
     */
    private void performCleanup() {
        BossBarComponent.cleanupBars();
        playerCache.cleanup();
    }
}