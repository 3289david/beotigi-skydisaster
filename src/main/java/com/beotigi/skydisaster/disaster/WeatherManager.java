package com.beotigi.skydisaster.disaster;

import com.beotigi.skydisaster.BeotigiPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 플레이어에게 알리지 않고, 비가 오면 조용히 바람/폭풍 단계를 격화시킨다.
 * 카운트다운도, 안정도 표시도 없다. 그냥 점점 심해질 뿐.
 */
public class WeatherManager {

    public enum Phase { CALM, LIGHT_RAIN, STORM, SEVERE_STORM }

    private static final Set<Material> LIGHT_BLOCKS = Set.of(
            Material.TORCH, Material.OAK_LEAVES, Material.BIRCH_LEAVES, Material.SPRUCE_LEAVES,
            Material.JUNGLE_LEAVES, Material.ACACIA_LEAVES, Material.DARK_OAK_LEAVES,
            Material.GLASS_PANE, Material.OAK_SAPLING, Material.DANDELION, Material.POPPY,
            Material.OAK_SIGN, Material.OAK_FENCE_GATE
    );

    private final BeotigiPlugin plugin;
    private final Map<UUID, Phase> phaseByWorld = new HashMap<>();

    private final double escalationChance;
    private final double deescalationChance;
    private final double windPushStrength;
    private final double treeFallChance;
    private final double lightBlockDamageChance;

    private BukkitTask task;

    public WeatherManager(BeotigiPlugin plugin) {
        this.plugin = plugin;
        var cfg = plugin.getConfig();
        this.escalationChance = cfg.getDouble("weather.escalation-chance-per-second", 0.004);
        this.deescalationChance = cfg.getDouble("weather.deescalation-chance-per-second", 0.01);
        this.windPushStrength = cfg.getDouble("weather.wind-push-strength", 0.35);
        this.treeFallChance = cfg.getDouble("weather.tree-fall-chance-per-second", 0.03);
        this.lightBlockDamageChance = cfg.getDouble("weather.light-block-damage-chance-per-second", 0.02);
    }

    public void start() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    public Phase getPhase(World world) {
        return phaseByWorld.getOrDefault(world.getUID(), Phase.CALM);
    }

    public void forcePhase(World world, Phase phase) {
        phaseByWorld.put(world.getUID(), phase);
    }

    private void tick() {
        var rng = ThreadLocalRandom.current();
        for (World world : plugin.getServer().getWorlds()) {
            if (world.getPlayers().isEmpty()) continue;

            Phase current = phaseByWorld.getOrDefault(world.getUID(), Phase.CALM);

            if (!world.hasStorm()) {
                phaseByWorld.put(world.getUID(), Phase.CALM);
                continue;
            }

            // 비가 내리는 중 - 조용히 격화되거나 약해진다.
            if (current == Phase.CALM) {
                current = Phase.LIGHT_RAIN;
            } else if (current != Phase.SEVERE_STORM && rng.nextDouble() < escalationChance) {
                current = Phase.values()[current.ordinal() + 1];
            } else if (current != Phase.LIGHT_RAIN && rng.nextDouble() < deescalationChance) {
                current = Phase.values()[current.ordinal() - 1];
            }
            phaseByWorld.put(world.getUID(), current);

            if (current == Phase.STORM || current == Phase.SEVERE_STORM) {
                applyStormEffects(world, current, rng);
            }
        }
    }

    private void applyStormEffects(World world, Phase phase, java.util.concurrent.ThreadLocalRandom rng) {
        for (Player player : world.getPlayers()) {
            if (!isExposedToSky(player)) continue;

            // 강풍 - 밀어냄
            Vector wind = new Vector(rng.nextDouble(-1, 1), 0, rng.nextDouble(-1, 1)).normalize()
                    .multiply(windPushStrength * (phase == Phase.SEVERE_STORM ? 1.6 : 1.0));
            if (rng.nextDouble() < 0.5) {
                player.setVelocity(player.getVelocity().add(wind));
            }

            // 가벼운 블록 파괴
            if (rng.nextDouble() < lightBlockDamageChance) {
                damageNearbyLightBlock(player, rng);
            }

            // 나무 쓰러짐 (심한 폭풍만)
            if (phase == Phase.SEVERE_STORM && rng.nextDouble() < treeFallChance) {
                toppleNearbyTree(player, rng);
            }
        }
    }

    private boolean isExposedToSky(Player player) {
        return player.getWorld().getHighestBlockYAt(player.getLocation()) <= player.getLocation().getBlockY() + 1;
    }

    private void damageNearbyLightBlock(Player player, java.util.concurrent.ThreadLocalRandom rng) {
        Location base = player.getLocation();
        for (int attempt = 0; attempt < 6; attempt++) {
            int dx = rng.nextInt(-6, 7);
            int dy = rng.nextInt(-2, 4);
            int dz = rng.nextInt(-6, 7);
            Block block = base.clone().add(dx, dy, dz).getBlock();
            if (LIGHT_BLOCKS.contains(block.getType())) {
                block.getWorld().playSound(block.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.6f, 1.2f);
                block.getWorld().spawnParticle(Particle.CLOUD, block.getLocation().add(0.5, 0.5, 0.5), 4, 0.2, 0.2, 0.2, 0.01);
                block.breakNaturally();
                return;
            }
        }
    }

    private void toppleNearbyTree(Player player, java.util.concurrent.ThreadLocalRandom rng) {
        Location base = player.getLocation();
        for (int attempt = 0; attempt < 8; attempt++) {
            int dx = rng.nextInt(-16, 17);
            int dz = rng.nextInt(-16, 17);
            int topY = player.getWorld().getHighestBlockYAt(base.getBlockX() + dx, base.getBlockZ() + dz);
            Block ground = player.getWorld().getBlockAt(base.getBlockX() + dx, topY, base.getBlockZ() + dz);
            if (!isLog(ground.getType())) continue;

            List<Block> trunk = new java.util.ArrayList<>();
            Block cursor = ground;
            for (int i = 0; i < 12 && isLog(cursor.getType()); i++) {
                trunk.add(cursor);
                cursor = cursor.getRelative(0, 1, 0);
            }
            if (trunk.size() < 3) continue;

            Vector fallDir = new Vector(rng.nextDouble(-1, 1), 0.15, rng.nextDouble(-1, 1)).normalize();
            player.getWorld().playSound(ground.getLocation(), Sound.BLOCK_WOOD_BREAK, 1.5f, 0.7f);
            for (Block log : trunk) {
                FallingBlock fb = log.getWorld().spawnFallingBlock(log.getLocation().add(0.5, 0.5, 0.5), log.getBlockData());
                fb.setDropItem(true);
                fb.setVelocity(fallDir.clone().multiply(0.4));
                log.setType(Material.AIR, false);
            }
            return;
        }
    }

    private boolean isLog(Material type) {
        return type.name().endsWith("_LOG") || type.name().endsWith("_WOOD");
    }
}
