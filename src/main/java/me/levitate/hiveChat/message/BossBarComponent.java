package me.levitate.hiveChat.message;

import me.levitate.hiveChat.HiveChat;
import me.levitate.hiveChat.placeholder.Placeholder;
import me.levitate.hiveChat.util.ColorUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.WeakHashMap;

public class BossBarComponent {
    private String content;
    private BarColor color = BarColor.WHITE;
    private BarStyle style = BarStyle.SOLID;
    private double progress = 1.0;
    private int duration = 600; // Default 30 seconds (600 ticks)

    private final Map<Player, BossBar> activeBars = new WeakHashMap<>();
    private final Map<Player, BukkitTask> activeTasks = new WeakHashMap<>();

    public void show(Player player, Placeholder... placeholders) {
        if (player == null || !player.isOnline()) return;

        // Remove existing boss bar if any
        removeBossBar(player);

        // Process content
        String processed = HiveChat.getParser().applyPlaceholders(content, player, placeholders);
        Component component = ColorUtil.parseMessageFormats(processed);

        // Create new boss bar
        BossBar bossBar = Bukkit.createBossBar(
                LegacyComponentSerializer.legacyAmpersand().serialize(component),
                color,
                style
        );

        bossBar.setProgress(progress);
        bossBar.addPlayer(player);

        activeBars.put(player, bossBar);

        // Schedule removal if duration > 0
        if (duration > 0) {
            BukkitTask task = Bukkit.getScheduler().runTaskLater(
                    HiveChat.getPlugin(),
                    () -> removeBossBar(player),
                    duration
            );
            activeTasks.put(player, task);
        }
    }

    private void removeBossBar(Player player) {
        BossBar existing = activeBars.remove(player);
        if (existing != null) {
            existing.removeAll();
        }

        BukkitTask existingTask = activeTasks.remove(player);
        if (existingTask != null) {
            existingTask.cancel();
        }
    }

    // Getters and setters with fluent interface
    public BossBarComponent setContent(String content) {
        this.content = content;
        return this;
    }

    public BossBarComponent setColor(BarColor color) {
        this.color = color;
        return this;
    }

    public BossBarComponent setStyle(BarStyle style) {
        this.style = style;
        return this;
    }

    public BossBarComponent setProgress(double progress) {
        this.progress = progress;
        return this;
    }

    public BossBarComponent setDuration(int duration) {
        this.duration = duration;
        return this;
    }
}