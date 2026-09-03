package com.beotigi.skydisaster.island;

import com.beotigi.skydisaster.BeotigiPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public class IslandManager {

    private static final int RADIUS = 8;

    private final BeotigiPlugin plugin;

    public IslandManager(BeotigiPlugin plugin) {
        this.plugin = plugin;
    }

    public void createIsland(Player player, IslandType type) {
        World world = player.getWorld();
        Location center = type.anchor(world);
        buildBase(world, center, type);
        type.decorate(world, center, RADIUS);

        Location spawn = center.clone().add(0, 2, 0);
        player.teleport(spawn);
        player.sendMessage("§7... §f새로운 땅에 발을 디뎠다.");
    }

    private void buildBase(World world, Location center, IslandType type) {
        int baseY = center.getBlockY();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > RADIUS) continue;

                double edgeFactor = 1.0 - (dist / RADIUS);
                int depth = Math.max(1, (int) Math.round(5 * edgeFactor));

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
}
