package com.beotigi.skydisaster.disaster;

import com.beotigi.skydisaster.BeotigiPlugin;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 번개폭풍: 폭풍 단계일 때 자연 번개 외에 추가로 번개를 내리쳐 화재 확률을 높인다.
 * 번개가 나무에 맞으면 바닐라 화재 전파가 알아서 집까지 번진다 - 우리가 할 일은
 * "이번엔 좀 심하게" 쳐주는 것뿐이다.
 */
public class LightningFireManager {

    private final BeotigiPlugin plugin;
    private final WeatherManager weatherManager;
    private final double extraStrikeChance;
    private BukkitTask task;

    public LightningFireManager(BeotigiPlugin plugin, WeatherManager weatherManager) {
        this.plugin = plugin;
        this.weatherManager = weatherManager;
        this.extraStrikeChance = plugin.getConfig().getDouble("lightning.extra-strike-chance-per-second", 0.05);
    }

    public void start() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 40L, 20L);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    private void tick() {
        var rng = ThreadLocalRandom.current();
        for (World world : plugin.getServer().getWorlds()) {
            WeatherManager.Phase phase = weatherManager.getPhase(world);
            if (phase != WeatherManager.Phase.STORM && phase != WeatherManager.Phase.SEVERE_STORM) continue;

            for (Player player : world.getPlayers()) {
                if (rng.nextDouble() >= extraStrikeChance) continue;

                int dx = rng.nextInt(-24, 25);
                int dz = rng.nextInt(-24, 25);
                Location loc = player.getLocation().clone().add(dx, 0, dz);
                loc.setY(world.getHighestBlockYAt(loc) + 1);
                world.strikeLightning(loc);
            }
        }
    }
}
