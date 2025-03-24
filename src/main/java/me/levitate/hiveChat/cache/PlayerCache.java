package me.levitate.hiveChat.cache;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerCache {
    private final Map<UUID, WeakReference<Player>> playerCache = new ConcurrentHashMap<>();
    private final Plugin plugin;

    public PlayerCache(Plugin plugin) {
        this.plugin = plugin;
        registerListeners();
    }

    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(new PlayerCacheListener(), plugin);
    }

    public Player getPlayer(UUID playerId) {
        if (playerId == null) return null;

        WeakReference<Player> ref = playerCache.get(playerId);
        if (ref != null) {
            Player player = ref.get();
            if (player != null && player.isOnline()) {
                return player;
            }

            playerCache.remove(playerId);
        }

        // If not in cache or invalid, try to get from Bukkit
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            playerCache.put(playerId, new WeakReference<>(player));
            return player;
        }

        return null;
    }

    // Cleanup method called periodically
    public void cleanup() {
        playerCache.entrySet().removeIf(entry -> {
            WeakReference<Player> ref = entry.getValue();
            if (ref == null) return true;

            Player player = ref.get();
            return player == null || !player.isOnline();
        });
    }

    private class PlayerCacheListener implements Listener {
        @EventHandler(priority = EventPriority.MONITOR)
        public void onJoin(PlayerJoinEvent event) {
            playerCache.put(event.getPlayer().getUniqueId(),
                    new WeakReference<>(event.getPlayer()));
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onQuit(PlayerQuitEvent event) {
            playerCache.remove(event.getPlayer().getUniqueId());
        }
    }
}