package me.levitate.hiveChat;

import lombok.Getter;
import me.levitate.hiveChat.cache.PlayerCache;
import me.levitate.hiveChat.chain.MessageChain;
import me.levitate.hiveChat.message.BossBarComponent;
import me.levitate.hiveChat.message.MessageRegistry;
import me.levitate.hiveChat.message.ParsedMessage;
import me.levitate.hiveChat.parser.MessageParser;
import me.levitate.hiveChat.placeholder.Placeholder;
import me.levitate.hiveChat.placeholder.UniversalPlaceholderManager;
import me.levitate.hiveChat.util.ServerUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@SuppressWarnings("unused")
public final class HiveChat {
    private static HiveChat instance;
    @Getter private final Plugin plugin;
    @Getter private final MessageParser messageParser;
    private final PlayerCache playerCache;
    private final MessageRegistry messageRegistry;

    private final Map<UUID, Queue<QueuedMessage>> playerMessageQueues = new ConcurrentHashMap<>();
    private final Map<String, Queue<QueuedMessage>> senderMessageQueues = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> playerProcessingFlags = new ConcurrentHashMap<>();
    private final Map<String, Boolean> senderProcessingFlags = new ConcurrentHashMap<>();
    private boolean papiEnabled = false;

    private HiveChat(Plugin plugin) {
        this.plugin = plugin;
        this.messageParser = new MessageParser(plugin);
        this.playerCache = new PlayerCache(plugin);
        this.messageRegistry = new MessageRegistry();
        
        // Initialize ServerUtil with our plugin instance
        ServerUtil.init(plugin);

        ServerUtil.runTaskTimer(this::performCleanup, 1200L, 1200L); // Every minute
        
        // Register shutdown hook to clean up resources
        plugin.getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onPluginDisable(org.bukkit.event.server.PluginDisableEvent event) {
                if (event.getPlugin().equals(plugin)) {
                    ServerUtil.cancelAllTasks();
                }
            }
        }, plugin);
    }

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

    public static MessageChain createChain() {
        checkInitialized();
        return new MessageChain();
    }

    public static void send(Player player, String message, Placeholder... placeholders) {
        checkInitialized();
        if (player == null || !player.isOnline()) return;

        CompletableFuture<ParsedMessage> messageFuture = instance.messageParser.parseAsync(message);
        instance.queuePlayerMessage(player, messageFuture, placeholders);
    }

    public static void send(CommandSender sender, String message, Placeholder... placeholders) {
        checkInitialized();
        if (sender == null) return;

        CompletableFuture<ParsedMessage> messageFuture = instance.messageParser.parseAsync(message);
        instance.queueSenderMessage(sender, messageFuture, placeholders);
    }

    public static void send(UUID playerId, String message, Placeholder... placeholders) {
        checkInitialized();
        Player player = instance.playerCache.getPlayer(playerId);
        if (player != null) {
            send(player, message, placeholders);
        }
    }

    public static void sendList(Player player, List<String> messages, Placeholder... placeholders) {
        checkInitialized();
        if (player == null || !player.isOnline() || messages == null || messages.isEmpty()) return;

        for (String message : messages) {
            CompletableFuture<ParsedMessage> messageFuture = instance.messageParser.parseAsync(message);
            instance.queuePlayerMessage(player, messageFuture, placeholders);
        }
    }

    public static void sendList(CommandSender sender, List<String> messages, Placeholder... placeholders) {
        checkInitialized();
        if (sender == null || messages == null || messages.isEmpty()) return;

        if (sender instanceof Player player) {
            sendList(player, messages, placeholders);
            return;
        }

        for (String message : messages) {
            CompletableFuture<ParsedMessage> messageFuture = instance.messageParser.parseAsync(message);
            instance.queueSenderMessage(sender, messageFuture, placeholders);
        }
    }

    public static void broadcast(String message, Placeholder... placeholders) {
        checkInitialized();
        CompletableFuture<ParsedMessage> messageFuture = instance.messageParser.parseAsync(message);

        messageFuture.thenAccept(parsed -> ServerUtil.runTask(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                parsed.send(player, placeholders);
            }
        }));
    }

    public static void broadcast(int radius, Location center, String message, Placeholder... placeholders) {
        checkInitialized();
        if (center == null || center.getWorld() == null) return;

        CompletableFuture<ParsedMessage> messageFuture = instance.messageParser.parseAsync(message);

        messageFuture.thenAccept(parsed -> {
            List<Player> nearbyPlayers = center.getWorld().getPlayers().stream()
                    .filter(p -> p.getLocation().distance(center) <= radius)
                    .toList();
                    
            for (Player player : nearbyPlayers) {
                ServerUtil.runAtEntity(player, p -> 
                    parsed.send(p, placeholders));
            }
        });
    }

    public static void broadcastList(List<String> messages, Placeholder... placeholders) {
        checkInitialized();
        if (messages == null || messages.isEmpty()) return;

        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());

        for (Player player : players) {
            sendList(player, messages, placeholders);
        }
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
            ServerUtil.runAtEntity(player, p -> 
                saved.send(p, placeholders));
        }
    }
    
    public static UniversalPlaceholderManager addUniversalPlaceholder(String key, String value) {
        checkInitialized();
        return UniversalPlaceholderManager.getInstance().addStaticPlaceholder(key, value);
    }
    
    public static UniversalPlaceholderManager addDynamicPlaceholder(String key, Function<Player, String> valueSupplier) {
        checkInitialized();
        return UniversalPlaceholderManager.getInstance().addDynamicPlaceholder(key, valueSupplier);
    }
    
    public static UniversalPlaceholderManager removeUniversalPlaceholder(String key) {
        checkInitialized();
        return UniversalPlaceholderManager.getInstance().removePlaceholder(key);
    }
    
    public static UniversalPlaceholderManager clearUniversalPlaceholders() {
        checkInitialized();
        return UniversalPlaceholderManager.getInstance().clearPlaceholders();
    }
    
    public static MessageRegistry getMessageRegistry() {
        checkInitialized();
        return instance.messageRegistry;
    }
    
    public static MessageRegistry registerMessage(String key, String message) {
        checkInitialized();
        return instance.messageRegistry.register(key, message);
    }
    
    public static void sendRegistered(String key, Player player, Placeholder... placeholders) {
        checkInitialized();
        instance.messageRegistry.send(key, player, placeholders);
    }
    
    public static void sendRegistered(String key, CommandSender sender, Placeholder... placeholders) {
        checkInitialized();
        instance.messageRegistry.send(key, sender, placeholders);
    }
    
    public static boolean isFolia() {
        return ServerUtil.isFolia();
    }

    private static void checkInitialized() {
        if (instance == null) {
            throw new IllegalStateException("HiveChat has not been initialized! Call HiveChat.init(plugin) first!");
        }
    }

    public static boolean isPapiEnabled() {
        return instance.papiEnabled;
    }

    public static MessageParser getParser() {
        return instance.messageParser;
    }

    private void performCleanup() {
        // Clean up player message queues
        for (Iterator<Map.Entry<UUID, Queue<QueuedMessage>>> it = playerMessageQueues.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Queue<QueuedMessage>> entry = it.next();
            if (entry.getValue().isEmpty()) {
                it.remove();
                playerProcessingFlags.remove(entry.getKey());
            }
        }

        // Clean up sender message queues
        for (Iterator<Map.Entry<String, Queue<QueuedMessage>>> it = senderMessageQueues.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Queue<QueuedMessage>> entry = it.next();
            if (entry.getValue().isEmpty()) {
                it.remove();
                senderProcessingFlags.remove(entry.getKey());
            }
        }

        // Reset stuck processing flags
        for (Map.Entry<UUID, Boolean> entry : playerProcessingFlags.entrySet()) {
            if (entry.getValue() && playerMessageQueues.containsKey(entry.getKey()) && !playerMessageQueues.get(entry.getKey()).isEmpty()) {
                entry.setValue(false);
                processNextPlayerMessage(entry.getKey());
            }
        }

        for (Map.Entry<String, Boolean> entry : senderProcessingFlags.entrySet()) {
            if (entry.getValue() && senderMessageQueues.containsKey(entry.getKey()) && !senderMessageQueues.get(entry.getKey()).isEmpty()) {
                entry.setValue(false);
                CommandSender sender = Bukkit.getConsoleSender(); // Default fallback
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().equals(entry.getKey())) {
                        sender = player;
                        break;
                    }
                }
                processNextSenderMessage(entry.getKey(), sender);
            }
        }
    }

    private void queuePlayerMessage(Player player, CompletableFuture<ParsedMessage> messageFuture, Placeholder... placeholders) {
        UUID playerId = player.getUniqueId();
        
        Queue<QueuedMessage> queue = playerMessageQueues.computeIfAbsent(playerId, k -> new LinkedList<>());
        queue.add(new QueuedMessage(messageFuture, placeholders));
        
        if (!playerProcessingFlags.getOrDefault(playerId, false)) {
            processNextPlayerMessage(playerId);
        }
    }

    private void processNextPlayerMessage(UUID playerId) {
        if (!playerMessageQueues.containsKey(playerId)) {
            return;
        }

        Queue<QueuedMessage> queue = playerMessageQueues.get(playerId);
        if (queue.isEmpty()) {
            playerProcessingFlags.put(playerId, false);
            return;
        }

        playerProcessingFlags.put(playerId, true);
        QueuedMessage queuedMessage = queue.poll();

        queuedMessage.messageFuture.thenAccept(parsedMessage -> {
            Player player = playerCache.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                ServerUtil.runAtEntity(player, p -> {
                    parsedMessage.send(p, queuedMessage.placeholders);
                    
                    ServerUtil.runTask(() -> {
                        playerProcessingFlags.put(playerId, false);
                        processNextPlayerMessage(playerId);
                    });
                });
            } else {
                playerProcessingFlags.put(playerId, false);
                processNextPlayerMessage(playerId);
            }
        }).exceptionally(ex -> {
            playerProcessingFlags.put(playerId, false);
            processNextPlayerMessage(playerId);
            return null;
        });
    }

    private void queueSenderMessage(CommandSender sender, CompletableFuture<ParsedMessage> messageFuture, Placeholder... placeholders) {
        String senderId = sender instanceof Player player ? player.getUniqueId().toString() : sender.getName();
        
        Queue<QueuedMessage> queue = senderMessageQueues.computeIfAbsent(senderId, k -> new LinkedList<>());
        queue.add(new QueuedMessage(messageFuture, placeholders));
        
        if (!senderProcessingFlags.getOrDefault(senderId, false)) {
            processNextSenderMessage(senderId, sender);
        }
    }

    private void processNextSenderMessage(String senderId, CommandSender sender) {
        if (!senderMessageQueues.containsKey(senderId)) {
            return;
        }

        Queue<QueuedMessage> queue = senderMessageQueues.get(senderId);
        if (queue.isEmpty()) {
            senderProcessingFlags.put(senderId, false);
            return;
        }

        senderProcessingFlags.put(senderId, true);
        QueuedMessage queuedMessage = queue.poll();

        queuedMessage.messageFuture.thenAccept(parsedMessage -> {
            if (sender instanceof Player player) {
                ServerUtil.runAtEntity(player, p -> {
                    parsedMessage.send(p, queuedMessage.placeholders);
                    
                    ServerUtil.runTask(() -> {
                        senderProcessingFlags.put(senderId, false);
                        processNextSenderMessage(senderId, sender);
                    });
                });
            } else {
                ServerUtil.runTask(() -> {
                    parsedMessage.send(sender, queuedMessage.placeholders);
                    senderProcessingFlags.put(senderId, false);
                    processNextSenderMessage(senderId, sender);
                });
            }
        }).exceptionally(ex -> {
            senderProcessingFlags.put(senderId, false);
            processNextSenderMessage(senderId, sender);
            return null;
        });
    }

    private static class QueuedMessage {
        final CompletableFuture<ParsedMessage> messageFuture;
        final Placeholder[] placeholders;

        QueuedMessage(CompletableFuture<ParsedMessage> messageFuture, Placeholder[] placeholders) {
            this.messageFuture = messageFuture;
            this.placeholders = placeholders;
        }
    }
}