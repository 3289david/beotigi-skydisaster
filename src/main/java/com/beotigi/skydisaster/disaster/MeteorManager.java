package com.beotigi.skydisaster.disaster;

import com.beotigi.skydisaster.BeotigiPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 밤에 아주 가끔, 하늘에서 불빛이 보이다가 쾅 떨어진다.
 * 크레이터 한가운데엔 희귀 광물이 남는다 - 재해이자 탐험 요소.
 */
public class MeteorManager implements Listener {

    private static final String META_KEY = "beotigi_meteor";

    private final BeotigiPlugin plugin;
    private final double chancePerMinute;
    private final int craterRadius;
    private BukkitTask task;

    public MeteorManager(BeotigiPlugin plugin) {
        this.plugin = plugin;
        this.chancePerMinute = plugin.getConfig().getDouble("meteor.chance-per-minute", 0.01);
        this.craterRadius = plugin.getConfig().getInt("meteor.crater-radius", 4);
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1200L, 1200L);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    public void forceStrike(Player nearPlayer) {
        launchMeteor(nearPlayer);
    }

    private void tick() {
        var rng = ThreadLocalRandom.current();
        for (World world : plugin.getServer().getWorlds()) {
            long time = world.getTime();
            boolean isNight = time >= 13000 && time <= 23000;
            if (!isNight || world.getPlayers().isEmpty()) continue;
            if (rng.nextDouble() >= chancePerMinute) continue;

            List<Player> players = world.getPlayers();
            Player target = players.get(rng.nextInt(players.size()));
            launchMeteor(target);
            return;
        }
    }

    private void launchMeteor(Player target) {
        var rng = ThreadLocalRandom.current();
        World world = target.getWorld();
        int dx = rng.nextInt(-40, 41);
        int dz = rng.nextInt(-40, 41);
        int gx = target.getLocation().getBlockX() + dx;
        int gz = target.getLocation().getBlockZ() + dz;
        int groundY = world.getHighestBlockYAt(gx, gz);

        Location spawnLoc = new Location(world, gx + 0.5, groundY + 45, gz + 0.5);
        FallingBlock meteor = world.spawn(spawnLoc, FallingBlock.class, fb -> {
            fb.setBlockData(Material.MAGMA_BLOCK.createBlockData());
            fb.setDropItem(false);
            fb.setHurtEntities(true);
            fb.setVelocity(new org.bukkit.util.Vector(0, -1.4, 0));
            fb.setMetadata(META_KEY, new FixedMetadataValue(plugin, true));
        });

        world.playSound(spawnLoc, Sound.ENTITY_GHAST_SHOOT, 2f, 0.4f);
        trailParticles(meteor);
    }

    private void trailParticles(FallingBlock meteor) {
        BukkitTask[] holder = new BukkitTask[1];
        holder[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!meteor.isValid()) {
                holder[0].cancel();
                return;
            }
            meteor.getWorld().spawnParticle(Particle.FLAME, meteor.getLocation(), 6, 0.15, 0.15, 0.15, 0.01);
            meteor.getWorld().spawnParticle(Particle.LAVA, meteor.getLocation(), 1, 0, 0, 0, 0);
        }, 0L, 2L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onLand(EntityChangeBlockEvent event) {
        if (event.getEntityType() != EntityType.FALLING_BLOCK) return;
        if (!event.getEntity().hasMetadata(META_KEY)) return;

        event.setCancelled(true);
        Location impact = event.getBlock().getLocation();
        event.getEntity().remove();
        createCrater(impact);
    }

    private void createCrater(Location center) {
        World world = center.getWorld();
        var rng = ThreadLocalRandom.current();

        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 3f, 0.8f);
        world.spawnParticle(Particle.EXPLOSION, center, 3);

        int r = craterRadius;
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= 1; y++) {
                for (int z = -r; z <= r; z++) {
                    if (x * x + y * y + z * z > r * r) continue;
                    Block block = center.clone().add(x, y, z).getBlock();
                    if (block.getType() == Material.AIR || block.getType() == Material.BEDROCK) continue;

                    boolean isCrust = (x * x + y * y + z * z) > (r - 1) * (r - 1);
                    if (isCrust && rng.nextDouble() < 0.6) {
                        block.setType(rng.nextBoolean() ? Material.BLACKSTONE : Material.BASALT, false);
                    } else {
                        block.setType(Material.AIR, false);
                    }
                }
            }
        }

        // 크레이터 바닥에 불씨
        for (int i = 0; i < 3; i++) {
            int x = rng.nextInt(-r, r + 1);
            int z = rng.nextInt(-r, r + 1);
            Block b = center.clone().add(x, 0, z).getBlock();
            if (b.getType() == Material.AIR) {
                Block below = b.getRelative(0, -1, 0);
                if (below.getType().isSolid()) b.setType(Material.FIRE, false);
            }
        }

        // 중심에 희귀 광물 보상 상자
        Block chestSpot = center.clone().add(0, -1, 0).getBlock();
        chestSpot.setType(Material.CHEST, false);
        if (chestSpot.getState() instanceof org.bukkit.block.Chest chestState) {
            Inventory inv = chestState.getBlockInventory();
            fillMeteorLoot(inv, rng);
        }
    }

    private void fillMeteorLoot(Inventory inv, java.util.concurrent.ThreadLocalRandom rng) {
        inv.addItem(new ItemStack(Material.RAW_IRON, rng.nextInt(2, 6)));
        inv.addItem(new ItemStack(Material.RAW_GOLD, rng.nextInt(1, 4)));
        if (rng.nextDouble() < 0.5) inv.addItem(new ItemStack(Material.DIAMOND, rng.nextInt(1, 3)));
        if (rng.nextDouble() < 0.15) inv.addItem(new ItemStack(Material.ANCIENT_DEBRIS, 1));
        if (rng.nextDouble() < 0.3) inv.addItem(new ItemStack(Material.EMERALD, rng.nextInt(1, 3)));
    }
}
