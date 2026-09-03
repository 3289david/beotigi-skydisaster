package com.beotigi.skydisaster.village;

import com.beotigi.skydisaster.BeotigiPlugin;
import com.beotigi.skydisaster.item.SpecialItemManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 아무도 부르지 않은 상인이 가끔 나타난다. 희귀 아이템을 팔고, 시간이 지나면 떠난다.
 * 바닐라 떠돌이 상인을 그대로 활용하되, 특수 아이템을 거래 목록에 섞는다.
 */
public class TravelingMerchantManager {

    private final BeotigiPlugin plugin;
    private final SpecialItemManager specialItemManager;
    private final double chancePerHour;
    private final int stayTicks;
    private BukkitTask rollTask;

    public TravelingMerchantManager(BeotigiPlugin plugin, SpecialItemManager specialItemManager) {
        this.plugin = plugin;
        this.specialItemManager = specialItemManager;
        this.chancePerHour = plugin.getConfig().getDouble("merchant.chance-per-hour", 0.4);
        this.stayTicks = plugin.getConfig().getInt("merchant.stay-minutes", 5) * 1200;
    }

    public void start() {
        rollTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1200L, 1200L);
    }

    public void stop() {
        if (rollTask != null) rollTask.cancel();
    }

    private void tick() {
        var rng = ThreadLocalRandom.current();
        for (World world : plugin.getServer().getWorlds()) {
            if (world.getPlayers().isEmpty()) continue;
            if (rng.nextDouble() >= chancePerHour / 60.0) continue;

            List<Player> players = world.getPlayers();
            Player target = players.get(rng.nextInt(players.size()));
            spawnMerchant(target, rng);
            return;
        }
    }

    public void spawnMerchant(Player target, java.util.concurrent.ThreadLocalRandom rng) {
        World world = target.getWorld();
        int dx = rng.nextInt(-25, 26);
        int dz = rng.nextInt(-25, 26);
        int y = world.getHighestBlockYAt(target.getLocation().getBlockX() + dx, target.getLocation().getBlockZ() + dz);
        Location spawnLoc = new Location(world, target.getLocation().getBlockX() + dx + 0.5, y + 1,
                target.getLocation().getBlockZ() + dz + 0.5);

        WanderingTrader trader = (WanderingTrader) world.spawnEntity(spawnLoc, EntityType.WANDERING_TRADER);
        trader.setRecipes(buildTrades(rng));
        trader.setRemoveWhenFarAway(false);
        world.spawnEntity(spawnLoc.clone().add(1, 0, 0), EntityType.TRADER_LLAMA);
        world.playSound(spawnLoc, Sound.ENTITY_WANDERING_TRADER_AMBIENT, 1f, 1f);
        world.spawnParticle(Particle.PORTAL, spawnLoc, 40, 1, 1, 1, 0.1);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (trader.isValid()) {
                world.spawnParticle(Particle.CLOUD, trader.getLocation(), 20, 0.5, 0.5, 0.5, 0.05);
                trader.remove();
            }
        }, stayTicks);
    }

    private List<MerchantRecipe> buildTrades(java.util.concurrent.ThreadLocalRandom rng) {
        List<MerchantRecipe> recipes = new ArrayList<>();

        recipes.add(simpleTrade(new ItemStack(Material.EMERALD, 6), new ItemStack(Material.ENDER_PEARL, 2)));
        recipes.add(simpleTrade(new ItemStack(Material.EMERALD, 4), new ItemStack(Material.GLOW_INK_SAC, 3)));
        recipes.add(simpleTrade(new ItemStack(Material.EMERALD, 10), new ItemStack(Material.SADDLE, 1)));

        // 특수 아이템 1~2종 - 희귀템은 비싸다.
        int specialCount = rng.nextInt(1, 3);
        var types = SpecialItemManager.SpecialItem.values();
        for (int i = 0; i < specialCount; i++) {
            var type = types[rng.nextInt(types.length)];
            recipes.add(simpleTrade(new ItemStack(Material.EMERALD, rng.nextInt(16, 33)),
                    specialItemManager.createItem(type)));
        }

        return recipes;
    }

    private MerchantRecipe simpleTrade(ItemStack cost, ItemStack result) {
        MerchantRecipe recipe = new MerchantRecipe(result, 6);
        recipe.addIngredient(cost);
        return recipe;
    }
}
