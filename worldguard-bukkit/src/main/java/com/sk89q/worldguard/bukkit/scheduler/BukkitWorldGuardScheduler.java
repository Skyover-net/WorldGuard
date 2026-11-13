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

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A scheduler for servers with a single main thread (Spigot, Paper), backed
 * by the {@link BukkitScheduler}.
 */
public final class BukkitWorldGuardScheduler implements WorldGuardScheduler {

    private final Plugin plugin;

    public BukkitWorldGuardScheduler(Plugin plugin) {
        this.plugin = checkNotNull(plugin);
    }

    private static BukkitScheduler scheduler() {
        return Bukkit.getScheduler();
    }

    private static TaskHandle wrap(BukkitTask task) {
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

    private static boolean isRemoved(Entity entity) {
        return entity instanceof Player player ? !player.isOnline() : !entity.isValid();
    }

    private static Runnable entityTask(Entity entity, Runnable task, @Nullable Runnable retired) {
        return () -> {
            if (isRemoved(entity)) {
                if (retired != null) {
                    retired.run();
                }
            } else {
                task.run();
            }
        };
    }

    @Override
    public boolean isRegionThreaded() {
        return false;
    }

    @Override
    public TaskHandle runGlobal(Runnable task) {
        return wrap(scheduler().runTask(plugin, task));
    }

    @Override
    public TaskHandle runGlobalLater(Runnable task, long delayTicks) {
        return wrap(scheduler().runTaskLater(plugin, task, delayTicks));
    }

    @Override
    public TaskHandle runGlobalTimer(Runnable task, long delayTicks, long periodTicks) {
        return wrap(scheduler().runTaskTimer(plugin, task, delayTicks, periodTicks));
    }

    @Override
    public TaskHandle runAtLocation(Location location, Runnable task) {
        return runGlobal(task);
    }

    @Override
    public TaskHandle runAtLocationLater(Location location, Runnable task, long delayTicks) {
        return runGlobalLater(task, delayTicks);
    }

    @Override
    public TaskHandle runAtEntity(Entity entity, Runnable task, @Nullable Runnable retired) {
        return runGlobal(entityTask(entity, task, retired));
    }

    @Override
    public TaskHandle runAtEntityLater(Entity entity, Runnable task, @Nullable Runnable retired, long delayTicks) {
        return runGlobalLater(entityTask(entity, task, retired), delayTicks);
    }

    @Override
    public TaskHandle runAsync(Runnable task) {
        return wrap(scheduler().runTaskAsynchronously(plugin, task));
    }

    @Override
    public boolean isOwnedByCurrentRegion(Entity entity) {
        return Bukkit.isPrimaryThread();
    }

    @Override
    public boolean isOwnedByCurrentRegion(Location location) {
        return Bukkit.isPrimaryThread();
    }

    @Override
    public CompletableFuture<Boolean> teleport(Entity entity, Location location) {
        if (Bukkit.isPrimaryThread()) {
            return CompletableFuture.completedFuture(entity.teleport(location));
        }
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        runAtEntity(entity, () -> future.complete(entity.teleport(location)), () -> future.complete(false));
        return future;
    }

    @Override
    public void cancelAllTasks() {
        scheduler().cancelTasks(plugin);
    }
}
