package com.beotigi.skydisaster.command;

import com.beotigi.skydisaster.BeotigiPlugin;
import com.beotigi.skydisaster.disaster.MeteorManager;
import com.beotigi.skydisaster.disaster.VoidStormManager;
import com.beotigi.skydisaster.disaster.WeatherManager;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public class BeotigiCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "status", "forcestorm", "forcemeteor", "forcevoidstorm", "stop", "reload");

    private final BeotigiPlugin plugin;
    private final WeatherManager weatherManager;
    private final MeteorManager meteorManager;
    private final VoidStormManager voidStormManager;

    public BeotigiCommand(BeotigiPlugin plugin, WeatherManager weatherManager,
                           MeteorManager meteorManager, VoidStormManager voidStormManager) {
        this.plugin = plugin;
        this.weatherManager = weatherManager;
        this.meteorManager = meteorManager;
        this.voidStormManager = voidStormManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§7사용법: /beotigi <status|forcestorm|forcemeteor|forcevoidstorm|stop|reload>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "status" -> handleStatus(sender);
            case "forcestorm" -> handleForceStorm(sender);
            case "forcemeteor" -> handleForceMeteor(sender);
            case "forcevoidstorm" -> {
                voidStormManager.forceStart();
                sender.sendMessage("§7공허 폭풍을 강제로 시작했습니다.");
            }
            case "stop" -> handleStop(sender);
            case "reload" -> {
                plugin.reloadConfig();
                sender.sendMessage("§7설정 파일을 다시 불러왔습니다. (수치 반영을 위해서는 재시작 권장)");
            }
            default -> sender.sendMessage("§7사용법: /beotigi <status|forcestorm|forcemeteor|forcevoidstorm|stop|reload>");
        }
        return true;
    }

    private void handleStatus(CommandSender sender) {
        sender.sendMessage("§7=== Beotigi 상태 ===");
        for (World world : plugin.getServer().getWorlds()) {
            if (world.getPlayers().isEmpty()) continue;
            sender.sendMessage("§7" + world.getName() + ": phase=" + weatherManager.getPhase(world)
                    + " storming=" + world.hasStorm());
        }
        sender.sendMessage("§7공허 폭풍 진행 중: " + voidStormManager.isActive());
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

    private void handleForceMeteor(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어만 사용할 수 있습니다.");
            return;
        }
        meteorManager.forceStrike(player);
        sender.sendMessage("§7운석을 호출했습니다.");
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
        return List.of();
    }
}
