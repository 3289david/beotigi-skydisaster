package com.beotigi.skydisaster.disaster;

import com.beotigi.skydisaster.BeotigiPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.FallingBlock;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 처음엔 그냥 평범한 섬. 그러다 연기 -> 작은 용암 -> 폭발 -> 주변 섬까지 피해, 순서로 진행되고
 * 다시 잠든다. 어떤 UI도 이 진행 상황을 알려주지 않는다.
 */
public class VolcanoManager {

    private enum Stage { DORMANT, SMOKING, LAVA, ERUPTING, COOLDOWN }

    private static class Site {
        final Location center;
        Stage stage = Stage.DORMANT;
        long stageEndsAt;
        int lavaPlaced = 0;

        Site(Location center) {
            this.center = center;
            this.stageEndsAt = System.currentTimeMillis() + randomMinutes(3, 8);
        }
    }

    private static long randomMinutes(int minMinutes, int maxMinutes) {
        int minutes = ThreadLocalRandom.current().nextInt(minMinutes, maxMinutes + 1);
        return minutes * 60_000L;
    }

    private final BeotigiPlugin plugin;
    private final List<Site> sites = new ArrayList<>();
    private BukkitTask task;

    public VolcanoManager(BeotigiPlugin plugin) {
        this.plugin = plugin;
    }

    public void registerVolcano(Location craterCenter) {
        sites.add(new Site(craterCenter));
    }

    public void start() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 100L, 100L);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Site site : sites) {
            if (site.center.getWorld() == null || site.center.getWorld().getPlayers().isEmpty()) continue;

            switch (site.stage) {
                case DORMANT -> {
                    if (now >= site.stageEndsAt) advance(site, Stage.SMOKING, randomMinutes(2, 5));
                }
                case SMOKING -> {
                    ambientSmoke(site);
                    if (now >= site.stageEndsAt) advance(site, Stage.LAVA, randomMinutes(1, 3));
                }
                case LAVA -> {
                    trickleLava(site);
                    if (now >= site.stageEndsAt) erupt(site);
                }
                case ERUPTING -> advance(site, Stage.COOLDOWN, randomMinutes(5, 10));
                case COOLDOWN -> {
                    if (ThreadLocalRandom.current().nextDouble() < 0.3) ambientSmoke(site);
                    if (now >= site.stageEndsAt) advance(site, Stage.DORMANT, randomMinutes(3, 8));
                }
            }
        }
    }

    private void advance(Site site, Stage next, long durationMillis) {
        site.stage = next;
        site.stageEndsAt = System.currentTimeMillis() + durationMillis;
        if (next == Stage.LAVA) site.lavaPlaced = 0;
    }

    private void ambientSmoke(Site site) {
        World world = site.center.getWorld();
        world.spawnParticle(Particle.LARGE_SMOKE, site.center.clone().add(0, 2, 0), 6, 1, 0.5, 1, 0.01);
        if (ThreadLocalRandom.current().nextDouble() < 0.2) {
            world.playSound(site.center, Sound.BLOCK_FIRE_AMBIENT, 1.5f, 0.5f);
        }
    }

    private void trickleLava(Site site) {
        if (site.lavaPlaced >= 20) return;
        World world = site.center.getWorld();
        var rng = ThreadLocalRandom.current();
        if (rng.nextDouble() >= 0.5) return;

        int dx = rng.nextInt(-4, 5);
        int dz = rng.nextInt(-4, 5);
        int topY = world.getHighestBlockYAt(site.center.getBlockX() + dx, site.center.getBlockZ() + dz);
        Block block = world.getBlockAt(site.center.getBlockX() + dx, topY + 1, site.center.getBlockZ() + dz);
        if (block.getType() == Material.AIR) {
            block.setType(Material.LAVA, false);
            site.lavaPlaced++;
            world.spawnParticle(Particle.LAVA, block.getLocation(), 3, 0.2, 0.2, 0.2, 0);
        }
    }

    private void erupt(Site site) {
        World world = site.center.getWorld();
        var rng = ThreadLocalRandom.current();

        world.playSound(site.center, Sound.ENTITY_GENERIC_EXPLODE, 4f, 0.6f);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, site.center.clone().add(0, 2, 0), 2);
        world.spawnParticle(Particle.LARGE_SMOKE, site.center.clone().add(0, 2, 0), 60, 3, 2, 3, 0.05);

        // 용암 폭탄 - 주변 섬까지 날아가 불을 낼 수 있다.
        for (int i = 0; i < 8; i++) {
            Vector dir = new Vector(rng.nextDouble(-1, 1), rng.nextDouble(0.6, 1.2), rng.nextDouble(-1, 1)).normalize();
            Location launchPoint = site.center.clone().add(0, 3, 0);
            FallingBlock bomb = world.spawnFallingBlock(launchPoint, Material.MAGMA_BLOCK.createBlockData());
            bomb.setDropItem(false);
            bomb.setHurtEntities(true);
            bomb.setVelocity(dir.multiply(1.6));
        }

        advance(site, Stage.ERUPTING, 5_000L);
    }
}
