package com.beotigi.skydisaster.disaster;

import com.beotigi.skydisaster.BeotigiPlugin;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 공중에 붕 뜬 채로 멀리 뻗은 다리/구조물(지지대 없는 구간)을 찾아,
 * 폭풍이 심할 때 가장자리 블록 몇 개를 무너뜨린다.
 * "야 우리 다리 왜 무너져?" - 플레이어가 지은 구조에 반응하는 재해.
 */
public class StructuralCollapseManager {

    private static final BlockFace[] NEIGHBORS = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST,
            BlockFace.UP, BlockFace.DOWN
    };
    private static final int MIN_MASS_SIZE = 10;
    private static final int GAP_THRESHOLD = 3;

    private final BeotigiPlugin plugin;
    private final WeatherManager weatherManager;
    private final int maxChunksPerScan;
    private final int supportSearchLimit;
    private BukkitTask task;

    public StructuralCollapseManager(BeotigiPlugin plugin, WeatherManager weatherManager) {
        this.plugin = plugin;
        this.weatherManager = weatherManager;
        int intervalSeconds = plugin.getConfig().getInt("structure.scan-interval-seconds", 15);
        this.maxChunksPerScan = plugin.getConfig().getInt("structure.max-chunks-per-scan", 6);
        this.supportSearchLimit = plugin.getConfig().getInt("structure.support-search-limit", 250);
        this.intervalTicks = intervalSeconds * 20L;
    }

    private final long intervalTicks;

    public void start() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::scan, intervalTicks, intervalTicks);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    private void scan() {
        for (World world : plugin.getServer().getWorlds()) {
            WeatherManager.Phase phase = weatherManager.getPhase(world);
            if (phase != WeatherManager.Phase.STORM && phase != WeatherManager.Phase.SEVERE_STORM) continue;

            List<Chunk> candidateChunks = new ArrayList<>();
            for (Player player : world.getPlayers()) {
                Chunk c = player.getLocation().getChunk();
                if (!candidateChunks.contains(c)) candidateChunks.add(c);
            }
            java.util.Collections.shuffle(candidateChunks);
            int limit = Math.min(maxChunksPerScan, candidateChunks.size());
            double collapseChance = phase == WeatherManager.Phase.SEVERE_STORM ? 0.5 : 0.2;

            for (int i = 0; i < limit; i++) {
                scanChunk(candidateChunks.get(i), collapseChance);
            }
        }
    }

    private void scanChunk(Chunk chunk, double collapseChance) {
        var rng = ThreadLocalRandom.current();
        World world = chunk.getWorld();

        for (int sample = 0; sample < 5; sample++) {
            int x = chunk.getX() * 16 + rng.nextInt(16);
            int z = chunk.getZ() * 16 + rng.nextInt(16);
            int topY = world.getHighestBlockYAt(x, z);
            Block top = world.getBlockAt(x, topY, z);
            if (top.getType() == Material.AIR || !hasGapBelow(top, GAP_THRESHOLD)) continue;

            Set<Block> mass = findSuspendedMass(top);
            if (mass.size() < MIN_MASS_SIZE) continue;
            if (rng.nextDouble() >= collapseChance) continue;

            shedBlocks(mass, rng);
            return; // 스캔당 한 구조물만 처리 (성능 보호)
        }
    }

    private boolean hasGapBelow(Block block, int gap) {
        for (int i = 1; i <= gap; i++) {
            if (block.getRelative(0, -i, 0).getType() != Material.AIR) return false;
        }
        return true;
    }

    /** 지지대 없이 공중에 뜬 연결된 블록 덩어리를 BFS로 찾는다 (한계치 내에서). */
    private Set<Block> findSuspendedMass(Block start) {
        Set<Block> visited = new HashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty() && visited.size() < supportSearchLimit) {
            Block current = queue.poll();
            for (BlockFace face : NEIGHBORS) {
                Block next = current.getRelative(face);
                if (visited.contains(next)) continue;
                Material nextType = next.getType();
                if (nextType == Material.AIR || nextType == Material.WATER || nextType == Material.LAVA) continue;
                if (!hasGapBelow(next, 1)) continue; // 지면에 닿은 부분은 덩어리에서 제외 (그라운드로 판단)
                visited.add(next);
                queue.add(next);
                if (visited.size() >= supportSearchLimit) break;
            }
        }
        return visited;
    }

    private void shedBlocks(Set<Block> mass, java.util.concurrent.ThreadLocalRandom rng) {
        List<Block> blocks = new ArrayList<>(mass);
        int shedCount = Math.min(blocks.size(), rng.nextInt(2, 5));
        World world = blocks.get(0).getWorld();

        world.playSound(blocks.get(0).getLocation(), Sound.BLOCK_STONE_BREAK, 1.2f, 0.7f);
        for (int i = 0; i < shedCount; i++) {
            Block b = blocks.get(rng.nextInt(blocks.size()));
            if (b.getType() == Material.AIR) continue;

            FallingBlock fb = b.getWorld().spawnFallingBlock(b.getLocation().add(0.5, 0.2, 0.5), b.getBlockData());
            fb.setDropItem(true);
            fb.setVelocity(new Vector(rng.nextDouble(-0.2, 0.2), -0.1, rng.nextDouble(-0.2, 0.2)));
            b.setType(Material.AIR, false);
        }
    }
}
