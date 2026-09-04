package com.beotigi.skydisaster.disaster;

import com.beotigi.skydisaster.BeotigiPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 후반부 이벤트: 하늘이 어두워지고 섬 가장자리부터 블록이 떨어져 나가기 시작한다.
 * "야 야 야 우리 섬 없어지는데?" 를 유발하는 것이 목적. 총 삭제량에 안전 상한을 둔다.
 */
public class VoidStormManager {

    private final BeotigiPlugin plugin;
    private final double chancePerHour;
    private final int durationSeconds;
    private final int erosionRadius;
    private static final int MAX_BLOCKS_PER_EVENT = 150;
    private static final int MAX_REMOVALS_PER_TICK_PER_PLAYER = 2;

    private final Set<UUID> activeWorlds = new HashSet<>();
    private int removedThisEvent = 0;
    private int secondsRemaining = 0;

    private BukkitTask rollTask;
    private BukkitTask stormTask;

    public VoidStormManager(BeotigiPlugin plugin) {
        this.plugin = plugin;
        this.chancePerHour = plugin.getConfig().getDouble("voidstorm.chance-per-hour", 0.03);
        this.durationSeconds = plugin.getConfig().getInt("voidstorm.duration-seconds", 60);
        this.erosionRadius = plugin.getConfig().getInt("voidstorm.erosion-radius", 20);
    }

    public void start() {
        rollTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::rollForEvent, 1200L, 1200L);
    }

    public void stop() {
        if (rollTask != null) rollTask.cancel();
        if (stormTask != null) stormTask.cancel();
    }

    public boolean isActive() {
        return stormTask != null && !activeWorlds.isEmpty();
    }

    public void forceStart() {
        if (isActive()) return;
        beginEvent();
    }

    private void rollForEvent() {
        if (isActive()) return;
        if (ThreadLocalRandom.current().nextDouble() >= chancePerHour / 60.0) return;
        beginEvent();
    }

    private void beginEvent() {
        activeWorlds.clear();
        for (World world : plugin.getServer().getWorlds()) {
            if (!world.getPlayers().isEmpty()) activeWorlds.add(world.getUID());
        }
        if (activeWorlds.isEmpty()) return;

        removedThisEvent = 0;
        secondsRemaining = durationSeconds;
        for (UUID wid : activeWorlds) {
            World world = plugin.getServer().getWorld(wid);
            if (world != null) {
                world.setStorm(true);
                com.beotigi.skydisaster.util.ChatAnnouncer.announce(world, "하늘이 이상하게 어두워진다...");
            }
        }

        // 몇 초 뒤부터 실제로 가장자리가 깎여나가기 시작한다.
        stormTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 100L, 20L);
    }

    private void tick() {
        secondsRemaining--;
        if (secondsRemaining <= 0 || removedThisEvent >= MAX_BLOCKS_PER_EVENT) {
            if (stormTask != null) stormTask.cancel();
            activeWorlds.clear();
            return;
        }

        var rng = ThreadLocalRandom.current();
        for (UUID wid : activeWorlds) {
            World world = plugin.getServer().getWorld(wid);
            if (world == null) continue;

            for (Player player : world.getPlayers()) {
                int erosions = 0;
                for (int attempt = 0; attempt < 10 && erosions < MAX_REMOVALS_PER_TICK_PER_PLAYER
                        && removedThisEvent < MAX_BLOCKS_PER_EVENT; attempt++) {
                    Block edge = findEdgeBlock(player, rng);
                    if (edge == null) continue;
                    world.spawnParticle(Particle.SMOKE, edge.getLocation().add(0.5, 0.5, 0.5), 8, 0.3, 0.3, 0.3, 0.02);
                    world.playSound(edge.getLocation(), Sound.BLOCK_STONE_BREAK, 0.5f, 0.6f);
                    edge.setType(Material.AIR, false);
                    erosions++;
                    removedThisEvent++;
                }
            }
        }
    }

    /** 아래로 3칸 이상 비어있어 공허에 노출된 블록을 랜덤 반경 내에서 찾는다. */
    private Block findEdgeBlock(Player player, java.util.concurrent.ThreadLocalRandom rng) {
        Location base = player.getLocation();
        World world = player.getWorld();
        for (int attempt = 0; attempt < 12; attempt++) {
            int dx = rng.nextInt(-erosionRadius, erosionRadius + 1);
            int dz = rng.nextInt(-erosionRadius, erosionRadius + 1);
            int topY = world.getHighestBlockYAt(base.getBlockX() + dx, base.getBlockZ() + dz);
            Block candidate = world.getBlockAt(base.getBlockX() + dx, topY, base.getBlockZ() + dz);
            if (candidate.getType() == Material.AIR || candidate.getType() == Material.BEDROCK) continue;
            if (candidate.getType() == Material.WATER || candidate.getType() == Material.LAVA) continue;

            boolean exposedBelow = true;
            for (int i = 1; i <= 3; i++) {
                if (candidate.getRelative(0, -i, 0).getType() != Material.AIR) {
                    exposedBelow = false;
                    break;
                }
            }
            if (exposedBelow) return candidate;
        }
        return null;
    }
}
