package me.levitate.hiveChat.message;

import me.levitate.hiveChat.HiveChat;
import me.levitate.hiveChat.placeholder.Placeholder;
import me.levitate.hiveChat.util.ColorUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.WeakHashMap;

public class ActionBar {
    private final Map<Player, BukkitTask> activeTasks = new WeakHashMap<>();
    private String content;
    private int duration = 60; // Default 3 seconds (60 ticks)

    public void show(Player player, Placeholder... placeholders) {
        if (player == null || !player.isOnline()) return;

        // Cancel existing task if any
        BukkitTask existingTask = activeTasks.remove(player);
        if (existingTask != null) {
            existingTask.cancel();
        }

        // Process content
        String processed = HiveChat.getParser().applyPlaceholders(content, player, placeholders);
        Component component = ColorUtil.parseMessageFormats(processed);

        // Show action bar
        player.sendActionBar(component);

        // Schedule removal if duration > 0
        if (duration > 0) {
            BukkitTask task = Bukkit.getScheduler().runTaskLater(
                    HiveChat.getPlugin(),
                    () -> {
                        if (player.isOnline()) {
                            player.sendActionBar(Component.empty());
                        }
                        activeTasks.remove(player);
                    },
                    duration
            );
            activeTasks.put(player, task);
        }
    }

    // Getters and setters with fluent interface
    public ActionBar setContent(String content) {
        this.content = content;
        return this;
    }

    public ActionBar setDuration(int duration) {
        this.duration = duration;
        return this;
    }
}