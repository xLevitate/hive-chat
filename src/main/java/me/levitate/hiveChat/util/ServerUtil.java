package me.levitate.hiveChat.util;

import com.tcoded.folialib.FoliaLib;
import com.tcoded.folialib.wrapper.task.WrappedTask;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class ServerUtil {

    private static FoliaLib foliaLib;
    private static Plugin plugin;
    private static final Map<String, Object> namedTasks = new HashMap<>();
    
    /**
     * Initialize ServerUtil with a plugin instance
     * @param pluginInstance The plugin instance
     */
    public static void init(Plugin pluginInstance) {
        plugin = pluginInstance;
        if (foliaLib == null) {
            foliaLib = new FoliaLib(plugin);
        }
    }

    /**
     * Checks if the server is running Folia
     * @return true if running Folia, false otherwise
     */
    public static boolean isFolia() {
        ensureInitialized();
        return foliaLib.isFolia();
    }

    /**
     * Runs a task synchronously on the main thread (Paper) or global region (Folia)
     */
    public static void runTask(Runnable task) {
        ensureInitialized();
        try {
            foliaLib.getScheduler().runNextTick(wrappedTask -> task.run());
        } catch (Exception e) {
            logError("Error running task", e);
        }
    }

    /**
     * Runs a task asynchronously
     */
    public static void runTaskAsync(Runnable task) {
        ensureInitialized();
        try {
            foliaLib.getScheduler().runAsync(wrappedTask -> task.run());
        } catch (Exception e) {
            logError("Error running async task", e);
        }
    }

    /**
     * Schedules a repeating task
     */
    public static void runTaskTimer(Runnable task, long delay, long period) {
        ensureInitialized();
        try {
            foliaLib.getScheduler().runTimer(wrappedTask -> task.run(), delay, period);
        } catch (Exception e) {
            logError("Error scheduling timer task", e);
        }
    }
    
    /**
     * Schedules a named repeating task that can be cancelled by name later
     */
    public static void runNamedTaskTimer(String taskName, Runnable task, long delay, long period) {
        ensureInitialized();
        cancelNamedTask(taskName); // Cancel if already exists
        
        try {
            // Store a marker for this task so we can track it was registered
            namedTasks.put(taskName, Boolean.TRUE);
            
            // FoliaLib's runTimer doesn't return the task, so we just run it
            foliaLib.getScheduler().runTimer(wt -> {
                // Only run if the task is still registered
                if (namedTasks.containsKey(taskName)) {
                    task.run();
                } else {
                    // If task was canceled, stop executing
                    wt.cancel();
                }
            }, delay, period);
        } catch (Exception e) {
            logError("Error scheduling named timer task: " + taskName, e);
        }
    }
    
    /**
     * Cancels a named task
     */
    public static void cancelNamedTask(String taskName) {
        // When we remove from namedTasks, it signals the task to self-cancel on next execution
        namedTasks.remove(taskName);
    }

    /**
     * Schedules a delayed task
     */
    public static void runTaskLater(Runnable task, long delay) {
        ensureInitialized();
        try {
            foliaLib.getScheduler().runLater(wrappedTask -> task.run(), delay);
        } catch (Exception e) {
            logError("Error scheduling delayed task", e);
        }
    }

    /**
     * Executes a task at an entity's location (region-aware in Folia)
     */
    public static <T extends Entity> void runAtEntity(T entity, Consumer<T> task) {
        ensureInitialized();
        if (entity == null) return;
        
        try {
            foliaLib.getScheduler().runAtEntity(entity, wrappedTask -> task.accept(entity));
        } catch (Exception e) {
            logError("Error running task at entity", e);
        }
    }

    /**
     * Executes a task at a specific location (region-aware in Folia)
     */
    public static void runAtLocation(Location location, Runnable task) {
        ensureInitialized();
        if (location == null || location.getWorld() == null) return;
        
        try {
            foliaLib.getScheduler().runAtLocation(location, wrappedTask -> task.run());
        } catch (Exception e) {
            logError("Error running task at location", e);
        }
    }

    /**
     * Executes a task on the global scheduler
     */
    public static void runGlobally(boolean async, Runnable task) {
        if (async) {
            runTaskAsync(task);
        } else {
            runTask(task);
        }
    }

    /**
     * Completes a future on the main thread or appropriate region
     */
    public static <T> void completeFuture(CompletableFuture<T> future, java.util.function.Supplier<T> valueSupplier) {
        runTask(() -> {
            try {
                future.complete(valueSupplier.get());
            } catch (Exception e) {
                future.completeExceptionally(e);
                logError("Error completing future", e);
            }
        });
    }
    
    /**
     * Cancel all scheduled tasks
     */
    public static void cancelAllTasks() {
        if (foliaLib != null) {
            foliaLib.getScheduler().cancelAllTasks();
        }
        namedTasks.clear();
    }
    
    /**
     * Check if a specific entity is in the current server thread's region
     * Useful for determining if an operation should be scheduled or can run directly
     */
    public static boolean isEntityInCurrentRegion(Entity entity) {
        ensureInitialized();
        if (!isFolia() || entity == null) return true;
        
        // In Paper, we're always on the main thread
        // In Folia, we need to check the region - but this is handled by FoliaLib internally
        // So we'll just return false for Folia, indicating it should use the proper scheduling method
        return !isFolia();
    }
    
    private static void logError(String message, Exception e) {
        if (plugin != null) {
            plugin.getLogger().warning(message + ": " + e.getMessage());
        }
    }
    
    private static void ensureInitialized() {
        if (foliaLib == null || plugin == null) {
            throw new IllegalStateException("ServerUtil has not been initialized. Call ServerUtil.init(plugin) first!");
        }
    }
    
    public static Plugin getPlugin() {
        ensureInitialized();
        return plugin;
    }
} 