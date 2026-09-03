package com.beotigi.skydisaster.command;

import com.beotigi.skydisaster.BeotigiPlugin;
import com.beotigi.skydisaster.creature.SkyCreatureManager;
import com.beotigi.skydisaster.disaster.MeteorManager;
import com.beotigi.skydisaster.disaster.NightEventManager;
import com.beotigi.skydisaster.disaster.VoidStormManager;
import com.beotigi.skydisaster.disaster.VolcanoManager;
import com.beotigi.skydisaster.disaster.WeatherManager;
import com.beotigi.skydisaster.island.DiscoverableIslandType;
import com.beotigi.skydisaster.island.IslandDiscoveryManager;
import com.beotigi.skydisaster.item.SpecialItemManager;
import com.beotigi.skydisaster.village.TravelingMerchantManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class BeotigiCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "status", "forcestorm", "forcemeteor", "forcevoidstorm", "forcevolcano",
            "forcediscovery", "forcemerchant", "forcecreature", "forcenightevent",
            "give", "stop", "reload");

    private final BeotigiPlugin plugin;
    private final WeatherManager weatherManager;
    private final MeteorManager meteorManager;
    private final VoidStormManager voidStormManager;
    private final VolcanoManager volcanoManager;
    private final IslandDiscoveryManager islandDiscoveryManager;
    private final SpecialItemManager specialItemManager;
    private final TravelingMerchantManager travelingMerchantManager;
    private final SkyCreatureManager skyCreatureManager;
    private final NightEventManager nightEventManager;

    public BeotigiCommand(BeotigiPlugin plugin, WeatherManager weatherManager, MeteorManager meteorManager,
                           VoidStormManager voidStormManager, VolcanoManager volcanoManager,
                           IslandDiscoveryManager islandDiscoveryManager, SpecialItemManager specialItemManager,
                           TravelingMerchantManager travelingMerchantManager, SkyCreatureManager skyCreatureManager,
                           NightEventManager nightEventManager) {
        this.plugin = plugin;
        this.weatherManager = weatherManager;
        this.meteorManager = meteorManager;
        this.voidStormManager = voidStormManager;
        this.volcanoManager = volcanoManager;
        this.islandDiscoveryManager = islandDiscoveryManager;
        this.specialItemManager = specialItemManager;
        this.travelingMerchantManager = travelingMerchantManager;
        this.skyCreatureManager = skyCreatureManager;
        this.nightEventManager = nightEventManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§7사용법: /beotigi <" + String.join("|", SUBCOMMANDS) + ">");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "status" -> handleStatus(sender);
            case "forcestorm" -> handleForceStorm(sender);
            case "forcemeteor" -> withPlayer(sender, meteorManager::forceStrike, "운석을 호출했습니다.");
            case "forcevoidstorm" -> {
                voidStormManager.forceStart();
                sender.sendMessage("§7공허 폭풍을 강제로 시작했습니다.");
            }
            case "forcevolcano" -> handleForceVolcano(sender);
            case "forcediscovery" -> handleForceDiscovery(sender, args);
            case "forcemerchant" -> withPlayer(sender,
                    p -> travelingMerchantManager.spawnMerchant(p, ThreadLocalRandom.current()),
                    "떠돌이 상인을 불렀습니다.");
            case "forcecreature" -> handleForceCreature(sender, args);
            case "forcenightevent" -> handleForceNightEvent(sender, args);
            case "give" -> handleGive(sender, args);
            case "stop" -> handleStop(sender);
            case "reload" -> {
                plugin.reloadConfig();
                sender.sendMessage("§7설정 파일을 다시 불러왔습니다. (수치 반영을 위해서는 재시작 권장)");
            }
            default -> sender.sendMessage("§7사용법: /beotigi <" + String.join("|", SUBCOMMANDS) + ">");
        }
        return true;
    }

    private void withPlayer(CommandSender sender, java.util.function.Consumer<Player> action, String successMessage) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어만 사용할 수 있습니다.");
            return;
        }
        action.accept(player);
        sender.sendMessage("§7" + successMessage);
    }

    private void handleStatus(CommandSender sender) {
        sender.sendMessage("§7=== Beotigi 상태 ===");
        for (World world : plugin.getServer().getWorlds()) {
            if (world.getPlayers().isEmpty()) continue;
            sender.sendMessage("§7" + world.getName() + ": phase=" + weatherManager.getPhase(world)
                    + " storming=" + world.hasStorm());
        }
        sender.sendMessage("§7공허 폭풍 진행 중: " + voidStormManager.isActive());
        sender.sendMessage("§7발견된 섬 수: " + islandDiscoveryManager.getDiscoveredAnchors().size());
    }

    private void handleForceStorm(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어만 사용할 수 있습니다.");
            return;
        }
        World world = player.getWorld();
        world.setStorm(true);
        world.setThundering(true);
        weatherManager.forcePhase(world, WeatherManager.Phase.SEVERE_STORM);
        sender.sendMessage("§7" + world.getName() + " 폭풍을 강제로 격화시켰습니다.");
    }

    private void handleForceVolcano(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어만 사용할 수 있습니다.");
            return;
        }
        Location loc = player.getLocation().clone().add(0, 0, 10);
        volcanoManager.registerVolcano(loc);
        sender.sendMessage("§7새 화산을 등록했습니다 (때가 되면 스스로 깨어납니다).");
    }

    private void handleForceDiscovery(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어만 사용할 수 있습니다.");
            return;
        }
        DiscoverableIslandType type;
        if (args.length >= 2) {
            try {
                type = DiscoverableIslandType.valueOf(args[1].toUpperCase());
            } catch (IllegalArgumentException e) {
                sender.sendMessage("§7타입: " + Arrays.toString(DiscoverableIslandType.values()));
                return;
            }
        } else {
            var values = DiscoverableIslandType.values();
            type = values[ThreadLocalRandom.current().nextInt(values.length)];
        }

        Location anchor = player.getLocation().clone().add(60, 0, 60);
        islandDiscoveryManager.spawnIsland(player.getWorld(), anchor, type);
        sender.sendMessage("§7" + type + " 섬을 생성했습니다.");
    }

    private void handleForceCreature(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어만 사용할 수 있습니다.");
            return;
        }
        String sub = args.length >= 2 ? args[1].toLowerCase() : "whale";
        switch (sub) {
            case "bird" -> skyCreatureManager.spawnGiantBird(player);
            case "bats" -> skyCreatureManager.spawnBatSwarm(player);
            default -> skyCreatureManager.spawnSkyWhale(player);
        }
        sender.sendMessage("§7하늘 생물을 불러왔습니다: " + sub);
    }

    private void handleForceNightEvent(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어만 사용할 수 있습니다.");
            return;
        }
        String sub = args.length >= 2 ? args[1].toLowerCase() : "shower";
        if (sub.equals("shower")) {
            meteorManager.startMeteorShower(player.getWorld());
        } else if (sub.equals("bats")) {
            skyCreatureManager.spawnBatSwarm(player);
        }
        sender.sendMessage("§7야간 이벤트를 강제로 실행했습니다: " + sub);
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어만 사용할 수 있습니다.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("§7사용법: /beotigi give <glider|island_magnet|life_seed|construction_core|wind_feather>");
            return;
        }
        try {
            var type = SpecialItemManager.SpecialItem.valueOf(args[1].toUpperCase());
            player.getInventory().addItem(specialItemManager.createItem(type));
            sender.sendMessage("§7지급했습니다: " + type);
        } catch (IllegalArgumentException e) {
            sender.sendMessage("§7사용법: /beotigi give <glider|island_magnet|life_seed|construction_core|wind_feather>");
        }
    }

    private void handleStop(CommandSender sender) {
        if (sender instanceof Player player) {
            World world = player.getWorld();
            world.setStorm(false);
            world.setThundering(false);
            weatherManager.forcePhase(world, WeatherManager.Phase.CALM);
        }
        sender.sendMessage("§7날씨 상태를 초기화했습니다.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return Arrays.stream(SpecialItemManager.SpecialItem.values())
                    .map(Enum::name).map(String::toLowerCase)
                    .filter(s -> s.startsWith(args[1].toLowerCase())).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("forcediscovery")) {
            return Arrays.stream(DiscoverableIslandType.values())
                    .map(Enum::name).map(String::toLowerCase)
                    .filter(s -> s.startsWith(args[1].toLowerCase())).toList();
        }
        return List.of();
    }
}
