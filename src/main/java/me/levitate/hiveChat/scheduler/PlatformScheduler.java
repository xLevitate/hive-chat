package me.levitate.hiveChat.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * A cross-platform scheduler that supports both Folia and Paper/Spigot
 */
public class PlatformScheduler {

    private final Plugin plugin;
    private final boolean isFolia;
    private final PlatformType platformType;

    // Folia-specific objects (only available on Folia)
    private Object globalRegionScheduler;
    private Object asyncScheduler;
    private Method runTask;
    private Method runTaskAsync;
    private Method runTaskTimer;
    private Method runTaskLater;
    private Method runAtLocation;
    private Method runAtEntity;
    private Method cancelAllTasks;

    public enum PlatformType {
        FOLIA,
        PAPER,
        SPIGOT
    }

    public PlatformScheduler(Plugin plugin) {
        this.plugin = plugin;
        this.platformType = detectPlatform();
        this.isFolia = platformType == PlatformType.FOLIA;

        if (isFolia) {
            initializeFoliaSchedulers();
        }
    }

    private PlatformType detectPlatform() {
        try {
            // Check for Folia-specific classes
            Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
            return PlatformType.FOLIA;
        } catch (ClassNotFoundException e) {
            // Not Folia, check for Paper
            try {
                Class.forName("com.destroystokyo.paper.PaperConfig");
                return PlatformType.PAPER;
            } catch (ClassNotFoundException ex) {
                return PlatformType.SPIGOT;
            }
        }
    }

    private void initializeFoliaSchedulers() {
        try {
            // Get Folia schedulers via reflection
            Method getGlobalRegionScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler");
            Method getAsyncScheduler = Bukkit.class.getMethod("getAsyncScheduler");

            this.globalRegionScheduler = getGlobalRegionScheduler.invoke(null);
            this.asyncScheduler = getAsyncScheduler.invoke(null);

            // Cache method references for performance
            Class<?> globalSchedulerClass = globalRegionScheduler.getClass();
            Class<?> asyncSchedulerClass = asyncScheduler.getClass();

            this.runTask = globalSchedulerClass.getMethod("run", Plugin.class, Consumer.class);
            this.runTaskLater = globalSchedulerClass.getMethod("runDelayed", Plugin.class, Consumer.class, long.class);
            this.runTaskTimer = globalSchedulerClass.getMethod("runAtFixedRate", Plugin.class, Consumer.class,
                    long.class, long.class);
            this.runTaskAsync = asyncSchedulerClass.getMethod("runNow", Plugin.class, Consumer.class);
            this.cancelAllTasks = globalSchedulerClass.getMethod("cancelTasks", Plugin.class);

            // Location and entity schedulers
            Method getRegionScheduler = Bukkit.class.getMethod("getRegionScheduler");
            Object regionScheduler = getRegionScheduler.invoke(null);
            this.runAtLocation = regionScheduler.getClass().getMethod("run", Plugin.class, Location.class,
                    Consumer.class);

            Method getEntityScheduler = Bukkit.class.getMethod("getEntityScheduler");
            Object entityScheduler = getEntityScheduler.invoke(null);
            this.runAtEntity = entityScheduler.getClass().getMethod("run", Entity.class, Plugin.class, Consumer.class,
                    Runnable.class);

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Folia schedulers", e);
        }
    }

    public boolean isFolia() {
        return isFolia;
    }

    public boolean isPaper() {
        return platformType == PlatformType.PAPER;
    }

    public boolean isSpigot() {
        return platformType == PlatformType.SPIGOT;
    }

    public PlatformType getPlatformType() {
        return platformType;
    }

    /**
     * Runs a task on the next tick (main thread for Paper/Spigot, global region for
     * Folia)
     */
    public void runTask(Runnable task) {
        if (isFolia) {
            try {
                runTask.invoke(globalRegionScheduler, plugin, (Consumer<Object>) t -> task.run());
            } catch (Exception e) {
                throw new RuntimeException("Failed to run task on Folia", e);
            }
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /**
     * Runs a task asynchronously
     */
    public void runTaskAsync(Runnable task) {
        if (isFolia) {
            try {
                runTaskAsync.invoke(asyncScheduler, plugin, (Consumer<Object>) t -> task.run());
            } catch (Exception e) {
                throw new RuntimeException("Failed to run async task on Folia", e);
            }
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }

    /**
     * Runs a task after a delay
     */
    public void runTaskLater(Runnable task, long delay) {
        if (isFolia) {
            try {
                runTaskLater.invoke(globalRegionScheduler, plugin, (Consumer<Object>) t -> task.run(), delay);
            } catch (Exception e) {
                throw new RuntimeException("Failed to run delayed task on Folia", e);
            }
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delay);
        }
    }

    /**
     * Runs a repeating task
     */
    public ScheduledTask runTaskTimer(Runnable task, long delay, long period) {
        if (isFolia) {
            try {
                Object foliaTask = runTaskTimer.invoke(globalRegionScheduler, plugin,
                        (Consumer<Object>) t -> task.run(), delay, period);
                return new FoliaScheduledTask(foliaTask);
            } catch (Exception e) {
                throw new RuntimeException("Failed to run timer task on Folia", e);
            }
        } else {
            int taskId = Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period).getTaskId();
            return new BukkitScheduledTask(taskId);
        }
    }

    /**
     * Runs a task at a specific location (region-aware for Folia)
     */
    public void runAtLocation(Location location, Runnable task) {
        if (isFolia) {
            try {
                Method getRegionScheduler = Bukkit.class.getMethod("getRegionScheduler");
                Object regionScheduler = getRegionScheduler.invoke(null);
                runAtLocation.invoke(regionScheduler, plugin, location, (Consumer<Object>) t -> task.run());
            } catch (Exception e) {
                throw new RuntimeException("Failed to run location task on Folia", e);
            }
        } else {
            // On Paper/Spigot, just run on main thread
            runTask(task);
        }
    }

    /**
     * Runs a task at an entity's location (entity-aware for Folia)
     */
    public <T extends Entity> void runAtEntity(T entity, Consumer<T> task) {
        if (isFolia) {
            try {
                Method getEntityScheduler = Bukkit.class.getMethod("getEntityScheduler");
                Object entityScheduler = getEntityScheduler.invoke(null);
                Runnable entityRetiredCallback = () -> {
                    // Entity removed fallback - do nothing
                };
                runAtEntity.invoke(entityScheduler, entity, plugin, (Consumer<Object>) t -> task.accept(entity),
                        entityRetiredCallback);
            } catch (Exception e) {
                throw new RuntimeException("Failed to run entity task on Folia", e);
            }
        } else {
            // On Paper/Spigot, just run on main thread
            runTask(() -> task.accept(entity));
        }
    }

    /**
     * Cancels all tasks scheduled by this plugin
     */
    public void cancelAllTasks() {
        if (isFolia) {
            try {
                cancelAllTasks.invoke(globalRegionScheduler, plugin);
            } catch (Exception e) {
                throw new RuntimeException("Failed to cancel tasks on Folia", e);
            }
        } else {
            Bukkit.getScheduler().cancelTasks(plugin);
        }
    }

    /**
     * Interface for scheduled tasks
     */
    public interface ScheduledTask {
        void cancel();

        boolean isCancelled();
    }

    /**
     * Bukkit implementation of scheduled task
     */
    private static class BukkitScheduledTask implements ScheduledTask {
        private final int taskId;
        private boolean cancelled = false;

        public BukkitScheduledTask(int taskId) {
            this.taskId = taskId;
        }

        @Override
        public void cancel() {
            if (!cancelled) {
                Bukkit.getScheduler().cancelTask(taskId);
                cancelled = true;
            }
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }

    /**
     * Folia implementation of scheduled task
     */
    private static class FoliaScheduledTask implements ScheduledTask {
        private final Object foliaTask;
        private boolean cancelled = false;

        public FoliaScheduledTask(Object foliaTask) {
            this.foliaTask = foliaTask;
        }

        @Override
        public void cancel() {
            if (!cancelled) {
                try {
                    Method cancel = foliaTask.getClass().getMethod("cancel");
                    cancel.invoke(foliaTask);
                    cancelled = true;
                } catch (Exception e) {
                    throw new RuntimeException("Failed to cancel Folia task", e);
                }
            }
        }

        @Override
        public boolean isCancelled() {
            if (cancelled)
                return true;

            try {
                Method isCancelled = foliaTask.getClass().getMethod("isCancelled");
                return (Boolean) isCancelled.invoke(foliaTask);
            } catch (Exception e) {
                return cancelled;
            }
        }
    }
}