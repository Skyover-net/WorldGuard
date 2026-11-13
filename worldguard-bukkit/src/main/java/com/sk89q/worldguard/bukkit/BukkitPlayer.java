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

package com.sk89q.worldguard.bukkit;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldedit.world.gamemode.GameMode;
import com.sk89q.worldedit.world.weather.WeatherType;
import com.sk89q.worldedit.world.weather.WeatherTypes;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.util.PaperInterop;
import com.sk89q.worldguard.util.MessagingUtil;
import org.bukkit.BanList.Type;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;

public class BukkitPlayer extends com.sk89q.worldedit.bukkit.BukkitPlayer implements LocalPlayer {

    protected final WorldGuardPlugin plugin;
    private final boolean silenced;
    private String name;

    public BukkitPlayer(WorldGuardPlugin plugin, Player player) {
        this(plugin, player, false);
    }

    BukkitPlayer(WorldGuardPlugin plugin, Player player, boolean silenced) {
        super(player);
        this.plugin = plugin;
        this.silenced = silenced;
    }

    /**
     * Run a change to the player's state on the thread that owns the player.
     *
     * <p>This runs right away when already on that thread (always the case
     * for event handlers and session ticks). Otherwise, such as when a command
     * targets a player in another Folia region, the change is scheduled.</p>
     *
     * @param task the change
     */
    private void onPlayerThread(Runnable task) {
        plugin.getWorldGuardScheduler().executeAtEntity(getPlayer(), task);
    }

    @Override
    public String getName() {
        if (this.name == null) {
            // getName() takes longer than before in newer versions of Minecraft
            this.name = getPlayer().getName();
        }
        return name;
    }

    @Override
    public boolean hasGroup(String group) {
        return plugin.inGroup(getPlayer(), group);
    }

    @Override
    public void kick(String msg) {
        if (!silenced) {
            onPlayerThread(() -> getPlayer().kickPlayer(msg));
        }
    }

    @Override
    public void ban(String msg) {
        if (!silenced) {
            Bukkit.getBanList(Type.NAME).addBan(getName(), null, null, null);
            onPlayerThread(() -> getPlayer().kickPlayer(msg));
        }
    }

    @Override
    public double getHealth() {
        return getPlayer().getHealth();
    }

    @Override
    public void setHealth(double health) {
        onPlayerThread(() -> getPlayer().setHealth(health));
    }

    @Override
    public double getMaxHealth() {
        return getPlayer().getMaxHealth();
    }

    @Override
    public double getFoodLevel() {
        return getPlayer().getFoodLevel();
    }

    @Override
    public void setFoodLevel(double foodLevel) {
        onPlayerThread(() -> getPlayer().setFoodLevel((int) foodLevel));
    }

    @Override
    public double getSaturation() {
        return getPlayer().getSaturation();
    }

    @Override
    public void setSaturation(double saturation) {
        onPlayerThread(() -> getPlayer().setSaturation((float) saturation));
    }

    @Override
    public float getExhaustion() {
        return getPlayer().getExhaustion();
    }

    @Override
    public void setExhaustion(float exhaustion) {
        onPlayerThread(() -> getPlayer().setExhaustion(exhaustion));
    }

    @Override
    public WeatherType getPlayerWeather() {
        org.bukkit.WeatherType playerWeather = getPlayer().getPlayerWeather();
        return playerWeather == null ? null : playerWeather == org.bukkit.WeatherType.CLEAR ? WeatherTypes.CLEAR : WeatherTypes.RAIN;
    }

    @Override
    public void setPlayerWeather(WeatherType weather) {
        org.bukkit.WeatherType bukkitWeather = weather == WeatherTypes.CLEAR ? org.bukkit.WeatherType.CLEAR : org.bukkit.WeatherType.DOWNFALL;
        onPlayerThread(() -> getPlayer().setPlayerWeather(bukkitWeather));
    }

    @Override
    public void resetPlayerWeather() {
        onPlayerThread(() -> getPlayer().resetPlayerWeather());
    }

    @Override
    public boolean isPlayerTimeRelative() {
        return getPlayer().isPlayerTimeRelative();
    }

    @Override
    public long getPlayerTimeOffset() {
        return getPlayer().getPlayerTimeOffset();
    }

    @Override
    public void setPlayerTime(long time, boolean relative) {
        onPlayerThread(() -> getPlayer().setPlayerTime(time, relative));
    }

    @Override
    public void resetPlayerTime() {
        onPlayerThread(() -> getPlayer().resetPlayerTime());
    }

    @Override
    public int getFireTicks() {
        return getPlayer().getFireTicks();
    }

    @Override
    public void setFireTicks(int fireTicks) {
        onPlayerThread(() -> getPlayer().setFireTicks(fireTicks));
    }

    @Override
    public void setCompassTarget(Location location) {
        org.bukkit.Location target = BukkitAdapter.adapt(location);
        onPlayerThread(() -> getPlayer().setCompassTarget(target));
    }

    @Override
    public void setGameMode(GameMode gameMode) {
        onPlayerThread(() -> super.setGameMode(gameMode));
    }

    @Override
    public void sendTitle(String title, String subtitle) {
        if (WorldGuard.getInstance().getPlatform().getGlobalStateManager().get(getWorld()).forceDefaultTitleTimes) {
            getPlayer().sendTitle(title, subtitle, 10, 70, 20);
        } else {
            getPlayer().sendTitle(title, subtitle, -1, -1, -1);
        }
    }

    @Override
    public void resetFallDistance() {
        onPlayerThread(() -> getPlayer().setFallDistance(0));
    }

    @Override
    public void teleport(Location location, String successMessage, String failMessage) {
        org.bukkit.Location destination = BukkitAdapter.adapt(location);
        CompletableFuture<Boolean> result = plugin.getWorldGuardScheduler().isRegionThreaded()
                ? plugin.getWorldGuardScheduler().teleport(getPlayer(), destination)
                : PaperInterop.teleportAsync(getPlayer(), destination);
        result.thenApply(success -> {
            if (success) {
                // The success message can be cleared via flag
                if (!successMessage.isEmpty()) {
                    MessagingUtil.sendStringToChat(this, successMessage);
                }
            } else {
                printError(failMessage);
            }
            return success;
        });
    }

    @Override
    public String[] getGroups() {
        return plugin.getGroups(getPlayer());
    }

    @Override
    public void printRaw(String msg) {
        if (!silenced) {
            super.printRaw(msg);
        }
    }

    @Override
    public boolean hasPermission(String perm) {
        return plugin.hasPermission(getPlayer(), perm);
    }
}
