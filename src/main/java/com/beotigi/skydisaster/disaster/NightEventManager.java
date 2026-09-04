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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 밤이 되면 평범한 마크보다 조금 더 위험해진다. 사전 안내는 없다.
 * "매일 밤 정각에 한 번" 같은 정해진 타이밍이 아니라, 밤 동안 계속 조금씩 확률을 굴려서
 * 언제 터질지 알 수 없게 한다. 한 이벤트당 하루 밤에는 최대 한 번만 (스팸 방지),
 * 하지만 그게 밤의 몇 분째일지는 아무도 모른다.
 */
public class NightEventManager {

    // 밤 동안 대략 이만큼 체크가 도니, 매 체크 확률을 이 값으로 나눠서
    // "하룻밤 전체 확률"이 기존 설정값과 비슷하게 유지되도록 한다.
    private static final int CHECKS_PER_NIGHT = 70;

    private final BeotigiPlugin plugin;
    private final MeteorManager meteorManager;
    private final SkyCreatureManager skyCreatureManager;

    private final double bloodMoonChance;
    private final double meteorShowerChance;
    private final double fogChance;
    private final double batSwarmChance;

    private final Map<UUID, Long> currentNightId = new HashMap<>();
    private final Map<UUID, Set<String>> triggeredThisNight = new HashMap<>();
    private BukkitTask task;

    public NightEventManager(BeotigiPlugin plugin, MeteorManager meteorManager, SkyCreatureManager skyCreatureManager) {
        this.plugin = plugin;
        this.meteorManager = meteorManager;
        this.skyCreatureManager = skyCreatureManager;
        var cfg = plugin.getConfig();
        this.bloodMoonChance = cfg.getDouble("night-event.blood-moon-chance", 0.05) / CHECKS_PER_NIGHT;
        this.meteorShowerChance = cfg.getDouble("night-event.meteor-shower-chance", 0.04) / CHECKS_PER_NIGHT;
        this.fogChance = cfg.getDouble("night-event.fog-chance", 0.08) / CHECKS_PER_NIGHT;
        this.batSwarmChance = cfg.getDouble("night-event.bat-swarm-chance", 0.1) / CHECKS_PER_NIGHT;
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
            UUID worldId = world.getUID();

            if (!isNight) {
                currentNightId.remove(worldId);
                triggeredThisNight.remove(worldId);
                continue;
            }

            long day = world.getFullTime() / 24000L;
            if (!Long.valueOf(day).equals(currentNightId.get(worldId))) {
                currentNightId.put(worldId, day);
                triggeredThisNight.put(worldId, new HashSet<>());
            }

            rollNightEvents(world, triggeredThisNight.get(worldId));
        }
    }

    private void rollNightEvents(World world, Set<String> triggered) {
        var rng = ThreadLocalRandom.current();
        List<Player> players = world.getPlayers();
        if (players.isEmpty()) return;

        if (!triggered.contains("blood_moon") && rng.nextDouble() < bloodMoonChance) {
            triggered.add("blood_moon");
            com.beotigi.skydisaster.util.ChatAnnouncer.announce(world, "오늘 밤은 뭔가 다르다...");
            triggerBloodMoon(world, players, rng);
        }
        if (!triggered.contains("meteor_shower") && rng.nextDouble() < meteorShowerChance) {
            triggered.add("meteor_shower");
            com.beotigi.skydisaster.util.ChatAnnouncer.announce(world, "하늘에서 뭔가 계속 떨어진다.");
            meteorManager.startMeteorShower(world);
        }
        if (!triggered.contains("fog") && rng.nextDouble() < fogChance) {
            triggered.add("fog");
            com.beotigi.skydisaster.util.ChatAnnouncer.announce(world, "안개가 스멀스멀 피어오른다...");
            triggerFog(players, rng);
        }
        if (!triggered.contains("bat_swarm") && rng.nextDouble() < batSwarmChance) {
            triggered.add("bat_swarm");
            com.beotigi.skydisaster.util.ChatAnnouncer.announce(world, "날개 소리가 가까워진다...");
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
