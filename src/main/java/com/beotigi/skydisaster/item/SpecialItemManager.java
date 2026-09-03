package com.beotigi.skydisaster.item;

import com.beotigi.skydisaster.BeotigiPlugin;
import com.beotigi.skydisaster.island.IslandDiscoveryManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 상자/던전/새 섬에서 발견되는 특수 아이템 5종.
 * 던전 텍스트나 튜토리얼 팝업 없이, 아이템 이름/설명(로어)과 사용했을 때의 결과로만 말한다.
 */
public class SpecialItemManager implements Listener {

    public enum SpecialItem { GLIDER, ISLAND_MAGNET, LIFE_SEED, CONSTRUCTION_CORE, WIND_FEATHER }

    private final BeotigiPlugin plugin;
    private final NamespacedKey key;
    private IslandDiscoveryManager islandDiscoveryManager;

    public SpecialItemManager(BeotigiPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "beotigi_item");
    }

    public void link(IslandDiscoveryManager islandDiscoveryManager) {
        this.islandDiscoveryManager = islandDiscoveryManager;
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public ItemStack createItem(SpecialItem type) {
        ItemStack item = switch (type) {
            case GLIDER -> new ItemStack(Material.ELYTRA);
            case ISLAND_MAGNET -> new ItemStack(Material.ECHO_SHARD);
            case LIFE_SEED -> new ItemStack(Material.WHEAT_SEEDS);
            case CONSTRUCTION_CORE -> new ItemStack(Material.HEART_OF_THE_SEA);
            case WIND_FEATHER -> new ItemStack(Material.FEATHER);
        };
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName(type));
            meta.setLore(List.of(lore(type)));
            if (type != SpecialItem.GLIDER) {
                meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, type.name());
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack randomItem() {
        SpecialItem[] values = SpecialItem.values();
        return createItem(values[ThreadLocalRandom.current().nextInt(values.length)]);
    }

    private String displayName(SpecialItem type) {
        return switch (type) {
            case GLIDER -> "§b글라이더";
            case ISLAND_MAGNET -> "§d섬 연결석";
            case LIFE_SEED -> "§a생명의 씨앗";
            case CONSTRUCTION_CORE -> "§6건설 코어";
            case WIND_FEATHER -> "§f바람의 깃털";
        };
    }

    private String lore(SpecialItem type) {
        return switch (type) {
            case GLIDER -> "§7공허 위에서 활공한다.";
            case ISLAND_MAGNET -> "§7가장 가까운 미지의 섬으로 다리를 놓는다.";
            case LIFE_SEED -> "§7죽은 땅을 되살린다.";
            case CONSTRUCTION_CORE -> "§7딛고 선 땅을 넓힌다.";
            case WIND_FEATHER -> "§7강풍 속에서 몸을 지켜준다.";
        };
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(key, PersistentDataType.STRING)) return;

        String raw = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        SpecialItem type;
        try {
            type = SpecialItem.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        boolean consumed = switch (type) {
            case ISLAND_MAGNET -> useIslandMagnet(player);
            case LIFE_SEED -> useLifeSeed(player, event.getClickedBlock());
            case CONSTRUCTION_CORE -> useConstructionCore(player, event.getClickedBlock());
            case WIND_FEATHER -> useWindFeather(player);
            case GLIDER -> false;
        };

        if (consumed) {
            item.setAmount(item.getAmount() - 1);
        }
    }

    private boolean useIslandMagnet(Player player) {
        if (islandDiscoveryManager == null) return false;
        List<Location> anchors = islandDiscoveryManager.getDiscoveredAnchors();
        Location from = player.getLocation();
        Location nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (Location anchor : anchors) {
            if (!anchor.getWorld().equals(from.getWorld())) continue;
            double d = anchor.distanceSquared(from);
            if (d < nearestDist) {
                nearestDist = d;
                nearest = anchor;
            }
        }
        if (nearest == null) {
            player.sendMessage("§7... 아직 이어질 곳이 보이지 않는다.");
            return false;
        }

        buildBridge(from, nearest);
        player.getWorld().playSound(from, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.2f);
        return true;
    }

    private void buildBridge(Location from, Location to) {
        World world = from.getWorld();
        Vector direction = to.toVector().subtract(from.toVector());
        double length = Math.min(direction.length(), 80);
        if (length < 1) return;
        Vector step = direction.normalize();

        Location cursor = from.clone();
        for (int i = 0; i < length; i++) {
            cursor.add(step);
            Block block = world.getBlockAt(cursor.getBlockX(), from.getBlockY() - 1, cursor.getBlockZ());
            if (block.getType() == Material.AIR) {
                block.setType(Material.PACKED_ICE, false);
            }
        }
    }

    private boolean useLifeSeed(Player player, Block clicked) {
        Block center = clicked != null ? clicked : player.getLocation().getBlock().getRelative(0, -1, 0);
        World world = center.getWorld();
        var rng = ThreadLocalRandom.current();
        int radius = 3;
        boolean revivedAny = false;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) continue;
                int topY = world.getHighestBlockYAt(center.getX() + dx, center.getZ() + dz);
                Block top = world.getBlockAt(center.getX() + dx, topY, center.getZ() + dz);
                if (isRevivable(top.getType())) {
                    top.setType(Material.GRASS_BLOCK, false);
                    revivedAny = true;
                    if (rng.nextDouble() < 0.35) {
                        Block above = top.getRelative(0, 1, 0);
                        if (above.getType() == Material.AIR) {
                            above.setType(rng.nextBoolean() ? Material.DANDELION : Material.POPPY, false);
                        }
                    }
                }
            }
        }

        if (revivedAny) {
            world.spawnParticle(Particle.HAPPY_VILLAGER, center.getLocation().add(0.5, 1, 0.5), 30, radius, 1, radius, 0.01);
            world.playSound(center.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
            player.sendMessage("§7... 땅에 색이 돌아왔다.");
        } else {
            player.sendMessage("§7이곳은 이미 살아있다.");
        }
        return revivedAny;
    }

    private boolean isRevivable(Material type) {
        return type == Material.STONE || type == Material.BLACKSTONE || type == Material.NETHERRACK
                || type == Material.COARSE_DIRT || type == Material.DIRT || type == Material.SAND
                || type == Material.GRAVEL || type == Material.BASALT;
    }

    private boolean useConstructionCore(Player player, Block clicked) {
        Block base = clicked != null ? clicked : player.getLocation().getBlock().getRelative(0, -1, 0);
        World world = base.getWorld();
        Material fillMaterial = base.getType() == Material.AIR ? Material.STONE : base.getType();
        int radius = 3;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) continue;
                Block target = base.getRelative(dx, 0, dz);
                if (target.getType() == Material.AIR) {
                    target.setType(fillMaterial, false);
                }
                Block below = target.getRelative(0, -1, 0);
                if (below.getType() == Material.AIR) {
                    below.setType(fillMaterial, false);
                }
            }
        }

        world.spawnParticle(Particle.CLOUD, base.getLocation().add(0.5, 1, 0.5), 20, radius, 0.5, radius, 0.02);
        world.playSound(base.getLocation(), Sound.BLOCK_STONE_PLACE, 1f, 0.8f);
        player.sendMessage("§7... 땅이 넓어졌다.");
        return true;
    }

    private boolean useWindFeather(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 30 * 20, 0));
        player.setVelocity(player.getVelocity().add(new Vector(0, 0.6, 0)));
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 25, 0.5, 1, 0.5, 0.05);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_ELYTRA_FLYING, 0.8f, 1.4f);
        return true;
    }
}
