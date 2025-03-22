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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageParser {
    private static final Pattern SOUND_PATTERN = Pattern.compile("<sound:([A-Za-z0-9_]+)(?::(\\d+(?:\\.\\d+)?):?(\\d+(?:\\.\\d+)?)?)>");
    private static final Pattern ACTIONBAR_PATTERN = Pattern.compile("<actionbar(?::(\\d+))?>(.*?)</actionbar>", Pattern.DOTALL);
    private static final Pattern BOSSBAR_PATTERN = Pattern.compile("<bossbar:([^:]+):([^:]+):([^:>]+)(?::(\\d+))?>(.*?)</bossbar>", Pattern.DOTALL);
    private static final Pattern TITLE_PATTERN = Pattern.compile("<title(?::(\\d+):(\\d+):(\\d+))?>(.*?)\\|(.*?)</title>", Pattern.DOTALL);

    private final Cache<String, ParsedMessage> messageCache;
    private final Plugin plugin;

    public MessageParser(Plugin plugin) {
        this.plugin = plugin;
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
        StringBuilder remainingText = new StringBuilder(message);

        // Process titles first
        Matcher titleMatcher = TITLE_PATTERN.matcher(remainingText);
        while (titleMatcher.find()) {
            try {
                String mainTitle = titleMatcher.group(4).trim();
                String subtitle = titleMatcher.group(5).trim();

                int fadeIn = titleMatcher.group(1) != null ?
                        Integer.parseInt(titleMatcher.group(1)) : 10;
                int stay = titleMatcher.group(2) != null ?
                        Integer.parseInt(titleMatcher.group(2)) : 70;
                int fadeOut = titleMatcher.group(3) != null ?
                        Integer.parseInt(titleMatcher.group(3)) : 20;

                TitleComponent titleComponent = new TitleComponent()
                        .setTitle(mainTitle)
                        .setSubtitle(subtitle)
                        .setTimes(fadeIn, stay, fadeOut);

                parsed.addComponent(new MessageComponent()
                        .setTitle(titleComponent));

                // Remove the entire title tag and its content
                int start = titleMatcher.start();
                int end = titleMatcher.end();
                remainingText.replace(start, end, "");
                // Adjust matcher region after removal
                titleMatcher.region(0, remainingText.length());
            } catch (Exception e) {
                plugin.getLogger().warning("Invalid title format at: " + titleMatcher.group());
            }
        }

        // Process sounds
        Matcher soundMatcher = SOUND_PATTERN.matcher(remainingText);
        while (soundMatcher.find()) {
            try {
                Sound sound = Sound.valueOf(soundMatcher.group(1).toUpperCase());
                float volume = soundMatcher.group(2) != null ?
                        Float.parseFloat(soundMatcher.group(2)) : 1.0f;
                float pitch = soundMatcher.group(3) != null ?
                        Float.parseFloat(soundMatcher.group(3)) : 1.0f;

                parsed.addComponent(new MessageComponent()
                        .setSound(sound)
                        .setVolume(volume)
                        .setPitch(pitch));

                // Remove the sound tag
                int start = soundMatcher.start();
                int end = soundMatcher.end();
                remainingText.replace(start, end, "");
                soundMatcher.region(0, remainingText.length());
            } catch (Exception e) {
                plugin.getLogger().warning("Invalid sound format: " + soundMatcher.group(1));
            }
        }

        // Process action bars with similar pattern
        Matcher actionBarMatcher = ACTIONBAR_PATTERN.matcher(remainingText);
        while (actionBarMatcher.find()) {
            try {
                String content = actionBarMatcher.group(2);
                int duration = actionBarMatcher.group(1) != null ?
                        Integer.parseInt(actionBarMatcher.group(1)) : 60;

                ActionBar actionBar = new ActionBar()
                        .setContent(content)
                        .setDuration(duration);

                parsed.addComponent(new MessageComponent()
                        .setActionBar(actionBar));

                // Remove the action bar tag and content
                int start = actionBarMatcher.start();
                int end = actionBarMatcher.end();
                remainingText.replace(start, end, "");
                actionBarMatcher.region(0, remainingText.length());
            } catch (Exception e) {
                plugin.getLogger().warning("Invalid action bar format at: " + actionBarMatcher.group());
            }
        }

        // Process boss bars with similar pattern
        Matcher bossBarMatcher = BOSSBAR_PATTERN.matcher(remainingText);
        while (bossBarMatcher.find()) {
            try {
                BarColor color = BarColor.valueOf(bossBarMatcher.group(1).toUpperCase());
                BarStyle style = BarStyle.valueOf(bossBarMatcher.group(2).toUpperCase());
                double progress = Double.parseDouble(bossBarMatcher.group(3));
                int duration = bossBarMatcher.group(4) != null ?
                        Integer.parseInt(bossBarMatcher.group(4)) : 600;
                String content = bossBarMatcher.group(5);

                BossBarComponent bossBar = new BossBarComponent()
                        .setContent(content)
                        .setColor(color)
                        .setStyle(style)
                        .setProgress(progress)
                        .setDuration(duration);

                parsed.addComponent(new MessageComponent()
                        .setBossBar(bossBar));

                // Remove the boss bar tag and content
                int start = bossBarMatcher.start();
                int end = bossBarMatcher.end();
                remainingText.replace(start, end, "");
                bossBarMatcher.region(0, remainingText.length());
            } catch (Exception e) {
                plugin.getLogger().warning("Invalid boss bar format at: " + bossBarMatcher.group());
            }
        }

        // Add remaining text as content if any
        String remaining = remainingText.toString().trim();
        if (!remaining.isEmpty()) {
            parsed.addComponent(new MessageComponent().setContent(remaining));
        }

        return parsed;
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