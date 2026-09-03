package com.beotigi.skydisaster.village;

import com.beotigi.skydisaster.BeotigiPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 섬을 어느 정도 발전시키면(집=침대+문) 주민이 자연스럽게 찾아온다.
 * 마을이 커지면 그 중 일부가 직업(=대장간/농장/도서관 등 실제 건물이 있는 상점)을 갖는다.
 * 플레이어가 도시를 운영하는 게 아니라, 그냥 마을이 자연스럽게 성장하는 느낌을 노린다.
 */
public class VillagerMigrationManager {

    private static final Map<Villager.Profession, Material> WORKSTATIONS = Map.ofEntries(
            Map.entry(Villager.Profession.FARMER, Material.COMPOSTER),
            Map.entry(Villager.Profession.LIBRARIAN, Material.LECTERN),
            Map.entry(Villager.Profession.ARMORER, Material.BLAST_FURNACE),
            Map.entry(Villager.Profession.WEAPONSMITH, Material.GRINDSTONE),
            Map.entry(Villager.Profession.TOOLSMITH, Material.SMITHING_TABLE),
            Map.entry(Villager.Profession.CLERIC, Material.BREWING_STAND),
            Map.entry(Villager.Profession.FISHERMAN, Material.BARREL),
            Map.entry(Villager.Profession.SHEPHERD, Material.LOOM),
            Map.entry(Villager.Profession.FLETCHER, Material.FLETCHING_TABLE),
            Map.entry(Villager.Profession.MASON, Material.STONECUTTER),
            Map.entry(Villager.Profession.BUTCHER, Material.SMOKER),
            Map.entry(Villager.Profession.CARTOGRAPHER, Material.CARTOGRAPHY_TABLE)
    );
    private static final List<Villager.Profession> PROFESSIONS = List.copyOf(WORKSTATIONS.keySet());

    private final BeotigiPlugin plugin;
    private final int radius = 10;
    private final double migrationChance;
    private final int maxVillagersPerSite;
    private final int shopThreshold;
    private BukkitTask task;

    public VillagerMigrationManager(BeotigiPlugin plugin) {
        this.plugin = plugin;
        var cfg = plugin.getConfig();
        this.migrationChance = cfg.getDouble("village.migration-chance", 0.2);
        this.maxVillagersPerSite = cfg.getInt("village.max-villagers-per-site", 8);
        this.shopThreshold = cfg.getInt("village.shop-threshold-villagers", 4);
        this.intervalTicks = cfg.getInt("village.scan-interval-seconds", 60) * 20L;
    }

    private final long intervalTicks;

    public void start() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, intervalTicks, intervalTicks);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    private void tick() {
        var rng = ThreadLocalRandom.current();
        for (World world : plugin.getServer().getWorlds()) {
            for (Player player : world.getPlayers()) {
                evaluateSite(player, rng);
            }
        }
    }

    private void evaluateSite(Player player, java.util.concurrent.ThreadLocalRandom rng) {
        Location base = player.getLocation();
        World world = player.getWorld();

        int beds = 0;
        int doors = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -6; dy <= 8; dy++) {
                    Material type = world.getBlockAt(base.getBlockX() + dx, base.getBlockY() + dy, base.getBlockZ() + dz).getType();
                    String name = type.name();
                    if (name.endsWith("_BED")) beds++;
                    else if (name.endsWith("_DOOR")) doors++;
                }
            }
        }
        if (beds < 1 || doors < 1) return;

        long villagerCount = world.getNearbyEntities(base, radius, radius, radius).stream()
                .filter(e -> e.getType() == EntityType.VILLAGER).count();
        if (villagerCount >= maxVillagersPerSite) return;
        if (rng.nextDouble() >= migrationChance) return;

        Villager villager = spawnMigrant(player, rng);
        if (villager != null && villagerCount + 1 >= shopThreshold) {
            assignProfession(villager, rng);
        }
    }

    private Villager spawnMigrant(Player player, java.util.concurrent.ThreadLocalRandom rng) {
        Location base = player.getLocation();
        World world = player.getWorld();

        for (int attempt = 0; attempt < 8; attempt++) {
            int dx = rng.nextInt(-radius, radius + 1);
            int dz = rng.nextInt(-radius, radius + 1);
            int y = world.getHighestBlockYAt(base.getBlockX() + dx, base.getBlockZ() + dz);
            Block ground = world.getBlockAt(base.getBlockX() + dx, y, base.getBlockZ() + dz);
            if (ground.getType() == Material.AIR) continue;
            Block spawnBlock = ground.getRelative(0, 1, 0);
            if (spawnBlock.getType() != Material.AIR) continue;

            Location spawnLoc = spawnBlock.getLocation().add(0.5, 0, 0.5);
            Villager villager = (Villager) world.spawnEntity(spawnLoc, EntityType.VILLAGER);
            world.playSound(spawnLoc, Sound.ENTITY_VILLAGER_YES, 1f, 1f);
            world.spawnParticle(Particle.HAPPY_VILLAGER, spawnLoc, 10, 0.5, 0.5, 0.5, 0.01);
            return villager;
        }
        return null;
    }

    private void assignProfession(Villager villager, java.util.concurrent.ThreadLocalRandom rng) {
        if (villager.getProfession() != Villager.Profession.NONE) return;

        Villager.Profession profession = PROFESSIONS.get(rng.nextInt(PROFESSIONS.size()));
        Material workstation = WORKSTATIONS.get(profession);

        Block base = villager.getLocation().getBlock();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                Block target = base.getRelative(dx, 0, dz);
                if (target.getType() != Material.AIR) continue;
                Block below = target.getRelative(0, -1, 0);
                if (!below.getType().isSolid()) continue;

                target.setType(workstation, false);
                villager.setProfession(profession);
                return;
            }
        }
    }
}
