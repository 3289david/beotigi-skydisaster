package com.beotigi.skydisaster.island;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 세 시작 섬은 일부러 서로 다르게 설계한다 - 자연스러운 협력을 유도하기 위해.
 */
public enum IslandType {

    FOREST(0, 100, 0, Material.GRASS_BLOCK, Material.DIRT) {
        @Override
        void decorate(World world, Location center, int radius) {
            var rng = ThreadLocalRandom.current();
            for (int i = 0; i < 5; i++) {
                int dx = rng.nextInt(-(radius - 2), radius - 1);
                int dz = rng.nextInt(-(radius - 2), radius - 1);
                Location treeLoc = center.clone().add(dx, 1, dz);
                if (treeLoc.getBlock().getType() != Material.AIR) continue;
                world.generateTree(treeLoc, rng.nextBoolean() ? TreeType.TREE : TreeType.BIG_TREE);
            }
            scatterGroundCover(world, center, radius, Material.SHORT_GRASS, 12);
        }
    },
    ROCK(300, 100, 0, Material.STONE, Material.STONE) {
        @Override
        void decorate(World world, Location center, int radius) {
            var rng = ThreadLocalRandom.current();
            // 노출된 광맥
            for (int i = 0; i < 10; i++) {
                int dx = rng.nextInt(-radius, radius + 1);
                int dz = rng.nextInt(-radius, radius + 1);
                Block block = center.clone().add(dx, -1, dz).getBlock();
                if (block.getType() != Material.STONE) continue;
                block.setType(rng.nextBoolean() ? Material.COAL_ORE : Material.IRON_ORE, false);
            }
            // 표면 돌 무더기
            for (int i = 0; i < 6; i++) {
                int dx = rng.nextInt(-radius, radius + 1);
                int dz = rng.nextInt(-radius, radius + 1);
                Block block = center.clone().add(dx, 1, dz).getBlock();
                if (block.getType() == Material.AIR) {
                    block.setType(rng.nextBoolean() ? Material.COBBLESTONE : Material.ANDESITE, false);
                }
            }
        }
    },
    PLAINS(-300, 100, 0, Material.GRASS_BLOCK, Material.DIRT) {
        @Override
        void decorate(World world, Location center, int radius) {
            var rng = ThreadLocalRandom.current();
            // 작은 농장
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    Block farmland = center.clone().add(dx, 0, dz).getBlock();
                    if (farmland.getType() != Material.GRASS_BLOCK) continue;
                    farmland.setType(Material.FARMLAND, false);
                    Block crop = farmland.getRelative(0, 1, 0);
                    crop.setType(rng.nextBoolean() ? Material.WHEAT : Material.CARROTS, false);
                }
            }
            scatterGroundCover(world, center, radius, Material.DANDELION, 6);

            for (EntityType type : new EntityType[]{EntityType.SHEEP, EntityType.CHICKEN}) {
                Location spawnLoc = center.clone().add(rng.nextInt(-3, 4), 1, rng.nextInt(-3, 4));
                world.spawnEntity(spawnLoc, type);
            }
        }
    };

    private final int anchorX;
    private final int anchorY;
    private final int anchorZ;
    private final Material topMaterial;
    private final Material subMaterial;

    IslandType(int anchorX, int anchorY, int anchorZ, Material topMaterial, Material subMaterial) {
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.anchorZ = anchorZ;
        this.topMaterial = topMaterial;
        this.subMaterial = subMaterial;
    }

    public Location anchor(World world) {
        return new Location(world, anchorX + 0.5, anchorY, anchorZ + 0.5);
    }

    public Material topMaterial() {
        return topMaterial;
    }

    public Material subMaterial() {
        return subMaterial;
    }

    abstract void decorate(World world, Location center, int radius);

    void scatterGroundCover(World world, Location center, int radius, Material material, int count) {
        var rng = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            int dx = rng.nextInt(-radius, radius + 1);
            int dz = rng.nextInt(-radius, radius + 1);
            Block block = center.clone().add(dx, 1, dz).getBlock();
            if (block.getType() == Material.AIR && block.getRelative(0, -1, 0).getType() != Material.AIR) {
                block.setType(material, false);
            }
        }
    }
}
