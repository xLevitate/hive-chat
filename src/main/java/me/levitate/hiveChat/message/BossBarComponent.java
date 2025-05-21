package me.levitate.hiveChat.message;

import me.levitate.hiveChat.HiveChat;
import me.levitate.hiveChat.placeholder.Placeholder;
import me.levitate.hiveChat.util.ColorUtil;
import me.levitate.hiveChat.util.ServerUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BossBarComponent {
    private static final Map<UUID, BossBar> activeBars = new ConcurrentHashMap<>();
    private static final Map<UUID, Object> activeTaskFlags = new ConcurrentHashMap<>();
    private String content;
    private BarColor color = BarColor.WHITE;
    private BarStyle style = BarStyle.SOLID;
    private double progress = 1.0;
    private int duration = 600; // Default 30 seconds (600 ticks)

    /**
     * Static method to clean all boss bars for players who are no longer online
     */
    public static void cleanupBars() {
        // Create a copy of the keys to avoid concurrent modification
        new HashSet<>(activeBars.keySet()).forEach(uuid -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                BossBar bar = activeBars.remove(uuid);
                if (bar != null) {
                    bar.removeAll();
                }
                activeTaskFlags.remove(uuid);
            }
        });
    }

    public void show(Player player, Placeholder... placeholders) {
        if (player == null || !player.isOnline()) return;

        // Remove existing boss bar if any
        removeBossBar(player);

        // Process content with placeholders
        String processed = HiveChat.getParser().applyPlaceholders(content, player, placeholders);

        // Parse the colors and formats properly
        Component component = ColorUtil.parseMessageFormats(processed);

        // Convert the component to a string suitable for Bukkit boss bar
        // We use the legacy serializer to ensure compatibility with the Bukkit boss bar API
        String coloredTitle = LegacyComponentSerializer.legacySection().serialize(component);

        // Create new boss bar
        BossBar bossBar = Bukkit.createBossBar(coloredTitle, color, style);
        bossBar.setProgress(progress);
        bossBar.addPlayer(player);

        UUID playerUUID = player.getUniqueId();
        activeBars.put(playerUUID, bossBar);

        // Schedule removal if duration > 0
        if (duration > 0) {
            activeTaskFlags.put(playerUUID, Boolean.TRUE);
            
            // We're just tracking if there's a task scheduled, not the actual task reference
            ServerUtil.runTaskLater(() -> {
                if (activeTaskFlags.remove(playerUUID) != null) {
                    removeBossBar(player);
                }
            }, duration);
        }
    }

    private void removeBossBar(Player player) {
        if (player == null) return;

        UUID playerUUID = player.getUniqueId();
        BossBar existing = activeBars.remove(playerUUID);
        if (existing != null) {
            existing.removeAll();
        }
        
        // No need to cancel tasks, just remove our tracking flag
        activeTaskFlags.remove(playerUUID);
    }

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