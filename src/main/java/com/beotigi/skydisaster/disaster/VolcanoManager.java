package com.beotigi.skydisaster.disaster;

import com.beotigi.skydisaster.BeotigiPlugin;
import com.beotigi.skydisaster.util.ChatAnnouncer;
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
 *
 * "3~8분 후 정확히 다음 단계로" 같은 정해진 타이머가 아니라, 다른 재해들과 똑같이
 * 매 체크마다 확률을 굴리는 방식이다 - 그래서 어떤 화산은 금방 넘어가고, 어떤 화산은
 * 한참을 그대로 있다가 갑자기 넘어간다. 예측할 수 없는 게 포인트.
 */
public class VolcanoManager {

    private enum Stage { DORMANT, SMOKING, LAVA, ERUPTING, COOLDOWN }

    private static class Site {
        final Location center;
        Stage stage = Stage.DORMANT;
        int lavaPlaced = 0;
        boolean eruptionWarned = false;

        Site(Location center) {
            this.center = center;
        }
    }

    private final BeotigiPlugin plugin;
    private final List<Site> sites = new ArrayList<>();
    private BukkitTask task;

    // 매 체크(5초)마다 다음 단계로 넘어갈 확률. 값이 작을수록 그 단계에 오래 머문다.
    private final double dormantAdvanceChance;
    private final double smokingAdvanceChance;
    private final double lavaAdvanceChance;
    private final double cooldownAdvanceChance;

    public VolcanoManager(BeotigiPlugin plugin) {
        this.plugin = plugin;
        var cfg = plugin.getConfig();
        this.dormantAdvanceChance = cfg.getDouble("volcano.dormant-advance-chance", 0.015);
        this.smokingAdvanceChance = cfg.getDouble("volcano.smoking-advance-chance", 0.025);
        this.lavaAdvanceChance = cfg.getDouble("volcano.lava-advance-chance", 0.04);
        this.cooldownAdvanceChance = cfg.getDouble("volcano.cooldown-advance-chance", 0.012);
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
        var rng = ThreadLocalRandom.current();
        for (Site site : sites) {
            if (site.center.getWorld() == null || site.center.getWorld().getPlayers().isEmpty()) continue;

            switch (site.stage) {
                case DORMANT -> {
                    if (rng.nextDouble() < dormantAdvanceChance) {
                        site.stage = Stage.SMOKING;
                        ChatAnnouncer.announce(site.center.getWorld(), "저 산 위로 연기 같은 게 보인다...");
                    }
                }
                case SMOKING -> {
                    ambientSmoke(site);
                    if (rng.nextDouble() < smokingAdvanceChance) {
                        site.stage = Stage.LAVA;
                        site.lavaPlaced = 0;
                        ChatAnnouncer.announce(site.center.getWorld(), "탄내가 점점 심해진다.");
                    }
                }
                case LAVA -> {
                    trickleLava(site);
                    if (!site.eruptionWarned && rng.nextDouble() < lavaAdvanceChance) {
                        site.eruptionWarned = true;
                        ChatAnnouncer.announce(site.center.getWorld(), "땅이 울리기 시작한다!");
                        long delayTicks = rng.nextLong(60L, 361L); // 3~18초, 매번 다르게
                        plugin.getServer().getScheduler().runTaskLater(plugin, () -> erupt(site), delayTicks);
                    }
                }
                case ERUPTING -> site.stage = Stage.COOLDOWN;
                case COOLDOWN -> {
                    if (rng.nextDouble() < 0.3) ambientSmoke(site);
                    if (rng.nextDouble() < cooldownAdvanceChance) {
                        site.stage = Stage.DORMANT;
                    }
                }
            }
        }
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
        site.eruptionWarned = false;

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

        site.stage = Stage.ERUPTING;
    }
}
