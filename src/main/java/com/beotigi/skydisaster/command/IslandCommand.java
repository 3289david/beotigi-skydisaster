package com.beotigi.skydisaster.command;

import com.beotigi.skydisaster.island.IslandManager;
import com.beotigi.skydisaster.island.IslandType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class IslandCommand implements CommandExecutor {

    private final IslandManager islandManager;

    public IslandCommand(IslandManager islandManager) {
        this.islandManager = islandManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어만 사용할 수 있습니다.");
            return true;
        }
        if (args.length != 1) {
            player.sendMessage("§7사용법: /island <forest|rock|plains>");
            return true;
        }

        IslandType type;
        try {
            type = IslandType.valueOf(args[0].toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage("§7사용법: /island <forest|rock|plains>");
            return true;
        }

        islandManager.createIsland(player, type);
        return true;
    }
}
