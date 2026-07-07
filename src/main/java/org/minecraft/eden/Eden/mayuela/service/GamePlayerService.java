package org.minecraft.eden.Eden.mayuela.service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.minecraft.eden.Eden.mayuela.model.GamePlayer;

public final class GamePlayerService {

    private final Map<UUID, GamePlayer> players = new LinkedHashMap<>();

    public GamePlayerService() {
    }

    public Optional<GamePlayer> join(Player player, int maxPlayers) {
        if (players.containsKey(player.getUniqueId())) {
            return Optional.of(players.get(player.getUniqueId()));
        }
        if (players.size() >= maxPlayers) {
            return Optional.empty();
        }
        GamePlayer gamePlayer = new GamePlayer(player.getUniqueId(), player.getName());
        players.put(player.getUniqueId(), gamePlayer);
        return Optional.of(gamePlayer);
    }

    public Optional<GamePlayer> find(UUID uuid) {
        return Optional.ofNullable(players.get(uuid));
    }

    public Collection<GamePlayer> all() {
        return players.values();
    }

    public void remove(UUID uuid) {
        players.remove(uuid);
    }

    public void clear() {
        players.clear();
    }
}
