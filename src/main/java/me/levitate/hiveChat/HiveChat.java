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

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("unused")
public final class HiveChat {
    private static HiveChat instance;
    private final Plugin plugin;
    private final MessageParser messageParser;
    private final PlayerCache playerCache;
    private boolean papiEnabled = false;

    // Message queues using LinkedList to maintain insertion order
    private final Map<UUID, Queue<QueuedMessage>> playerMessageQueues = new ConcurrentHashMap<>();
    private final Map<String, Queue<QueuedMessage>> senderMessageQueues = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> playerProcessingFlags = new ConcurrentHashMap<>();
    private final Map<String, Boolean> senderProcessingFlags = new ConcurrentHashMap<>();

    // Class to hold a message future and its placeholders
    private static class QueuedMessage {
        final CompletableFuture<ParsedMessage> messageFuture;
        final Placeholder[] placeholders;

        QueuedMessage(CompletableFuture<ParsedMessage> messageFuture, Placeholder[] placeholders) {
            this.messageFuture = messageFuture;
            this.placeholders = placeholders;
        }
    }

    private HiveChat(Plugin plugin) {
        this.plugin = plugin;
        this.messageParser = new MessageParser(plugin);
        this.playerCache = new PlayerCache(plugin);

        // Schedule periodic cleanup
        Bukkit.getScheduler().runTaskTimer(plugin, this::performCleanup, 1200L, 1200L); // Every minute
    }

    /**
     * Perform periodic cleanup operations
     */
    private void performCleanup() {
        BossBarComponent.cleanupBars();
        playerCache.cleanup();

        // Clean up message queues for offline players
        Set<UUID> toRemove = new HashSet<>();
        for (UUID playerId : playerMessageQueues.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                toRemove.add(playerId);
            }
        }

        // Remove queues for offline players
        for (UUID playerId : toRemove) {
            playerMessageQueues.remove(playerId);
            playerProcessingFlags.remove(playerId);
        }
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
     * Add a message to player's queue and process it
     * @param player The player
     * @param messageFuture Future containing the parsed message
     * @param placeholders Placeholders to apply
     */
    private void queuePlayerMessage(Player player, CompletableFuture<ParsedMessage> messageFuture, Placeholder... placeholders) {
        if (player == null || !player.isOnline()) return;

        UUID playerId = player.getUniqueId();

        // Create a queued message with the future and placeholders
        QueuedMessage queuedMessage = new QueuedMessage(messageFuture, placeholders);

        // Get or create the queue
        Queue<QueuedMessage> queue = playerMessageQueues.computeIfAbsent(
                playerId, k -> new LinkedList<>());

        // Add the message to the queue
        queue.add(queuedMessage);

        // Start processing if not already processing
        if (!playerProcessingFlags.getOrDefault(playerId, false)) {
            playerProcessingFlags.put(playerId, true);
            processNextPlayerMessage(playerId);
        }
    }

    /**
     * Process the next message in a player's queue
     * @param playerId Player's UUID
     */
    private void processNextPlayerMessage(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            // Player went offline, clean up
            playerMessageQueues.remove(playerId);
            playerProcessingFlags.remove(playerId);
            return;
        }

        Queue<QueuedMessage> queue = playerMessageQueues.get(playerId);
        if (queue == null || queue.isEmpty()) {
            // Queue empty, stop processing
            playerProcessingFlags.put(playerId, false);
            return;
        }

        // Get the next queued message
        QueuedMessage queuedMessage = queue.poll();

        // When the message is parsed, send it and process the next one
        queuedMessage.messageFuture.thenAccept(message -> {
            if (player.isOnline()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    message.send(player, queuedMessage.placeholders);
                    processNextPlayerMessage(playerId);
                });
            } else {
                // Player went offline while waiting for parse
                playerMessageQueues.remove(playerId);
                playerProcessingFlags.remove(playerId);
            }
        }).exceptionally(ex -> {
            plugin.getLogger().warning("Error processing message: " + ex.getMessage());
            // Continue with next message even on error
            Bukkit.getScheduler().runTask(plugin, () ->
                    processNextPlayerMessage(playerId));
            return null;
        });
    }

    /**
     * Add a message to a command sender's queue and process it
     * @param sender Command sender
     * @param messageFuture Future containing the parsed message
     * @param placeholders Placeholders to apply
     */
    private void queueSenderMessage(CommandSender sender, CompletableFuture<ParsedMessage> messageFuture, Placeholder... placeholders) {
        if (sender == null) return;

        // For players, use the player queue
        if (sender instanceof Player player) {
            queuePlayerMessage(player, messageFuture, placeholders);
            return;
        }

        String senderId = sender.getName();

        // Create a queued message with the future and placeholders
        QueuedMessage queuedMessage = new QueuedMessage(messageFuture, placeholders);

        // Get or create the queue
        Queue<QueuedMessage> queue = senderMessageQueues.computeIfAbsent(
                senderId, k -> new LinkedList<>());

        // Add the message to the queue
        queue.add(queuedMessage);

        // Start processing if not already processing
        if (!senderProcessingFlags.getOrDefault(senderId, false)) {
            senderProcessingFlags.put(senderId, true);
            processNextSenderMessage(senderId, sender);
        }
    }

    /**
     * Process the next message in a sender's queue
     * @param senderId Sender's name
     * @param sender Command sender
     */
    private void processNextSenderMessage(String senderId, CommandSender sender) {
        Queue<QueuedMessage> queue = senderMessageQueues.get(senderId);
        if (queue == null || queue.isEmpty()) {
            // Queue empty, stop processing
            senderProcessingFlags.put(senderId, false);
            return;
        }

        // Get the next queued message
        QueuedMessage queuedMessage = queue.poll();

        // When the message is parsed, send it and process the next one
        queuedMessage.messageFuture.thenAccept(message -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                message.send(sender, queuedMessage.placeholders);
                processNextSenderMessage(senderId, sender);
            });
        }).exceptionally(ex -> {
            plugin.getLogger().warning("Error processing message: " + ex.getMessage());
            // Continue with next message even on error
            Bukkit.getScheduler().runTask(plugin, () ->
                    processNextSenderMessage(senderId, sender));
            return null;
        });
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

        // Queue the message for ordered processing
        CompletableFuture<ParsedMessage> messageFuture = instance.messageParser.parseAsync(message);
        instance.queuePlayerMessage(player, messageFuture, placeholders);
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

        // Queue the message for ordered processing
        CompletableFuture<ParsedMessage> messageFuture = instance.messageParser.parseAsync(message);
        instance.queueSenderMessage(sender, messageFuture, placeholders);
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
    public static void sendList(Player player, List<String> messages, Placeholder... placeholders) {
        checkInitialized();
        if (player == null || !player.isOnline() || messages == null || messages.isEmpty()) return;

        // Parse and queue each message in order
        for (String message : messages) {
            CompletableFuture<ParsedMessage> messageFuture = instance.messageParser.parseAsync(message);
            instance.queuePlayerMessage(player, messageFuture, placeholders);
        }
    }

    /**
     * Send a list of messages to a command sender in the exact order they appear in the list
     * @param sender The command sender to receive messages
     * @param messages The list of messages
     * @param placeholders Optional placeholders
     */
    public static void sendList(CommandSender sender, List<String> messages, Placeholder... placeholders) {
        checkInitialized();
        if (sender == null || messages == null || messages.isEmpty()) return;

        if (sender instanceof Player player) {
            sendList(player, messages, placeholders);
            return;
        }

        // Parse and queue each message in order
        for (String message : messages) {
            CompletableFuture<ParsedMessage> messageFuture = instance.messageParser.parseAsync(message);
            instance.queueSenderMessage(sender, messageFuture, placeholders);
        }
    }

    /**
     * Broadcast a message to all online players
     * @param message The message content
     * @param placeholders Optional placeholders
     */
    public static void broadcast(String message, Placeholder... placeholders) {
        checkInitialized();
        CompletableFuture<ParsedMessage> messageFuture = instance.messageParser.parseAsync(message);

        messageFuture.thenAccept(parsed -> {
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

        CompletableFuture<ParsedMessage> messageFuture = instance.messageParser.parseAsync(message);

        messageFuture.thenAccept(parsed -> {
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
    public static void broadcastList(List<String> messages, Placeholder... placeholders) {
        checkInitialized();
        if (messages == null || messages.isEmpty()) return;

        // Get all the players currently online
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());

        // Send the list to each player
        for (Player player : players) {
            sendList(player, messages, placeholders);
        }
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
}