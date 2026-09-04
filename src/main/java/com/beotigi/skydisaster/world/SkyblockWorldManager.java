package com.beotigi.skydisaster.world;

import com.beotigi.skydisaster.BeotigiPlugin;
import com.beotigi.skydisaster.island.IslandManager;
import com.beotigi.skydisaster.island.IslandType;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.UUID;

/**
 * 접속만 하면 바로 플레이할 수 있게 - 서버가 켜지면 스카이블럭 월드와 시작 섬 3개를
 * 미리 다 지어놓고, 새 플레이어가 처음 들어오면 그 중 하나에 바로 떨어뜨린다.
 * 아무 명령어도 칠 필요 없다.
 */
public class SkyblockWorldManager implements Listener {

    private static final String WORLD_NAME = "beotigi_skyblock";

    private final BeotigiPlugin plugin;
    private final IslandManager islandManager;
    private final PlayerAssignmentStore assignmentStore;
    private final NamespacedKey builtKey;
    private World world;

    public SkyblockWorldManager(BeotigiPlugin plugin, IslandManager islandManager) {
        this.plugin = plugin;
        this.islandManager = islandManager;
        this.assignmentStore = new PlayerAssignmentStore(plugin);
        this.builtKey = new NamespacedKey(plugin, "beotigi_islands_built");
    }

    public void start() {
        world = plugin.getServer().getWorld(WORLD_NAME);
        if (world == null) {
            plugin.getLogger().info("스카이블럭 월드가 없어서 새로 만든다: " + WORLD_NAME);
            world = new WorldCreator(WORLD_NAME)
                    .generator(new VoidChunkGenerator())
                    .environment(World.Environment.NORMAL)
                    .generateStructures(false)
                    .createWorld();
        }
        if (world != null) {
            ensureIslandsBuilt();
        }
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void stop() {
        assignmentStore.save();
    }

    public World getWorld() {
        return world;
    }

    private void ensureIslandsBuilt() {
        Boolean alreadyBuilt = world.getPersistentDataContainer().get(builtKey, PersistentDataType.BOOLEAN);
        if (Boolean.TRUE.equals(alreadyBuilt)) return;

        plugin.getLogger().info("시작 섬 3개를 미리 짓는다...");
        for (IslandType type : IslandType.values()) {
            islandManager.buildIsland(world, type);
        }
        world.getPersistentDataContainer().set(builtKey, PersistentDataType.BOOLEAN, true);
        world.setSpawnLocation(IslandType.FOREST.anchor(world));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (world == null) return;
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();

        if (assignmentStore.getAssignment(id) != null) return; // 이미 배정된 플레이어 - 하던 대로 둔다.

        IslandType type = pickLeastPopulatedType();
        assignmentStore.setAssignment(id, type);
        assignmentStore.save();

        Location spawn = type.anchor(world).clone().add(0, 2, 0);
        player.teleport(spawn);
        player.sendMessage("§7... §f눈을 뜨니 낯선 땅 위였다.");
    }

    private IslandType pickLeastPopulatedType() {
        Map<IslandType, Integer> counts = assignmentStore.countsByType();
        IslandType best = IslandType.values()[0];
        int bestCount = Integer.MAX_VALUE;
        for (IslandType type : IslandType.values()) {
            int count = counts.getOrDefault(type, 0);
            if (count < bestCount) {
                bestCount = count;
                best = type;
            }
        }
        return best;
    }
}
