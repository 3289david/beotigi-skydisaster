package com.beotigi.skydisaster.disaster;

import com.beotigi.skydisaster.BeotigiPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 폭우: 폭풍이 심할 때 농장 주변에 물이 차오른다. 경고 없이 그냥 침수된다.
 * 시간이 지나면 알아서 물이 빠진다 (원래 블록은 파괴하지 않음, 물만 되돌림).
 */
public class FloodManager {

    private static final Material[] FLOODABLE_GROUND = {
            Material.FARMLAND, Material.DIRT, Material.GRASS_BLOCK, Material.COARSE_DIRT
    };

    private final BeotigiPlugin plugin;
    private final WeatherManager weatherManager;
    private final double floodChance;
    private final long recedeAfterMillis;

    // 우리가 직접 채운 물 블록만 추적해서 나중에 원상복구한다.
    private final Map<Location, Long> floodedWater = new LinkedHashMap<>();

    private BukkitTask tickTask;
    private BukkitTask recedeTask;

    public FloodManager(BeotigiPlugin plugin, WeatherManager weatherManager) {
        this.plugin = plugin;
        this.weatherManager = weatherManager;
        this.floodChance = plugin.getConfig().getDouble("flood.flood-chance-per-second", 0.02);
        this.recedeAfterMillis = plugin.getConfig().getLong("flood.recede-after-seconds", 90) * 1000L;
    }

    public void start() {
        tickTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 60L, 20L);
        recedeTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::recede, 200L, 100L);
    }

    public void stop() {
        if (tickTask != null) tickTask.cancel();
        if (recedeTask != null) recedeTask.cancel();
    }

    private void tick() {
        var rng = ThreadLocalRandom.current();
        for (World world : plugin.getServer().getWorlds()) {
            WeatherManager.Phase phase = weatherManager.getPhase(world);
            if (phase != WeatherManager.Phase.STORM && phase != WeatherManager.Phase.SEVERE_STORM) continue;

            for (Player player : world.getPlayers()) {
                if (rng.nextDouble() >= floodChance) continue;
                floodNear(player, rng);
            }
        }
    }

    private void floodNear(Player player, java.util.concurrent.ThreadLocalRandom rng) {
        Location base = player.getLocation();
        World world = player.getWorld();
        int placed = 0;
        for (int attempt = 0; attempt < 20 && placed < 4; attempt++) {
            int dx = rng.nextInt(-10, 11);
            int dz = rng.nextInt(-10, 11);
            int y = world.getHighestBlockYAt(base.getBlockX() + dx, base.getBlockZ() + dz);
            Block ground = world.getBlockAt(base.getBlockX() + dx, y, base.getBlockZ() + dz);
            Block above = ground.getRelative(0, 1, 0);

            if (!isFloodable(ground.getType()) || above.getType() != Material.AIR) continue;

            above.setType(Material.WATER, false);
            floodedWater.put(above.getLocation(), System.currentTimeMillis());
            placed++;

            if (ground.getType() == Material.FARMLAND && rng.nextDouble() < 0.3) {
                world.playSound(ground.getLocation(), Sound.BLOCK_WATER_AMBIENT, 0.5f, 1f);
            }
        }
    }

    private boolean isFloodable(Material type) {
        for (Material m : FLOODABLE_GROUND) {
            if (m == type) return true;
        }
        return false;
    }

    private void recede() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<Location, Long>> it = floodedWater.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Location, Long> entry = it.next();
            if (now - entry.getValue() < recedeAfterMillis) continue;
            Block block = entry.getKey().getBlock();
            if (block.getType() == Material.WATER) {
                block.setType(Material.AIR, false);
            }
            it.remove();
        }
    }
}
