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

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;

/**
 * Schedules work on the thread that owns the affected game state.
 *
 * <p>On region-threaded servers (Folia and its forks, such as Canvas) work
 * is routed to the global region, the region that owns a location, or the
 * scheduler of an entity. On other servers everything runs on the main
 * server thread through the Bukkit scheduler.</p>
 *
 * <p>All delays and periods are in server ticks.</p>
 */
public interface WorldGuardScheduler {

    /**
     * Get whether the server uses region threading, in which case there is
     * no single main thread.
     *
     * @return true on Folia and its forks
     */
    boolean isRegionThreaded();

    /**
     * Run a task on the global region (Folia) or main thread (Bukkit) on the next tick.
     *
     * <p>The global region does not own any entities or chunks, so the task
     * must not touch them directly.</p>
     *
     * @param task the task
     * @return the handle
     */
    TaskHandle runGlobal(Runnable task);

    /**
     * Run a task on the global region (Folia) or main thread (Bukkit) after a delay.
     *
     * @param task the task
     * @param delayTicks the delay, in ticks
     * @return the handle
     */
    TaskHandle runGlobalLater(Runnable task, long delayTicks);

    /**
     * Run a repeating task on the global region (Folia) or main thread (Bukkit).
     *
     * @param task the task
     * @param delayTicks the initial delay, in ticks
     * @param periodTicks the period, in ticks
     * @return the handle
     */
    TaskHandle runGlobalTimer(Runnable task, long delayTicks, long periodTicks);

    /**
     * Run a task on the region that owns the given location.
     *
     * @param location the location
     * @param task the task
     * @return the handle
     */
    TaskHandle runAtLocation(Location location, Runnable task);

    /**
     * Run a task on the region that owns the given location after a delay.
     *
     * @param location the location
     * @param task the task
     * @param delayTicks the delay, in ticks
     * @return the handle
     */
    TaskHandle runAtLocationLater(Location location, Runnable task, long delayTicks);

    /**
     * Run a task on the thread that owns the given entity. The task follows
     * the entity if it moves between regions.
     *
     * @param entity the entity
     * @param task the task
     * @param retired run instead of the task if the entity is removed before the task runs, may be null
     * @return the handle
     */
    TaskHandle runAtEntity(Entity entity, Runnable task, @Nullable Runnable retired);

    /**
     * Run a task on the thread that owns the given entity after a delay.
     *
     * @param entity the entity
     * @param task the task
     * @param retired run instead of the task if the entity is removed before the task runs, may be null
     * @param delayTicks the delay, in ticks
     * @return the handle
     */
    TaskHandle runAtEntityLater(Entity entity, Runnable task, @Nullable Runnable retired, long delayTicks);

    /**
     * Run a task off the server threads.
     *
     * @param task the task
     * @return the handle
     */
    TaskHandle runAsync(Runnable task);

    /**
     * Get whether the current thread owns the given entity.
     *
     * @param entity the entity
     * @return true if the entity can be accessed from this thread
     */
    boolean isOwnedByCurrentRegion(Entity entity);

    /**
     * Get whether the current thread owns the given location.
     *
     * @param location the location
     * @return true if the location can be accessed from this thread
     */
    boolean isOwnedByCurrentRegion(Location location);

    /**
     * Run a task right away if the current thread owns the entity,
     * otherwise schedule it on the entity's thread.
     *
     * @param entity the entity
     * @param task the task
     */
    default void executeAtEntity(Entity entity, Runnable task) {
        if (isOwnedByCurrentRegion(entity)) {
            task.run();
        } else {
            runAtEntity(entity, task, null);
        }
    }

    /**
     * Run a task right away if the current thread owns the location,
     * otherwise schedule it on the owning region.
     *
     * @param location the location
     * @param task the task
     */
    default void executeAtLocation(Location location, Runnable task) {
        if (isOwnedByCurrentRegion(location)) {
            task.run();
        } else {
            runAtLocation(location, task);
        }
    }

    /**
     * Teleport an entity in the way the server requires.
     *
     * <p>On Folia this is always asynchronous, and may be called from any
     * thread. On other servers the teleport happens right away when called
     * from the main thread.</p>
     *
     * @param entity the entity
     * @param location the destination
     * @return a future that completes with whether the teleport succeeded
     */
    CompletableFuture<Boolean> teleport(Entity entity, Location location);

    /**
     * Cancel all tasks that this scheduler can cancel in bulk.
     */
    void cancelAllTasks();

}
