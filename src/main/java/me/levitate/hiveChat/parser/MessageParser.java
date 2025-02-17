package me.levitate.hiveChat.parser;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import me.clip.placeholderapi.PlaceholderAPI;
import me.levitate.hiveChat.HiveChat;
import me.levitate.hiveChat.message.*;
import me.levitate.hiveChat.placeholder.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageParser {
    private static final Pattern SOUND_PATTERN = Pattern.compile("<sound:([A-Za-z0-9_]+):(\\d+(?:\\.\\d+)?):?(\\d+(?:\\.\\d+)?)?>");
    private static final Pattern ACTIONBAR_PATTERN = Pattern.compile("<actionbar(?::(\\d+))?>([^<]+)</actionbar>");
    private static final Pattern BOSSBAR_PATTERN = Pattern.compile("<bossbar:([^:]+):([^:]+):([^:>]+)(?::(\\d+))?>([^<]+)</bossbar>");
    private static final Pattern TITLE_PATTERN = Pattern.compile("<title(?::(\\d+):(\\d+):(\\d+))?>([^|<]+)(?:\\|([^<]+))?</title>");

    private final Cache<String, ParsedMessage> messageCache;
    private final Plugin plugin;

    public MessageParser() {
        this.plugin = HiveChat.getPlugin();
        this.messageCache = CacheBuilder.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .build();
    }

    public CompletableFuture<ParsedMessage> parseAsync(String message) {
        if (message == null || message.isEmpty()) {
            return CompletableFuture.completedFuture(new ParsedMessage());
        }

        // Check cache first
        ParsedMessage cached = messageCache.getIfPresent(message);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        // Parse asynchronously
        CompletableFuture<ParsedMessage> future = new CompletableFuture<>();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                ParsedMessage parsed = parseMessage(message);
                messageCache.put(message, parsed);
                future.complete(parsed);
            } catch (Exception e) {
                plugin.getLogger().warning("Error parsing message: " + e.getMessage());
                future.complete(new ParsedMessage());
            }
        });

        return future;
    }

    private ParsedMessage parseMessage(String message) {
        ParsedMessage parsed = new ParsedMessage();

        // Create a map to store all matches and their positions
        TreeMap<Integer, MessageComponent> componentMap = new TreeMap<>();

        // Process each pattern and add components to the map
        processSoundPattern(message, componentMap);
        processActionBarPattern(message, componentMap);
        processBossBarPattern(message, componentMap);
        processTitlePattern(message, componentMap);

        // Handle remaining text
        processRemainingText(message, componentMap);

        // Add components in order
        componentMap.values().forEach(parsed::addComponent);

        return parsed;
    }

    private void processSoundPattern(String message, TreeMap<Integer, MessageComponent> componentMap) {
        Matcher matcher = SOUND_PATTERN.matcher(message);
        while (matcher.find()) {
            try {
                Sound sound = Sound.valueOf(matcher.group(1).toUpperCase());
                float volume = matcher.group(2) != null ? Float.parseFloat(matcher.group(2)) : 1.0f;
                float pitch = matcher.group(3) != null ? Float.parseFloat(matcher.group(3)) : 1.0f;

                componentMap.put(matcher.start(), new MessageComponent()
                        .setSound(sound)
                        .setVolume(volume)
                        .setPitch(pitch));
            } catch (Exception e) {
                plugin.getLogger().warning("Invalid sound format: " + matcher.group(1));
            }
        }
    }

    private void processActionBarPattern(String message, TreeMap<Integer, MessageComponent> componentMap) {
        Matcher matcher = ACTIONBAR_PATTERN.matcher(message);
        while (matcher.find()) {
            try {
                String content = matcher.group(2);
                int duration = matcher.group(1) != null ? Integer.parseInt(matcher.group(1)) : 60;

                ActionBar actionBar = new ActionBar()
                        .setContent(content)
                        .setDuration(duration);

                componentMap.put(matcher.start(), new MessageComponent()
                        .setActionBar(actionBar));
            } catch (Exception e) {
                plugin.getLogger().warning("Invalid action bar format at: " + matcher.group());
            }
        }
    }

    private void processBossBarPattern(String message, TreeMap<Integer, MessageComponent> componentMap) {
        Matcher matcher = BOSSBAR_PATTERN.matcher(message);
        while (matcher.find()) {
            try {
                BarColor color = BarColor.valueOf(matcher.group(1).toUpperCase());
                BarStyle style = BarStyle.valueOf(matcher.group(2).toUpperCase());
                double progress = Double.parseDouble(matcher.group(3));
                int duration = matcher.group(4) != null ? Integer.parseInt(matcher.group(4)) : 600;
                String content = matcher.group(5);

                BossBarComponent bossBar = new BossBarComponent()
                        .setContent(content)
                        .setColor(color)
                        .setStyle(style)
                        .setProgress(progress)
                        .setDuration(duration);

                componentMap.put(matcher.start(), new MessageComponent()
                        .setBossBar(bossBar));
            } catch (Exception e) {
                plugin.getLogger().warning("Invalid boss bar format at: " + matcher.group());
            }
        }
    }

    private void processTitlePattern(String message, TreeMap<Integer, MessageComponent> componentMap) {
        Matcher matcher = TITLE_PATTERN.matcher(message);
        while (matcher.find()) {
            try {
                String title = matcher.group(4);
                String subtitle = matcher.group(5);

                int fadeIn = matcher.group(1) != null ? Integer.parseInt(matcher.group(1)) : 10;
                int stay = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 70;
                int fadeOut = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : 20;

                TitleComponent titleComponent = new TitleComponent()
                        .setTitle(title)
                        .setSubtitle(subtitle)
                        .setTimes(fadeIn, stay, fadeOut);

                componentMap.put(matcher.start(), new MessageComponent()
                        .setTitle(titleComponent));
            } catch (Exception e) {
                plugin.getLogger().warning("Invalid title format at: " + matcher.group());
            }
        }
    }

    private void processRemainingText(String message, TreeMap<Integer, MessageComponent> componentMap) {
        if (componentMap.isEmpty()) {
            if (!message.trim().isEmpty()) {
                componentMap.put(0, new MessageComponent().setContent(message.trim()));
            }
            return;
        }

        int lastEnd = 0;
        for (Map.Entry<Integer, MessageComponent> entry : componentMap.entrySet()) {
            int start = entry.getKey();
            if (start > lastEnd) {
                String text = message.substring(lastEnd, start).trim();
                if (!text.isEmpty()) {
                    componentMap.put(lastEnd, new MessageComponent().setContent(text));
                }
            }

            Matcher matcher = Pattern.compile("<[^>]+>").matcher(message);
            if (matcher.find(start)) {
                lastEnd = matcher.end();
            }
        }

        if (lastEnd < message.length()) {
            String text = message.substring(lastEnd).trim();
            if (!text.isEmpty()) {
                componentMap.put(lastEnd, new MessageComponent().setContent(text));
            }
        }
    }

    public String applyPlaceholders(String text, Player player, Placeholder... placeholders) {
        if (text == null) return "";

        String processed = text;
        for (Placeholder placeholder : placeholders) {
            processed = processed.replace(
                    "{" + placeholder.getKey() + "}",
                    placeholder.getValue()
            );
        }

        if (HiveChat.isPapiEnabled() && PlaceholderAPI.containsPlaceholders(processed)) {
            if (Bukkit.isPrimaryThread()) {
                processed = PlaceholderAPI.setPlaceholders(player, processed);
            } else {
                CompletableFuture<String> future = new CompletableFuture<>();
                String finalProcessed = processed;
                Bukkit.getScheduler().runTask(plugin, () ->
                        future.complete(PlaceholderAPI.setPlaceholders(player, finalProcessed)));
                try {
                    processed = future.get(100, TimeUnit.MILLISECONDS);
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to process PlaceholderAPI placeholders: " + e.getMessage());
                }
            }
        }

        return processed;
    }

    public void cacheMessage(String key, ParsedMessage message) {
        messageCache.put(key, message);
    }

    public ParsedMessage getCachedMessage(String key) {
        return messageCache.getIfPresent(key);
    }
}