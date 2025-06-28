package me.levitate.hiveChat.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.function.Consumer;

public class PlatformScheduler {

    private final Plugin plugin;
    private boolean isFolia;
    private PlatformType platformType;

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

        plugin.getLogger().info("Detected server platform: " + platformType);

        if (isFolia) {
            try {
                initializeFoliaSchedulers();
                plugin.getLogger().info("Successfully initialized Folia schedulers");
            } catch (Exception e) {
                plugin.getLogger().warning(
                        "Failed to initialize Folia schedulers, falling back to Paper mode: " + e.getMessage());
                this.platformType = PlatformType.PAPER;
                this.isFolia = false;
            }
        }
    }

    private PlatformType detectPlatform() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return PlatformType.FOLIA;
        } catch (ClassNotFoundException e) {
            try {
                Class.forName("com.destroystokyo.paper.PaperConfig");
                return PlatformType.PAPER;
            } catch (ClassNotFoundException ex) {
                try {
                    Class.forName("io.papermc.paper.configuration.Configuration");
                    return PlatformType.PAPER;
                } catch (ClassNotFoundException exc) {
                    return PlatformType.SPIGOT;
                }
            }
        }
    }

    private void initializeFoliaSchedulers() {
        try {
            Method getGlobalRegionScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler");
            Method getAsyncScheduler = Bukkit.class.getMethod("getAsyncScheduler");

            this.globalRegionScheduler = getGlobalRegionScheduler.invoke(null);
            this.asyncScheduler = getAsyncScheduler.invoke(null);

            Class<?> globalSchedulerClass = globalRegionScheduler.getClass();
            Class<?> asyncSchedulerClass = asyncScheduler.getClass();

            this.runTask = globalSchedulerClass.getMethod("run", Plugin.class, Consumer.class);
            this.runTaskLater = globalSchedulerClass.getMethod("runDelayed", Plugin.class, Consumer.class, long.class);
            this.runTaskTimer = globalSchedulerClass.getMethod("runAtFixedRate", Plugin.class, Consumer.class,
                    long.class, long.class);
            this.runTaskAsync = asyncSchedulerClass.getMethod("runNow", Plugin.class, Consumer.class);
            this.cancelAllTasks = globalSchedulerClass.getMethod("cancelTasks", Plugin.class);

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
            runTask(task);
        }
    }

    public <T extends Entity> void runAtEntity(T entity, Consumer<T> task) {
        if (isFolia) {
            try {
                Method getEntityScheduler = Bukkit.class.getMethod("getEntityScheduler");
                Object entityScheduler = getEntityScheduler.invoke(null);
                Runnable entityRetiredCallback = () -> {
                };
                runAtEntity.invoke(entityScheduler, entity, plugin, (Consumer<Object>) t -> task.accept(entity),
                        entityRetiredCallback);
            } catch (Exception e) {
                throw new RuntimeException("Failed to run entity task on Folia", e);
            }
        } else {
            runTask(() -> task.accept(entity));
        }
    }

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

    public interface ScheduledTask {
        void cancel();

        boolean isCancelled();
    }

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