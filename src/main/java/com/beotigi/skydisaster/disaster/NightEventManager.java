package com.beotigi.skydisaster.disaster;

import com.beotigi.skydisaster.BeotigiPlugin;
import com.beotigi.skydisaster.creature.SkyCreatureManager;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 밤이 되면 평범한 마크보다 조금 더 위험해진다. 사전 안내는 없다.
 * 매일 밤 시작 시 한 번, 각 이벤트를 독립적으로 굴린다 (여러 개가 겹칠 수도 있다).
 */
public class NightEventManager {

    private final BeotigiPlugin plugin;
    private final MeteorManager meteorManager;
    private final SkyCreatureManager skyCreatureManager;

    private final double bloodMoonChance;
    private final double meteorShowerChance;
    private final double fogChance;
    private final double batSwarmChance;

    private final Map<UUID, Long> lastHandledNight = new HashMap<>();
    private BukkitTask task;

    public NightEventManager(BeotigiPlugin plugin, MeteorManager meteorManager, SkyCreatureManager skyCreatureManager) {
        this.plugin = plugin;
        this.meteorManager = meteorManager;
        this.skyCreatureManager = skyCreatureManager;
        var cfg = plugin.getConfig();
        this.bloodMoonChance = cfg.getDouble("night-event.blood-moon-chance", 0.05);
        this.meteorShowerChance = cfg.getDouble("night-event.meteor-shower-chance", 0.04);
        this.fogChance = cfg.getDouble("night-event.fog-chance", 0.08);
        this.batSwarmChance = cfg.getDouble("night-event.bat-swarm-chance", 0.1);
    }

    public void start() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 100L, 100L);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    private void tick() {
        for (World world : plugin.getServer().getWorlds()) {
            if (world.getPlayers().isEmpty()) continue;
            long time = world.getTime();
            boolean isNight = time >= 13000 && time <= 23000;
            if (!isNight) continue;

            long day = world.getFullTime() / 24000L;
            UUID worldId = world.getUID();
            if (lastHandledNight.getOrDefault(worldId, -1L) == day) continue;
            lastHandledNight.put(worldId, day);

            rollNightEvents(world);
        }
    }

    private void rollNightEvents(World world) {
        var rng = ThreadLocalRandom.current();
        List<Player> players = world.getPlayers();
        if (players.isEmpty()) return;

        if (rng.nextDouble() < bloodMoonChance) triggerBloodMoon(world, players, rng);
        if (rng.nextDouble() < meteorShowerChance) meteorManager.startMeteorShower(world);
        if (rng.nextDouble() < fogChance) triggerFog(players, rng);
        if (rng.nextDouble() < batSwarmChance) {
            Player target = players.get(rng.nextInt(players.size()));
            skyCreatureManager.spawnBatSwarm(target);
        }
    }

    private void triggerBloodMoon(World world, List<Player> players, java.util.concurrent.ThreadLocalRandom rng) {
        EntityType[] hostiles = {EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER};
        for (Player player : players) {
            int spawnCount = rng.nextInt(3, 7);
            for (int i = 0; i < spawnCount; i++) {
                int dx = rng.nextInt(-20, 21);
                int dz = rng.nextInt(-20, 21);
                int y = world.getHighestBlockYAt(player.getLocation().getBlockX() + dx, player.getLocation().getBlockZ() + dz);
                world.spawnEntity(
                        new org.bukkit.Location(world, player.getLocation().getBlockX() + dx + 0.5, y + 1,
                                player.getLocation().getBlockZ() + dz + 0.5),
                        hostiles[rng.nextInt(hostiles.length)]);
            }
        }
    }

    private void triggerFog(List<Player> players, java.util.concurrent.ThreadLocalRandom rng) {
        int durationTicks = rng.nextInt(30, 60) * 20;
        BukkitTask[] holder = new BukkitTask[1];
        long endAt = System.currentTimeMillis() + durationTicks * 50L;

        holder[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (System.currentTimeMillis() >= endAt) {
                holder[0].cancel();
                return;
            }
            for (Player player : players) {
                if (!player.isOnline()) continue;
                player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 100, 0, true, false));
                player.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, player.getLocation().add(0, 1, 0),
                        6, 2, 1, 2, 0.01);
            }
        }, 0L, 60L);
    }
}
