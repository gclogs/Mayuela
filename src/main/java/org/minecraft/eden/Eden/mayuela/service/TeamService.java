package org.minecraft.eden.Eden.mayuela.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.minecraft.eden.Eden.mayuela.model.GamePlayer;
import org.minecraft.eden.Eden.mayuela.model.GameTeam;
import org.minecraft.eden.Eden.mayuela.model.PlayerColor;

public final class TeamService {

    private static final int TEAM_SIZE = 2;
    private final Map<String, GameTeam> teams = new LinkedHashMap<>();
    private final Map<UUID, String> playerTeams = new LinkedHashMap<>();
    private final Map<UUID, PlayerColor> manualColors = new LinkedHashMap<>();

    public boolean formTeams(Collection<GamePlayer> players) {
        return formTeams(players, MatchMode.TEAM);
    }

    public boolean formTeams(Collection<GamePlayer> players, MatchMode matchMode) {
        if (matchMode == MatchMode.SOLO) {
            return formSoloTeams(players);
        }
        if (players.size() < 4 || players.size() > 10 || players.size() % TEAM_SIZE != 0) {
            return false;
        }
        teams.clear();
        playerTeams.clear();
        List<GamePlayer> unassignedPlayers = new ArrayList<>(players);
        Collections.shuffle(unassignedPlayers);
        List<PlayerColor> colors = PlayerColor.defaults();
        int teamCount = players.size() / TEAM_SIZE;

        for (PlayerColor color : colors) {
            List<GamePlayer> manuallyAssigned = players.stream()
                    .filter(player -> color.equals(manualColors.get(player.uuid())))
                    .toList();
            if (manuallyAssigned.size() > TEAM_SIZE) {
                clearRuntimeTeams();
                return false;
            }
            if (manuallyAssigned.isEmpty()) {
                continue;
            }
            if (teams.size() >= teamCount) {
                clearRuntimeTeams();
                return false;
            }
            GameTeam team = new GameTeam("team_" + (teams.size() + 1), color);
            manuallyAssigned.forEach(player -> addMember(team, player));
            unassignedPlayers.removeAll(manuallyAssigned);
            while (team.members().size() < TEAM_SIZE && !unassignedPlayers.isEmpty()) {
                addMember(team, unassignedPlayers.remove(0));
            }
            teams.put(team.id(), team);
        }

        int colorIndex = 0;
        while (!unassignedPlayers.isEmpty()) {
            PlayerColor color = nextUnusedColor(colors, colorIndex);
            colorIndex = colors.indexOf(color) + 1;
            GameTeam team = new GameTeam("team_" + (teams.size() + 1), color);
            for (int memberIndex = 0; memberIndex < TEAM_SIZE; memberIndex++) {
                if (unassignedPlayers.isEmpty()) {
                    clearRuntimeTeams();
                    return false;
                }
                addMember(team, unassignedPlayers.remove(0));
            }
            teams.put(team.id(), team);
        }
        return true;
    }

    private boolean formSoloTeams(Collection<GamePlayer> players) {
        List<PlayerColor> colors = PlayerColor.defaults();
        if (players.size() < 2 || players.size() > colors.size()) {
            return false;
        }
        teams.clear();
        playerTeams.clear();
        List<GamePlayer> orderedPlayers = new ArrayList<>(players);
        Collections.shuffle(orderedPlayers);

        for (GamePlayer player : players) {
            PlayerColor color = manualColors.get(player.uuid());
            if (color == null) {
                continue;
            }
            if (teams.values().stream().anyMatch(team -> team.color().equals(color))) {
                clearRuntimeTeams();
                return false;
            }
            GameTeam team = new GameTeam("team_" + (teams.size() + 1), color);
            addMember(team, player);
            teams.put(team.id(), team);
            orderedPlayers.remove(player);
        }

        int colorIndex = 0;

        for (GamePlayer player : orderedPlayers) {
            PlayerColor color = nextUnusedColor(colors, colorIndex);
            colorIndex = colors.indexOf(color) + 1;
            GameTeam team = new GameTeam("team_" + (teams.size() + 1), color);
            addMember(team, player);
            teams.put(team.id(), team);
        }
        return true;
    }

    public boolean assignManualColor(GamePlayer player, PlayerColor color) {
        long assignedCount = manualColors.values().stream().filter(color::equals).count();
        PlayerColor previous = manualColors.get(player.uuid());
        if (!color.equals(previous) && assignedCount >= TEAM_SIZE) {
            return false;
        }
        manualColors.put(player.uuid(), color);
        return true;
    }

    public void clearManualColor(UUID playerId) {
        manualColors.remove(playerId);
    }

    public Optional<PlayerColor> manualColor(UUID playerId) {
        return Optional.ofNullable(manualColors.get(playerId));
    }

    public Optional<GameTeam> findByPlayer(UUID playerId) {
        return Optional.ofNullable(playerTeams.get(playerId)).map(teams::get);
    }

    public Optional<GameTeam> removePlayer(UUID playerId) {
        String teamId = playerTeams.remove(playerId);
        manualColors.remove(playerId);
        if (teamId == null) {
            return Optional.empty();
        }
        GameTeam team = teams.get(teamId);
        if (team == null) {
            return Optional.empty();
        }
        team.members().remove(playerId);
        return Optional.of(team);
    }

    public Optional<GameTeam> find(String teamId) {
        return Optional.ofNullable(teams.get(teamId));
    }

    public Collection<GameTeam> all() {
        return teams.values();
    }

    public void clear() {
        clearRuntimeTeams();
        manualColors.clear();
    }

    private void addMember(GameTeam team, GamePlayer player) {
        team.members().add(player.uuid());
        playerTeams.put(player.uuid(), team.id());
    }

    private PlayerColor nextUnusedColor(List<PlayerColor> colors, int startIndex) {
        for (int index = startIndex; index < colors.size(); index++) {
            PlayerColor color = colors.get(index);
            boolean used = teams.values().stream().anyMatch(team -> team.color().equals(color));
            if (!used) {
                return color;
            }
        }
        return colors.get(0);
    }

    private void clearRuntimeTeams() {
        teams.clear();
        playerTeams.clear();
    }

    public enum MatchMode {
        TEAM("팀전"),
        SOLO("개인전");

        private final String displayName;

        MatchMode(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }
}
