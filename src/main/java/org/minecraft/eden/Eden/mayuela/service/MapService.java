package org.minecraft.eden.Eden.mayuela.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.minecraft.eden.Eden.mayuela.model.GameMap;
import org.minecraft.eden.Eden.mayuela.repository.ConfigRepository;

public final class MapService {

    private final ConfigRepository configRepository;
    private final Map<UUID, Location> position1 = new HashMap<>();
    private final Map<UUID, Location> position2 = new HashMap<>();
    private GameMap activeMap;

    public MapService(ConfigRepository configRepository) {
        this.configRepository = configRepository;
        this.activeMap = configRepository.firstMap().orElse(null);
    }

    public void setPosition1(Player player) {
        position1.put(player.getUniqueId(), player.getLocation().getBlock().getLocation());
    }

    public void setPosition2(Player player) {
        position2.put(player.getUniqueId(), player.getLocation().getBlock().getLocation());
    }

    public boolean save(Player player, String name) {
        Location first = position1.get(player.getUniqueId());
        Location second = position2.get(player.getUniqueId());
        if (first == null || second == null) {
            return false;
        }
        configRepository.saveMap(name, first, second);
        activeMap = new GameMap(name, first, second);
        return true;
    }

    public boolean isInsideGameArea(Location location) {
        return Optional.ofNullable(activeMap)
                .map(map -> map.contains(location))
                .orElse(true);
    }
}
