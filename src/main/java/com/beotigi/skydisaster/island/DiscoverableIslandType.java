package com.beotigi.skydisaster.island;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 플레이하다 먼 곳에서 자연스럽게 발견되는 섬들. 퀘스트 마커 없이, 그냥 미니맵 저 멀리에
 * 뭔가 보이면 직접 가보는 방식.
 */
public enum DiscoverableIslandType {

    JUNGLE(Material.GRASS_BLOCK, Material.DIRT) {
        @Override
        List<Block> decorate(World world, Location center, int radius) {
            var rng = ThreadLocalRandom.current();
            for (int i = 0; i < 4; i++) {
                Location loc = randomPoint(center, radius - 2, 1);
                if (loc.getBlock().getType() == Material.AIR) {
                    world.generateTree(loc, TreeType.SMALL_JUNGLE);
                }
            }
            scatter(world, center, radius, Material.SHORT_GRASS, 15);
            return List.of();
        }
    },
    GLACIER(Material.SNOW_BLOCK, Material.PACKED_ICE) {
        @Override
        List<Block> decorate(World world, Location center, int radius) {
            scatter(world, center, radius, Material.ICE, 10);
            var rng = ThreadLocalRandom.current();
            for (int i = 0; i < 4; i++) {
                Location loc = randomPoint(center, radius - 1, 1);
                Block b = loc.getBlock();
                if (b.getType() == Material.AIR && b.getRelative(0, -1, 0).getType() != Material.AIR) {
                    b.setType(Material.SNOW, false);
                }
            }
            return List.of();
        }
    },
    DESERT(Material.SAND, Material.SANDSTONE) {
        @Override
        List<Block> decorate(World world, Location center, int radius) {
            var rng = ThreadLocalRandom.current();
            for (int i = 0; i < 3; i++) {
                Location loc = randomPoint(center, radius - 2, 1);
                if (loc.getBlock().getType() == Material.AIR) {
                    loc.getBlock().setType(Material.CACTUS, false);
                }
            }
            scatter(world, center, radius, Material.DEAD_BUSH, 6);
            return List.of();
        }
    },
    VOLCANO(Material.STONE, Material.BASALT) {
        @Override
        List<Block> decorate(World world, Location center, int radius) {
            // 중앙에 작은 봉우리를 쌓아 크레이터를 만든다.
            int peakHeight = 5;
            for (int h = 1; h <= peakHeight; h++) {
                int ringRadius = Math.max(1, radius / 2 - h);
                for (int dx = -ringRadius; dx <= ringRadius; dx++) {
                    for (int dz = -ringRadius; dz <= ringRadius; dz++) {
                        if (dx * dx + dz * dz > ringRadius * ringRadius) continue;
                        Block block = world.getBlockAt(center.getBlockX() + dx, center.getBlockY() + h, center.getBlockZ() + dz);
                        block.setType(h == peakHeight ? Material.MAGMA_BLOCK : Material.BASALT, false);
                    }
                }
            }
            return List.of();
        }
    },
    FLOWER(Material.GRASS_BLOCK, Material.DIRT) {
        @Override
        List<Block> decorate(World world, Location center, int radius) {
            Material[] flowers = {Material.DANDELION, Material.POPPY, Material.BLUE_ORCHID,
                    Material.ALLIUM, Material.AZURE_BLUET, Material.CORNFLOWER};
            var rng = ThreadLocalRandom.current();
            for (int i = 0; i < 25; i++) {
                Location loc = randomPoint(center, radius - 1, 1);
                Block b = loc.getBlock();
                if (b.getType() == Material.AIR && b.getRelative(0, -1, 0).getType() == Material.GRASS_BLOCK) {
                    b.setType(flowers[rng.nextInt(flowers.length)], false);
                }
            }
            return List.of();
        }
    },
    MUSHROOM(Material.MYCELIUM, Material.DIRT) {
        @Override
        List<Block> decorate(World world, Location center, int radius) {
            var rng = ThreadLocalRandom.current();
            for (int i = 0; i < 3; i++) {
                Location loc = randomPoint(center, radius - 2, 1);
                if (loc.getBlock().getType() == Material.AIR) {
                    world.generateTree(loc, rng.nextBoolean() ? TreeType.RED_MUSHROOM : TreeType.BROWN_MUSHROOM);
                }
            }
            scatter(world, center, radius, Material.RED_MUSHROOM, 8);
            return List.of();
        }
    },
    MINING(Material.STONE, Material.STONE) {
        @Override
        List<Block> decorate(World world, Location center, int radius) {
            var rng = ThreadLocalRandom.current();
            Material[] ores = {Material.COAL_ORE, Material.IRON_ORE, Material.GOLD_ORE,
                    Material.REDSTONE_ORE, Material.LAPIS_ORE, Material.COPPER_ORE};
            for (int i = 0; i < 14; i++) {
                Location loc = randomPoint(center, radius - 1, -rng.nextInt(1, 4));
                Block b = loc.getBlock();
                if (b.getType() == Material.STONE) {
                    b.setType(ores[rng.nextInt(ores.length)], false);
                }
            }
            List<Block> chests = new ArrayList<>();
            Block chestBlock = center.clone().add(0, 1, 0).getBlock();
            chestBlock.setType(Material.CHEST, false);
            chests.add(chestBlock);
            return chests;
        }
    },
    RUIN(Material.GRASS_BLOCK, Material.DIRT) {
        @Override
        List<Block> decorate(World world, Location center, int radius) {
            return buildBrokenWalls(world, center, 5, 3, Material.COBBLESTONE, Material.MOSSY_COBBLESTONE, 1);
        }
    },
    CASTLE(Material.STONE, Material.STONE) {
        @Override
        List<Block> decorate(World world, Location center, int radius) {
            return buildBrokenWalls(world, center, 7, 5, Material.STONE_BRICKS, Material.CRACKED_STONE_BRICKS, 2);
        }
    };

    private final Material topMaterial;
    private final Material subMaterial;

    DiscoverableIslandType(Material topMaterial, Material subMaterial) {
        this.topMaterial = topMaterial;
        this.subMaterial = subMaterial;
    }

    public Material topMaterial() {
        return topMaterial;
    }

    public Material subMaterial() {
        return subMaterial;
    }

    /** 섬 기본 지형 위에 얹는 추가 디테일. 전리품 상자가 있다면 그 블록 목록을 반환한다. */
    abstract List<Block> decorate(World world, Location center, int radius);

    static Location randomPoint(Location center, int radius, int yOffset) {
        var rng = ThreadLocalRandom.current();
        int dx = rng.nextInt(-radius, radius + 1);
        int dz = rng.nextInt(-radius, radius + 1);
        return center.clone().add(dx, yOffset, dz);
    }

    static void scatter(World world, Location center, int radius, Material material, int count) {
        var rng = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            Location loc = randomPoint(center, radius, 1);
            Block block = loc.getBlock();
            if (block.getType() == Material.AIR && block.getRelative(0, -1, 0).getType() != Material.AIR) {
                block.setType(material, false);
            }
        }
    }

    /** 부서진 사각형 벽 - 오래전 누군가 살았던 흔적. 무너진 부분은 랜덤하게 비운다. */
    static List<Block> buildBrokenWalls(World world, Location center, int size, int height,
                                         Material mainMaterial, Material accentMaterial, int chestCount) {
        var rng = ThreadLocalRandom.current();
        int half = size / 2;
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                boolean onEdge = Math.abs(dx) == half || Math.abs(dz) == half;
                if (!onEdge) continue;
                int wallHeight = Math.max(0, height - rng.nextInt(0, 3));
                for (int y = 1; y <= wallHeight; y++) {
                    if (rng.nextDouble() < 0.12) continue; // 무너진 틈
                    Block block = center.clone().add(dx, y, dz).getBlock();
                    block.setType(rng.nextDouble() < 0.3 ? accentMaterial : mainMaterial, false);
                }
            }
        }

        List<Block> chests = new ArrayList<>();
        for (int i = 0; i < chestCount; i++) {
            int dx = rng.nextInt(-half + 1, half);
            int dz = rng.nextInt(-half + 1, half);
            Block chestBlock = center.clone().add(dx, 1, dz).getBlock();
            chestBlock.setType(Material.CHEST, false);
            chests.add(chestBlock);
        }
        return chests;
    }
}
