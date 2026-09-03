package com.beotigi.skydisaster.creature;

import com.beotigi.skydisaster.BeotigiPlugin;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 가끔 하늘에 거대한 새, 하늘고래, 거대 박쥐 떼가 등장한다. 잡아야 하는 목표물이 아니라
 * 그냥 이 세계에 존재하는 생물 - 가끔 섬을 지나가거나 공격할 수도 있다.
 */
public class SkyCreatureManager {

    private final BeotigiPlugin plugin;
    private final double chancePer10Minutes;
    private final int maxConcurrent;
    private final List<UUID> tracked = new ArrayList<>();
    private BukkitTask task;

    public SkyCreatureManager(BeotigiPlugin plugin) {
        this.plugin = plugin;
        this.chancePer10Minutes = plugin.getConfig().getDouble("creature.chance-per-10-minutes", 0.08);
        this.maxConcurrent = plugin.getConfig().getInt("creature.max-concurrent", 3);
    }

    public void start() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 12000L, 12000L);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    private void tick() {
        cleanupTracked();
        if (tracked.size() >= maxConcurrent) return;

        var rng = ThreadLocalRandom.current();
        for (World world : plugin.getServer().getWorlds()) {
            if (world.getPlayers().isEmpty()) continue;
            if (rng.nextDouble() >= chancePer10Minutes) continue;

            List<Player> players = world.getPlayers();
            Player target = players.get(rng.nextInt(players.size()));
            if (rng.nextBoolean()) {
                spawnSkyWhale(target);
            } else {
                spawnGiantBird(target);
            }
            return;
        }
    }

    private void cleanupTracked() {
        Iterator<UUID> it = tracked.iterator();
        while (it.hasNext()) {
            Entity e = plugin.getServer().getEntity(it.next());
            if (e == null || !e.isValid()) it.remove();
        }
    }

    public void spawnSkyWhale(Player near) {
        LivingEntity ghast = spawnNear(near, EntityType.GHAST, 30, 25);
        if (ghast == null) return;
        applyScale(ghast, 2.2);
        ghast.setCustomName("하늘고래");
        tracked.add(ghast.getUniqueId());
    }

    public void spawnGiantBird(Player near) {
        LivingEntity phantom = spawnNear(near, EntityType.PHANTOM, 25, 15);
        if (phantom == null) return;
        applyScale(phantom, 1.8);
        phantom.setCustomName("거대한 새");
        tracked.add(phantom.getUniqueId());
    }

    public void spawnBatSwarm(Player near) {
        World world = near.getWorld();
        int count = ThreadLocalRandom.current().nextInt(5, 9);
        for (int i = 0; i < count; i++) {
            LivingEntity bat = spawnNear(near, EntityType.BAT, 12, 8);
            if (bat == null) continue;
            applyScale(bat, 1.6);
            tracked.add(bat.getUniqueId());
        }
    }

    private LivingEntity spawnNear(Player near, EntityType type, int horizontalOffset, int heightOffset) {
        var rng = ThreadLocalRandom.current();
        World world = near.getWorld();
        Location base = near.getLocation();
        int dx = rng.nextInt(-horizontalOffset, horizontalOffset + 1);
        int dz = rng.nextInt(-horizontalOffset, horizontalOffset + 1);
        Location spawnLoc = base.clone().add(dx, heightOffset, dz);
        Entity entity = world.spawnEntity(spawnLoc, type);
        return entity instanceof LivingEntity living ? living : null;
    }

    private void applyScale(LivingEntity entity, double scale) {
        var attribute = entity.getAttribute(Attribute.SCALE);
        if (attribute != null) {
            attribute.setBaseValue(scale);
        }
    }
}
