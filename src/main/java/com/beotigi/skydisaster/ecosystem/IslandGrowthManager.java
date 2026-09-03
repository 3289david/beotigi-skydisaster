package com.beotigi.skydisaster.ecosystem;

import com.beotigi.skydisaster.BeotigiPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 플레이어가 모든 걸 직접 만들 필요는 없다. 시간이 지나면 꽃이 피고, 작은 연못이 생기고,
 * 동물이 늘어난다. 몇 시간 뒤 돌아보면 흙덩어리가 진짜 살아있는 섬이 되어 있는 느낌.
 */
public class IslandGrowthManager {

    private static final Material[] FLOWERS = {
            Material.DANDELION, Material.POPPY, Material.SHORT_GRASS, Material.SHORT_GRASS
    };
    private static final Set<EntityType> PASSIVE_ANIMALS = Set.of(
            EntityType.COW, EntityType.SHEEP, EntityType.PIG, EntityType.CHICKEN
    );

    private final BeotigiPlugin plugin;
    private final int radius = 14;
    private final double flowerSpreadChance;
    private final double pondFormationChance;
    private final double animalGrowthChance;
    private final int maxAnimals;
    private BukkitTask task;

    public IslandGrowthManager(BeotigiPlugin plugin) {
        this.plugin = plugin;
        var cfg = plugin.getConfig();
        this.flowerSpreadChance = cfg.getDouble("growth.flower-spread-chance", 0.15);
        this.pondFormationChance = cfg.getDouble("growth.pond-formation-chance", 0.05);
        this.animalGrowthChance = cfg.getDouble("growth.animal-growth-chance", 0.1);
        this.maxAnimals = cfg.getInt("growth.max-passive-animals-per-scan-area", 10);
        this.intervalTicks = cfg.getInt("growth.scan-interval-seconds", 45) * 20L;
    }

    private final long intervalTicks;

    public void start() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, intervalTicks, intervalTicks);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    private void tick() {
        var rng = ThreadLocalRandom.current();
        for (World world : plugin.getServer().getWorlds()) {
            for (Player player : world.getPlayers()) {
                if (rng.nextDouble() < flowerSpreadChance) spreadFlowers(player, rng);
                if (rng.nextDouble() < pondFormationChance) formPond(player, rng);
                if (rng.nextDouble() < animalGrowthChance) growAnimals(player, rng);
            }
        }
    }

    private void spreadFlowers(Player player, java.util.concurrent.ThreadLocalRandom rng) {
        Location base = player.getLocation();
        World world = player.getWorld();
        for (int attempt = 0; attempt < 5; attempt++) {
            int dx = rng.nextInt(-radius, radius + 1);
            int dz = rng.nextInt(-radius, radius + 1);
            int y = world.getHighestBlockYAt(base.getBlockX() + dx, base.getBlockZ() + dz);
            Block ground = world.getBlockAt(base.getBlockX() + dx, y, base.getBlockZ() + dz);
            Block above = ground.getRelative(0, 1, 0);
            if (ground.getType() == Material.GRASS_BLOCK && above.getType() == Material.AIR) {
                above.setType(FLOWERS[rng.nextInt(FLOWERS.length)], false);
                return;
            }
        }
    }

    private void formPond(Player player, java.util.concurrent.ThreadLocalRandom rng) {
        Location base = player.getLocation();
        World world = player.getWorld();
        for (int attempt = 0; attempt < 6; attempt++) {
            int dx = rng.nextInt(-radius, radius + 1);
            int dz = rng.nextInt(-radius, radius + 1);
            int y = world.getHighestBlockYAt(base.getBlockX() + dx, base.getBlockZ() + dz);
            Block center = world.getBlockAt(base.getBlockX() + dx, y, base.getBlockZ() + dz);
            if (center.getType() != Material.GRASS_BLOCK && center.getType() != Material.DIRT) continue;

            int higherNeighbors = 0;
            for (int[] off : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int ny = world.getHighestBlockYAt(center.getX() + off[0], center.getZ() + off[1]);
                if (ny > y) higherNeighbors++;
            }
            if (higherNeighbors < 2) continue;

            for (int cx = -1; cx <= 1; cx++) {
                for (int cz = -1; cz <= 1; cz++) {
                    Block b = center.getRelative(cx, 0, cz);
                    if (b.getType() == Material.GRASS_BLOCK || b.getType() == Material.DIRT) {
                        b.setType(Material.WATER, false);
                    }
                }
            }
            return;
        }
    }

    private void growAnimals(Player player, java.util.concurrent.ThreadLocalRandom rng) {
        Location base = player.getLocation();
        World world = player.getWorld();
        long animalCount = world.getNearbyEntities(base, radius, radius, radius).stream()
                .filter(e -> PASSIVE_ANIMALS.contains(e.getType())).count();
        if (animalCount >= maxAnimals) return;

        for (int attempt = 0; attempt < 6; attempt++) {
            int dx = rng.nextInt(-radius, radius + 1);
            int dz = rng.nextInt(-radius, radius + 1);
            int y = world.getHighestBlockYAt(base.getBlockX() + dx, base.getBlockZ() + dz);
            Block ground = world.getBlockAt(base.getBlockX() + dx, y, base.getBlockZ() + dz);
            if (ground.getType() == Material.GRASS_BLOCK) {
                EntityType[] options = {EntityType.COW, EntityType.SHEEP, EntityType.PIG, EntityType.CHICKEN};
                world.spawnEntity(ground.getLocation().add(0.5, 1, 0.5), options[rng.nextInt(options.length)]);
                return;
            }
        }
    }
}
