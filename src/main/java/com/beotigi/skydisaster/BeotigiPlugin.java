package com.beotigi.skydisaster;

import com.beotigi.skydisaster.command.BeotigiCommand;
import com.beotigi.skydisaster.command.IslandCommand;
import com.beotigi.skydisaster.creature.SkyCreatureManager;
import com.beotigi.skydisaster.disaster.FloodManager;
import com.beotigi.skydisaster.disaster.LightningFireManager;
import com.beotigi.skydisaster.disaster.MeteorManager;
import com.beotigi.skydisaster.disaster.NightEventManager;
import com.beotigi.skydisaster.disaster.StructuralCollapseManager;
import com.beotigi.skydisaster.disaster.VoidStormManager;
import com.beotigi.skydisaster.disaster.VolcanoManager;
import com.beotigi.skydisaster.disaster.WeatherManager;
import com.beotigi.skydisaster.ecosystem.EcosystemManager;
import com.beotigi.skydisaster.ecosystem.IslandGrowthManager;
import com.beotigi.skydisaster.island.IslandDiscoveryManager;
import com.beotigi.skydisaster.island.IslandManager;
import com.beotigi.skydisaster.item.SpecialItemManager;
import com.beotigi.skydisaster.village.TravelingMerchantManager;
import com.beotigi.skydisaster.village.VillagerMigrationManager;
import com.beotigi.skydisaster.world.SkyblockWorldManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class BeotigiPlugin extends JavaPlugin {

    private IslandManager islandManager;
    private SkyblockWorldManager skyblockWorldManager;
    private VolcanoManager volcanoManager;
    private IslandDiscoveryManager islandDiscoveryManager;
    private SpecialItemManager specialItemManager;
    private WeatherManager weatherManager;
    private LightningFireManager lightningFireManager;
    private FloodManager floodManager;
    private MeteorManager meteorManager;
    private VoidStormManager voidStormManager;
    private StructuralCollapseManager structuralCollapseManager;
    private EcosystemManager ecosystemManager;
    private IslandGrowthManager islandGrowthManager;
    private VillagerMigrationManager villagerMigrationManager;
    private TravelingMerchantManager travelingMerchantManager;
    private SkyCreatureManager skyCreatureManager;
    private NightEventManager nightEventManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // --- 생성 순서: 뒤 단계가 앞 단계를 참조하므로 순서가 중요하다 ---
        this.islandManager = new IslandManager(this);
        this.skyblockWorldManager = new SkyblockWorldManager(this, islandManager);
        // 다른 시스템이 돌기 전에 월드/시작 섬부터 준비해둔다 - 접속하자마자 바로 플레이 가능하게.
        skyblockWorldManager.start();

        this.volcanoManager = new VolcanoManager(this);
        this.islandDiscoveryManager = new IslandDiscoveryManager(this, volcanoManager);
        this.specialItemManager = new SpecialItemManager(this);
        specialItemManager.link(islandDiscoveryManager);
        islandDiscoveryManager.linkSpecialItems(specialItemManager);

        this.weatherManager = new WeatherManager(this);
        this.lightningFireManager = new LightningFireManager(this, weatherManager);
        this.floodManager = new FloodManager(this, weatherManager);
        this.meteorManager = new MeteorManager(this);
        this.voidStormManager = new VoidStormManager(this);
        this.structuralCollapseManager = new StructuralCollapseManager(this, weatherManager);

        this.ecosystemManager = new EcosystemManager(this);
        this.islandGrowthManager = new IslandGrowthManager(this);
        this.villagerMigrationManager = new VillagerMigrationManager(this);
        this.travelingMerchantManager = new TravelingMerchantManager(this, specialItemManager);
        this.skyCreatureManager = new SkyCreatureManager(this);
        this.nightEventManager = new NightEventManager(this, meteorManager, skyCreatureManager);

        // --- 시작 ---
        specialItemManager.start();
        weatherManager.start();
        lightningFireManager.start();
        floodManager.start();
        meteorManager.start();
        voidStormManager.start();
        structuralCollapseManager.start();
        islandDiscoveryManager.start();
        volcanoManager.start();
        ecosystemManager.start();
        islandGrowthManager.start();
        villagerMigrationManager.start();
        travelingMerchantManager.start();
        skyCreatureManager.start();
        nightEventManager.start();

        var islandCommand = getCommand("island");
        if (islandCommand != null) {
            islandCommand.setExecutor(new IslandCommand(islandManager));
        }

        var beotigiCommand = getCommand("beotigi");
        if (beotigiCommand != null) {
            BeotigiCommand executor = new BeotigiCommand(
                    this, weatherManager, meteorManager, voidStormManager, volcanoManager,
                    islandDiscoveryManager, specialItemManager, travelingMerchantManager,
                    skyCreatureManager, nightEventManager);
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
        if (islandDiscoveryManager != null) islandDiscoveryManager.stop();
        if (volcanoManager != null) volcanoManager.stop();
        if (ecosystemManager != null) ecosystemManager.stop();
        if (islandGrowthManager != null) islandGrowthManager.stop();
        if (villagerMigrationManager != null) villagerMigrationManager.stop();
        if (travelingMerchantManager != null) travelingMerchantManager.stop();
        if (skyCreatureManager != null) skyCreatureManager.stop();
        if (nightEventManager != null) nightEventManager.stop();
        if (skyblockWorldManager != null) skyblockWorldManager.stop();
    }
}
