package com.beotigi.skydisaster;

import com.beotigi.skydisaster.command.BeotigiCommand;
import com.beotigi.skydisaster.command.IslandCommand;
import com.beotigi.skydisaster.disaster.FloodManager;
import com.beotigi.skydisaster.disaster.LightningFireManager;
import com.beotigi.skydisaster.disaster.MeteorManager;
import com.beotigi.skydisaster.disaster.StructuralCollapseManager;
import com.beotigi.skydisaster.disaster.VoidStormManager;
import com.beotigi.skydisaster.disaster.WeatherManager;
import com.beotigi.skydisaster.island.IslandManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class BeotigiPlugin extends JavaPlugin {

    private WeatherManager weatherManager;
    private LightningFireManager lightningFireManager;
    private FloodManager floodManager;
    private MeteorManager meteorManager;
    private VoidStormManager voidStormManager;
    private StructuralCollapseManager structuralCollapseManager;
    private IslandManager islandManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.islandManager = new IslandManager(this);
        this.weatherManager = new WeatherManager(this);
        this.lightningFireManager = new LightningFireManager(this, weatherManager);
        this.floodManager = new FloodManager(this, weatherManager);
        this.meteorManager = new MeteorManager(this);
        this.voidStormManager = new VoidStormManager(this);
        this.structuralCollapseManager = new StructuralCollapseManager(this, weatherManager);

        weatherManager.start();
        lightningFireManager.start();
        floodManager.start();
        meteorManager.start();
        voidStormManager.start();
        structuralCollapseManager.start();

        var islandCommand = getCommand("island");
        if (islandCommand != null) {
            islandCommand.setExecutor(new IslandCommand(islandManager));
        }

        var beotigiCommand = getCommand("beotigi");
        if (beotigiCommand != null) {
            BeotigiCommand executor = new BeotigiCommand(
                    this, weatherManager, meteorManager, voidStormManager);
            beotigiCommand.setExecutor(executor);
            beotigiCommand.setTabCompleter(executor);
        }

        getLogger().info("Beotigi 재해 시스템이 조용히 깨어났습니다.");
    }

    @Override
    public void onDisable() {
        if (weatherManager != null) weatherManager.stop();
        if (lightningFireManager != null) lightningFireManager.stop();
        if (floodManager != null) floodManager.stop();
        if (meteorManager != null) meteorManager.stop();
        if (voidStormManager != null) voidStormManager.stop();
        if (structuralCollapseManager != null) structuralCollapseManager.stop();
    }
}
