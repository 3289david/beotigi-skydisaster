package com.beotigi.skydisaster.world;

import com.beotigi.skydisaster.BeotigiPlugin;
import com.beotigi.skydisaster.island.IslandType;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 어떤 플레이어가 어떤 시작 섬을 받았는지 기억한다 (재시작해도 유지).
 * 새 섬 발견 목록처럼 무거운 상태가 아니라 플레이어 수만큼만 있는 아주 작은 파일이라
 * YAML로 충분하다.
 */
public class PlayerAssignmentStore {

    private final File file;
    private final YamlConfiguration config;
    private final Map<UUID, IslandType> assignments = new HashMap<>();

    public PlayerAssignmentStore(BeotigiPlugin plugin) {
        this.file = new File(plugin.getDataFolder(), "assignments.yml");
        this.config = YamlConfiguration.loadConfiguration(file);
        for (String key : config.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                IslandType type = IslandType.valueOf(config.getString(key));
                assignments.put(uuid, type);
            } catch (IllegalArgumentException ignored) {
                // 손상된 항목은 건너뛴다.
            }
        }
    }

    public IslandType getAssignment(UUID playerId) {
        return assignments.get(playerId);
    }

    public void setAssignment(UUID playerId, IslandType type) {
        assignments.put(playerId, type);
        config.set(playerId.toString(), type.name());
    }

    public Map<IslandType, Integer> countsByType() {
        Map<IslandType, Integer> counts = new EnumMap<>(IslandType.class);
        for (IslandType type : IslandType.values()) counts.put(type, 0);
        for (IslandType type : assignments.values()) {
            counts.merge(type, 1, Integer::sum);
        }
        return counts;
    }

    public void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save assignments.yml", e);
        }
    }
}
