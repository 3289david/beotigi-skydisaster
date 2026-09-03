package com.beotigi.skydisaster.ecosystem;

import com.beotigi.skydisaster.BeotigiPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 섬이 커질수록 생물이 늘어나고, 그 생태계에 스스로 문제가 생긴다.
 * 쥐(토끼)가 너무 늘면 밭이 상하고, 벌이 없으면 작물이 느리게 자라고,
 * 양이 너무 많아지면 늑대가 몰려온다 - 전부 바닐라 AI에 최대한 기대어 구현.
 */
public class EcosystemManager implements Listener {

    private static final Set<Material> CROPS = Set.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS,
            Material.SUGAR_CANE, Material.MELON_STEM, Material.PUMPKIN_STEM
    );

    private final BeotigiPlugin plugin;
    private final int scanRadius = 16;
    private final int rabbitThreshold;
    private final double rabbitLitterChance;
    private final int beeSearchRadius;
    private final double noBeeGrowthPenaltyChance;
    private final int wolfPressureSheepThreshold;
    private final double wolfSpawnChance;

    private BukkitTask task;

    public EcosystemManager(BeotigiPlugin plugin) {
        this.plugin = plugin;
        var cfg = plugin.getConfig();
        this.rabbitThreshold = cfg.getInt("ecosystem.rabbit-overpopulation-threshold", 6);
        this.rabbitLitterChance = cfg.getDouble("ecosystem.rabbit-litter-chance", 0.15);
        this.beeSearchRadius = cfg.getInt("ecosystem.bee-search-radius", 12);
        this.noBeeGrowthPenaltyChance = cfg.getDouble("ecosystem.no-bee-growth-penalty-chance", 0.25);
        this.wolfPressureSheepThreshold = cfg.getInt("ecosystem.wolf-pressure-sheep-threshold", 5);
        this.wolfSpawnChance = cfg.getDouble("ecosystem.wolf-spawn-chance", 0.1);
        this.intervalTicks = cfg.getInt("ecosystem.scan-interval-seconds", 30) * 20L;
    }

    private final long intervalTicks;

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, intervalTicks, intervalTicks);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    private void tick() {
        var rng = ThreadLocalRandom.current();
        for (World world : plugin.getServer().getWorlds()) {
            for (Player player : world.getPlayers()) {
                handleRabbits(player, rng);
                handleWolfPressure(player, rng);
            }
        }
    }

    private void handleRabbits(Player player, java.util.concurrent.ThreadLocalRandom rng) {
        Location loc = player.getLocation();
        long rabbitCount = player.getWorld().getNearbyEntities(loc, scanRadius, scanRadius, scanRadius).stream()
                .filter(e -> e.getType() == EntityType.RABBIT).count();
        if (rabbitCount >= rabbitThreshold) return;
        if (rng.nextDouble() >= rabbitLitterChance) return;

        Block farmland = findNearbyFarmland(player, rng);
        if (farmland == null) return;

        int litter = rng.nextInt(1, 3);
        for (int i = 0; i < litter; i++) {
            player.getWorld().spawnEntity(farmland.getLocation().add(0.5, 1, 0.5), EntityType.RABBIT);
        }
    }

    private Block findNearbyFarmland(Player player, java.util.concurrent.ThreadLocalRandom rng) {
        Location base = player.getLocation();
        for (int attempt = 0; attempt < 8; attempt++) {
            int dx = rng.nextInt(-scanRadius, scanRadius + 1);
            int dz = rng.nextInt(-scanRadius, scanRadius + 1);
            int y = player.getWorld().getHighestBlockYAt(base.getBlockX() + dx, base.getBlockZ() + dz);
            Block block = player.getWorld().getBlockAt(base.getBlockX() + dx, y, base.getBlockZ() + dz);
            if (block.getType() == Material.FARMLAND) return block;
        }
        return null;
    }

    private void handleWolfPressure(Player player, java.util.concurrent.ThreadLocalRandom rng) {
        Location loc = player.getLocation();
        long sheepCount = player.getWorld().getNearbyEntities(loc, scanRadius, scanRadius, scanRadius).stream()
                .filter(e -> e.getType() == EntityType.SHEEP).count();
        if (sheepCount < wolfPressureSheepThreshold) return;
        if (rng.nextDouble() >= wolfSpawnChance) return;

        int dx = rng.nextInt(-scanRadius, scanRadius + 1);
        int dz = rng.nextInt(-scanRadius, scanRadius + 1);
        int y = player.getWorld().getHighestBlockYAt(loc.getBlockX() + dx, loc.getBlockZ() + dz);
        Location spawnLoc = new Location(player.getWorld(), loc.getBlockX() + dx + 0.5, y + 1, loc.getBlockZ() + dz + 0.5);
        player.getWorld().spawnEntity(spawnLoc, EntityType.WOLF);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCropGrow(BlockGrowEvent event) {
        if (!CROPS.contains(event.getBlock().getType())) return;

        long bees = event.getBlock().getWorld()
                .getNearbyEntities(event.getBlock().getLocation(), beeSearchRadius, beeSearchRadius, beeSearchRadius).stream()
                .filter(e -> e.getType() == EntityType.BEE).count();

        if (bees == 0 && ThreadLocalRandom.current().nextDouble() < noBeeGrowthPenaltyChance) {
            event.setCancelled(true);
        }
    }
}
