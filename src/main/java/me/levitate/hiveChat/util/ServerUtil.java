package me.levitate.hiveChat.util;

import me.levitate.hiveChat.scheduler.PlatformScheduler;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class ServerUtil {

    private static PlatformScheduler platformScheduler;
    private static Plugin plugin;
    private static final Map<String, PlatformScheduler.ScheduledTask> namedTasks = new HashMap<>();

    /**
     * Initialize ServerUtil with a plugin instance
     * 
     * @param pluginInstance The plugin instance
     */
    public static void init(Plugin pluginInstance) {
        plugin = pluginInstance;
        if (platformScheduler == null) {
            platformScheduler = new PlatformScheduler(plugin);
        }
    }

    /**
     * Checks if the server is running Folia
     * 
     * @return true if running Folia, false otherwise
     */
    public static boolean isFolia() {
        ensureInitialized();
        return platformScheduler.isFolia();
    }

    /**
     * Runs a task synchronously on the main thread (Paper) or global region (Folia)
     */
    public static void runTask(Runnable task) {
        ensureInitialized();
        try {
            platformScheduler.runTask(task);
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
            platformScheduler.runTaskAsync(task);
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
            platformScheduler.runTaskTimer(task, delay, period);
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
            // Store the actual task so we can cancel it later
            PlatformScheduler.ScheduledTask scheduledTask = platformScheduler.runTaskTimer(task, delay, period);
            namedTasks.put(taskName, scheduledTask);
        } catch (Exception e) {
            logError("Error scheduling named timer task: " + taskName, e);
        }
    }

    /**
     * Cancels a named task
     */
    public static void cancelNamedTask(String taskName) {
        PlatformScheduler.ScheduledTask task = namedTasks.remove(taskName);
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    /**
     * Schedules a delayed task
     */
    public static void runTaskLater(Runnable task, long delay) {
        ensureInitialized();
        try {
            platformScheduler.runTaskLater(task, delay);
        } catch (Exception e) {
            logError("Error scheduling delayed task", e);
        }
    }

    /**
     * Executes a task at an entity's location (region-aware in Folia)
     */
    public static <T extends Entity> void runAtEntity(T entity, Consumer<T> task) {
        ensureInitialized();
        if (entity == null)
            return;

        try {
            platformScheduler.runAtEntity(entity, task);
        } catch (Exception e) {
            logError("Error running task at entity", e);
        }
    }

    /**
     * Executes a task at a specific location (region-aware in Folia)
     */
    public static void runAtLocation(Location location, Runnable task) {
        ensureInitialized();
        if (location == null || location.getWorld() == null)
            return;

        try {
            platformScheduler.runAtLocation(location, task);
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
        // Cancel all named tasks
        for (PlatformScheduler.ScheduledTask task : namedTasks.values()) {
            if (!task.isCancelled()) {
                task.cancel();
            }
        }
        namedTasks.clear();

        // Cancel all tasks from the platform scheduler
        if (platformScheduler != null) {
            platformScheduler.cancelAllTasks();
        }
    }

    /**
     * Check if a specific entity is in the current server thread's region
     * Useful for determining if an operation should be scheduled or can run
     * directly
     */
    public static boolean isEntityInCurrentRegion(Entity entity) {
        ensureInitialized();
        if (!isFolia() || entity == null)
            return true;

        // For now, return false to always schedule the task
        // This could be enhanced with actual region checking in the future
        return !isFolia();
    }

    /**
     * Get platform type information
     */
    public static PlatformScheduler.PlatformType getPlatformType() {
        ensureInitialized();
        return platformScheduler.getPlatformType();
    }

    /**
     * Check if running on Paper
     */
    public static boolean isPaper() {
        ensureInitialized();
        return platformScheduler.isPaper();
    }

    /**
     * Check if running on Spigot
     */
    public static boolean isSpigot() {
        ensureInitialized();
        return platformScheduler.isSpigot();
    }

    private static void logError(String message, Exception e) {
        if (plugin != null) {
            plugin.getLogger().severe(message + ": " + e.getMessage());
        }
    }

    private static void ensureInitialized() {
        if (platformScheduler == null || plugin == null) {
            throw new IllegalStateException("ServerUtil has not been initialized! Call ServerUtil.init(plugin) first!");
        }
    }

    public static Plugin getPlugin() {
        return plugin;
    }
}