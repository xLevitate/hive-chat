package me.levitate.hiveChat.message;

import me.levitate.hiveChat.HiveChat;
import me.levitate.hiveChat.placeholder.Placeholder;
import me.levitate.hiveChat.util.ColorUtil;
import me.levitate.hiveChat.util.ServerUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ActionBar {
    private static final Map<Player, Boolean> activeTasks = new ConcurrentHashMap<>();
    private String content;
    private int duration = 60; // Default 3 seconds (60 ticks)

    public void show(Player player, Placeholder... placeholders) {
        if (player == null || !player.isOnline()) return;

        // Cancel any existing task
        removeActionBar(player);

        // Process content with placeholders
        String processed = HiveChat.getParser().applyPlaceholders(content, player, placeholders);

        // Parse the colors and formats properly
        Component component = ColorUtil.parseMessageFormats(processed);

        // Show the action bar
        player.sendActionBar(component);

        // Schedule removal if duration > 0
        if (duration > 0) {
            // Mark this player as having an active action bar
            activeTasks.put(player, Boolean.TRUE);
            
            // Replace Bukkit scheduler with ServerUtil
            ServerUtil.runAtEntity(player, p -> {
                ServerUtil.runTaskLater(() -> {
                    if (p.isOnline()) {
                        p.sendActionBar(Component.empty());
                    }
                    activeTasks.remove(p);
                }, duration);
            });
        }
    }

    private void removeActionBar(Player player) {
        if (player == null) return;
        
        if (activeTasks.remove(player) != null && player.isOnline()) {
            player.sendActionBar(Component.empty());
        }
    }

    public ActionBar setContent(String content) {
        this.content = content;
        return this;
    }

    public ActionBar setDuration(int duration) {
        this.duration = duration;
        return this;
    }
}