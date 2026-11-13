/*
 * WorldGuard, a suite of tools for Minecraft
 * Copyright (C) sk89q <http://www.sk89q.com>
 * Copyright (C) WorldGuard team and contributors
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.sk89q.worldguard.bukkit.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A scheduler for region-threaded servers (Folia and its forks, such as
 * Canvas), backed by the global region, region, entity and async schedulers.
 *
 * <p>This class references Folia scheduler types directly, so it must only
 * be loaded when the server is region-threaded.</p>
 */
public final class FoliaWorldGuardScheduler implements WorldGuardScheduler {

    private final Plugin plugin;

    public FoliaWorldGuardScheduler(Plugin plugin) {
        this.plugin = checkNotNull(plugin);
    }

    private Server server() {
        return plugin.getServer();
    }

    /**
     * Folia rejects delays below one tick.
     */
    private static long ticks(long ticks) {
        return Math.max(1, ticks);
    }

    private static Consumer<ScheduledTask> consumer(Runnable task) {
        return ignored -> task.run();
    }

    private static TaskHandle wrap(@Nullable ScheduledTask task) {
        if (task == null) {
            return TaskHandle.NONE;
        }
        return new TaskHandle() {
            @Override
            public void cancel() {
                task.cancel();
            }

            @Override
            public boolean isCancelled() {
                return task.isCancelled();
            }
        };
    }

    @Override
    public boolean isRegionThreaded() {
        return true;
    }

    @Override
    public TaskHandle runGlobal(Runnable task) {
        return wrap(server().getGlobalRegionScheduler().run(plugin, consumer(task)));
    }

    @Override
    public TaskHandle runGlobalLater(Runnable task, long delayTicks) {
        return wrap(server().getGlobalRegionScheduler().runDelayed(plugin, consumer(task), ticks(delayTicks)));
    }

    @Override
    public TaskHandle runGlobalTimer(Runnable task, long delayTicks, long periodTicks) {
        return wrap(server().getGlobalRegionScheduler().runAtFixedRate(plugin, consumer(task), ticks(delayTicks), ticks(periodTicks)));
    }

    @Override
    public TaskHandle runAtLocation(Location location, Runnable task) {
        return wrap(server().getRegionScheduler().run(plugin, location, consumer(task)));
    }

    @Override
    public TaskHandle runAtLocationLater(Location location, Runnable task, long delayTicks) {
        return wrap(server().getRegionScheduler().runDelayed(plugin, location, consumer(task), ticks(delayTicks)));
    }

    @Override
    public TaskHandle runAtEntity(Entity entity, Runnable task, @Nullable Runnable retired) {
        ScheduledTask scheduled = entity.getScheduler().run(plugin, consumer(task), retired);
        if (scheduled == null && retired != null) {
            // The entity was already removed, so the scheduler will not call the retired callback
            retired.run();
        }
        return wrap(scheduled);
    }

    @Override
    public TaskHandle runAtEntityLater(Entity entity, Runnable task, @Nullable Runnable retired, long delayTicks) {
        ScheduledTask scheduled = entity.getScheduler().runDelayed(plugin, consumer(task), retired, ticks(delayTicks));
        if (scheduled == null && retired != null) {
            retired.run();
        }
        return wrap(scheduled);
    }

    @Override
    public TaskHandle runAsync(Runnable task) {
        return wrap(server().getAsyncScheduler().runNow(plugin, consumer(task)));
    }

    @Override
    public boolean isOwnedByCurrentRegion(Entity entity) {
        return server().isOwnedByCurrentRegion(entity);
    }

    @Override
    public boolean isOwnedByCurrentRegion(Location location) {
        return server().isOwnedByCurrentRegion(location);
    }

    @Override
    public CompletableFuture<Boolean> teleport(Entity entity, Location location) {
        if (isOwnedByCurrentRegion(entity)) {
            return entity.teleportAsync(location);
        }
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        runAtEntity(entity, () -> entity.teleportAsync(location).whenComplete((result, error) -> {
            if (error != null) {
                future.completeExceptionally(error);
            } else {
                future.complete(result);
            }
        }), () -> future.complete(false));
        return future;
    }

    @Override
    public void cancelAllTasks() {
        // Entity and region tasks are dropped by the server once the plugin is disabled
        server().getGlobalRegionScheduler().cancelTasks(plugin);
        server().getAsyncScheduler().cancelTasks(plugin);
    }
}
