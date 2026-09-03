package com.beotigi.skydisaster.island;

import com.beotigi.skydisaster.BeotigiPlugin;
import com.beotigi.skydisaster.disaster.VolcanoManager;
import com.beotigi.skydisaster.item.SpecialItemManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 플레이하면서 먼 곳에 새로운 하늘섬이 자연스럽게 나타난다. 안내 없음 - 미니맵/탐험으로 발견.
 */
public class IslandDiscoveryManager {

    private static final int ISLAND_RADIUS = 11;

    private final BeotigiPlugin plugin;
    private final VolcanoManager volcanoManager;
    private SpecialItemManager specialItemManager;

    private final double chancePerInterval;
    private final int minDistance;
    private final int maxDistance;
    private final int minGapBetweenIslands;

    private final List<Location> discoveredAnchors = new ArrayList<>();
    private BukkitTask task;

    public IslandDiscoveryManager(BeotigiPlugin plugin, VolcanoManager volcanoManager) {
        this.plugin = plugin;
        this.volcanoManager = volcanoManager;
        var cfg = plugin.getConfig();
        this.chancePerInterval = cfg.getDouble("discovery.chance-per-interval", 0.25);
        this.minDistance = cfg.getInt("discovery.min-distance", 300);
        this.maxDistance = cfg.getInt("discovery.max-distance", 1200);
        this.minGapBetweenIslands = cfg.getInt("discovery.min-gap-between-islands", 150);
    }

    public void linkSpecialItems(SpecialItemManager specialItemManager) {
        this.specialItemManager = specialItemManager;
    }

    public void start() {
        // 10분마다 확률을 굴린다.
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 12000L, 12000L);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    public List<Location> getDiscoveredAnchors() {
        return List.copyOf(discoveredAnchors);
    }

    private void tick() {
        var rng = ThreadLocalRandom.current();
        for (World world : plugin.getServer().getWorlds()) {
            if (world.getPlayers().isEmpty()) continue;
            if (rng.nextDouble() >= chancePerInterval) continue;

            Location anchor = pickAnchor(world, rng);
            if (anchor == null) continue;

            DiscoverableIslandType[] types = DiscoverableIslandType.values();
            DiscoverableIslandType type = types[rng.nextInt(types.length)];
            spawnIsland(world, anchor, type);
            return;
        }
    }

    private Location pickAnchor(World world, java.util.concurrent.ThreadLocalRandom rng) {
        for (int attempt = 0; attempt < 10; attempt++) {
            double angle = rng.nextDouble(0, Math.PI * 2);
            double dist = rng.nextDouble(minDistance, maxDistance);
            int x = (int) (Math.cos(angle) * dist);
            int z = (int) (Math.sin(angle) * dist);
            int y = rng.nextInt(90, 140);
            Location candidate = new Location(world, x + 0.5, y, z + 0.5);

            boolean tooClose = false;
            for (Location existing : discoveredAnchors) {
                if (existing.getWorld().equals(world) && existing.distance(candidate) < minGapBetweenIslands) {
                    tooClose = true;
                    break;
                }
            }
            if (!tooClose) return candidate;
        }
        return null;
    }

    public void spawnIsland(World world, Location anchor, DiscoverableIslandType type) {
        buildBase(world, anchor, type);
        List<Block> chests = type.decorate(world, anchor, ISLAND_RADIUS);
        for (Block chestBlock : chests) {
            fillLoot(chestBlock, type);
        }

        if (type == DiscoverableIslandType.VOLCANO) {
            volcanoManager.registerVolcano(anchor.clone());
        }

        discoveredAnchors.add(anchor.clone());
    }

    private void buildBase(World world, Location center, DiscoverableIslandType type) {
        int baseY = center.getBlockY();
        for (int dx = -ISLAND_RADIUS; dx <= ISLAND_RADIUS; dx++) {
            for (int dz = -ISLAND_RADIUS; dz <= ISLAND_RADIUS; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > ISLAND_RADIUS) continue;

                double edgeFactor = 1.0 - (dist / ISLAND_RADIUS);
                int depth = Math.max(1, (int) Math.round(6 * edgeFactor));

                for (int i = 0; i < depth; i++) {
                    int y = baseY - i;
                    Block block = world.getBlockAt(center.getBlockX() + dx, y, center.getBlockZ() + dz);
                    if (i == 0) {
                        block.setType(type.topMaterial(), false);
                    } else if (i == 1) {
                        block.setType(type.subMaterial(), false);
                    } else {
                        block.setType(Material.STONE, false);
                    }
                }
            }
        }
    }

    private void fillLoot(Block chestBlock, DiscoverableIslandType type) {
        if (!(chestBlock.getState() instanceof Chest chest)) return;
        Inventory inv = chest.getBlockInventory();
        var rng = ThreadLocalRandom.current();

        switch (type) {
            case MINING -> {
                inv.addItem(new ItemStack(Material.RAW_IRON, rng.nextInt(2, 5)));
                inv.addItem(new ItemStack(Material.COAL, rng.nextInt(3, 8)));
                inv.addItem(new ItemStack(Material.TORCH, rng.nextInt(4, 10)));
            }
            case RUIN -> {
                inv.addItem(new ItemStack(Material.BREAD, rng.nextInt(2, 5)));
                inv.addItem(new ItemStack(Material.IRON_INGOT, rng.nextInt(1, 4)));
                if (rng.nextDouble() < 0.3) inv.addItem(new ItemStack(Material.BOOKSHELF, 1));
            }
            case CASTLE -> {
                inv.addItem(new ItemStack(Material.GOLD_INGOT, rng.nextInt(2, 6)));
                inv.addItem(new ItemStack(Material.EMERALD, rng.nextInt(1, 4)));
                if (rng.nextDouble() < 0.4) inv.addItem(new ItemStack(Material.DIAMOND, rng.nextInt(1, 3)));
            }
            default -> {
                return;
            }
        }

        if (specialItemManager != null && rng.nextDouble() < 0.2) {
            inv.addItem(specialItemManager.randomItem());
        }
    }
}
