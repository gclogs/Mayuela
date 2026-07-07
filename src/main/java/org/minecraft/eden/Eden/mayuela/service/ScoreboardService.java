package org.minecraft.eden.Eden.mayuela.service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.minecraft.eden.Eden.mayuela.model.GamePlayer;
import org.minecraft.eden.Eden.mayuela.model.GameTeam;

public final class ScoreboardService {

    private static final String[] HIDDEN_ENTRIES = {
            "\u00A70", "\u00A71", "\u00A72", "\u00A73", "\u00A74",
            "\u00A75", "\u00A76", "\u00A77", "\u00A78", "\u00A79"
    };

    private BossBar bossBar;
    private long playtimeSeconds;

    public void setPlaytimeSeconds(long playtimeSeconds) {
        this.playtimeSeconds = Math.max(0L, playtimeSeconds);
    }

    public void updateBoard(Iterable<GamePlayer> players) {
        updateReadyBoard(players, null);
    }

    public void updateBoard(Iterable<GamePlayer> players, TeamService teamService) {
        updateReadyBoard(players, teamService);
    }

    private void updateReadyBoard(Iterable<GamePlayer> players, TeamService teamService) {
        for (GamePlayer viewer : players) {
            Player bukkitPlayer = Bukkit.getPlayer(viewer.uuid());
            if (bukkitPlayer == null) {
                continue;
            }
            Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective objective = scoreboard.registerNewObjective("mayuela", Criteria.DUMMY, Component.text("마쥬엘라"));
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);

            int score = 15;
            int lineIndex = 0;
            for (GamePlayer gamePlayer : players) {
                if (lineIndex >= HIDDEN_ENTRIES.length) {
                    break;
                }
                Component status = teamService == null
                        ? Component.text("준비")
                        : teamService.manualColor(gamePlayer.uuid())
                                .map(playerColor -> Component.text(playerColor.displayName() + " 팀", TextColor.fromHexString(playerColor.hex())))
                                .orElse(Component.text("준비"));
                Component line = Component.text(gamePlayer.name() + " >> ").append(status);
                String entry = HIDDEN_ENTRIES[lineIndex++];
                Team scoreboardTeam = scoreboard.registerNewTeam("line_" + lineIndex);
                scoreboardTeam.addEntry(entry);
                scoreboardTeam.prefix(line);
                objective.getScore(entry).setScore(score--);
            }
            bukkitPlayer.setScoreboard(scoreboard);
        }
    }

    public void updateBoard(Iterable<GamePlayer> players, Iterable<GameTeam> teams) {
        List<GameTeam> ranking = sortedRanking(teams);
        Map<UUID, String> playerNames = playerNames(players);
        updateBossBar(ranking, players);
        for (GamePlayer viewer : players) {
            Player bukkitPlayer = Bukkit.getPlayer(viewer.uuid());
            if (bukkitPlayer == null) {
                continue;
            }
            Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective objective = scoreboard.registerNewObjective("mayuela", Criteria.DUMMY, Component.text(formatTime(playtimeSeconds)));
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            registerPlayerColorTeams(scoreboard, ranking, playerNames);

            int score = 15;
            int lineIndex = 0;
            for (int i = 0; i < Math.min(5, ranking.size()); i++) {
                GameTeam team = ranking.get(i);
                TextColor color = TextColor.fromHexString(team.color().hex());
                Component line = Component.text((i + 1) + "위 ")
                        .append(Component.text(team.color().displayName(), color))
                        .append(Component.text(" (" + formatPercent(team.activationRate()) + ")"));
                String entry = HIDDEN_ENTRIES[lineIndex++];
                Team scoreboardTeam = scoreboard.registerNewTeam("line_" + lineIndex);
                scoreboardTeam.addEntry(entry);
                scoreboardTeam.prefix(line);
                objective.getScore(entry).setScore(score--);
            }
            Component bountyLine = bountyLine(ranking);
            if (bountyLine != null && lineIndex < HIDDEN_ENTRIES.length) {
                String entry = HIDDEN_ENTRIES[lineIndex++];
                Team scoreboardTeam = scoreboard.registerNewTeam("line_" + lineIndex);
                scoreboardTeam.addEntry(entry);
                scoreboardTeam.prefix(bountyLine);
                objective.getScore(entry).setScore(score--);
            }
            GameTeam viewerTeam = findViewerTeam(viewer.uuid(), ranking);
            if (viewerTeam != null && lineIndex < HIDDEN_ENTRIES.length) {
                TextColor color = TextColor.fromHexString(viewerTeam.color().hex());
                Component line = Component.text("내 팀 ")
                        .append(Component.text(viewerTeam.color().displayName(), color));
                String entry = HIDDEN_ENTRIES[lineIndex++];
                Team scoreboardTeam = scoreboard.registerNewTeam("line_" + lineIndex);
                scoreboardTeam.addEntry(entry);
                scoreboardTeam.prefix(line);
                objective.getScore(entry).setScore(score--);
            }
            if (viewerTeam != null) {
                for (UUID memberId : viewerTeam.members()) {
                    if (lineIndex >= HIDDEN_ENTRIES.length) {
                        break;
                    }
                    Component line = Component.text("팀원 " + playerNames.getOrDefault(memberId, "알 수 없음"));
                    String entry = HIDDEN_ENTRIES[lineIndex++];
                    Team scoreboardTeam = scoreboard.registerNewTeam("line_" + lineIndex);
                    scoreboardTeam.addEntry(entry);
                    scoreboardTeam.prefix(line);
                    objective.getScore(entry).setScore(score--);
                }
            }
            bukkitPlayer.setScoreboard(scoreboard);
        }
    }

    public void clear(Iterable<GamePlayer> players) {
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }
        for (GamePlayer player : players) {
            Player bukkitPlayer = Bukkit.getPlayer(player.uuid());
            if (bukkitPlayer != null) {
                bukkitPlayer.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
            }
        }
    }

    public void clearPlayer(Player player) {
        player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
        if (bossBar != null) {
            bossBar.removePlayer(player);
        }
    }

    private String legacyHex(String hex) {
        String normalized = hex.replace("#", "");
        if (normalized.length() != 6) {
            return "§f";
        }

        StringBuilder builder = new StringBuilder("§x");
        for (char character : normalized.toCharArray()) {
            builder.append('§').append(character);
        }
        return builder.toString();
    }

    private void updateBossBar(List<GameTeam> ranking, Iterable<GamePlayer> players) {
        if (bossBar == null) {
            bossBar = Bukkit.createBossBar("Mayuela", BarColor.WHITE, BarStyle.SOLID);
        }
        for (GamePlayer player : players) {
            Player bukkitPlayer = Bukkit.getPlayer(player.uuid());
            if (bukkitPlayer != null && !bossBar.getPlayers().contains(bukkitPlayer)) {
                bossBar.addPlayer(bukkitPlayer);
            }
        }
        if (ranking.isEmpty()) {
            bossBar.setTitle("§c1§f위 없음 (0.0%) §7| §f점령 누적 시간 §e00:00 |");
            bossBar.setProgress(0.0);
            bossBar.setColor(BarColor.WHITE);
            return;
        }
        GameTeam first = ranking.get(0);
        String teamColor = legacyHex(first.color().hex());
        bossBar.setTitle("§c1§f위 "
                + teamColor + first.color().displayName()
                + "§f (" + formatPercent(first.activationRate()) + ") "
                + "§7| §f점령 누적 시간 §e" + formatTime(first.capturedSeconds()) + " §7|");
        bossBar.setProgress(Math.max(0.0, Math.min(1.0, first.activationRate() / 100.0)));
        bossBar.setColor(toBarColor(first.color().id()));
    }

    private List<GameTeam> sortedRanking(Iterable<GameTeam> teams) {
        java.util.ArrayList<GameTeam> ranking = new java.util.ArrayList<>();
        teams.forEach(ranking::add);
        ranking.sort(Comparator
                .comparingDouble(GameTeam::activationRate).reversed()
                .thenComparing(GameTeam::capturedSeconds, Comparator.reverseOrder())
                .thenComparing(GameTeam::id));
        return ranking;
    }

    private Map<UUID, String> playerNames(Iterable<GamePlayer> players) {
        Map<UUID, String> names = new LinkedHashMap<>();
        for (GamePlayer player : players) {
            names.put(player.uuid(), player.name());
        }
        return names;
    }

    private void registerPlayerColorTeams(Scoreboard scoreboard, Iterable<GameTeam> teams, Map<UUID, String> playerNames) {
        int index = 0;
        for (GameTeam team : teams) {
            Team scoreboardTeam = scoreboard.registerNewTeam("player_color_" + index++);
            scoreboardTeam.color(toNamedColor(team.color().id()));
            for (UUID memberId : team.members()) {
                String playerName = playerNames.get(memberId);
                if (playerName != null) {
                    scoreboardTeam.addEntry(playerName);
                }
            }
        }
    }

    private Component bountyLine(List<GameTeam> ranking) {
        Component line = Component.text("현상수배 ");
        boolean found = false;
        for (GameTeam team : ranking) {
            if (!team.bountyWanted()) {
                continue;
            }
            if (found) {
                line = line.append(Component.text(", "));
            }
            line = line.append(Component.text(team.color().displayName(), TextColor.fromHexString(team.color().hex())));
            found = true;
        }
        return found ? line : null;
    }

    private GameTeam findViewerTeam(UUID viewerId, Iterable<GameTeam> teams) {
        for (GameTeam team : teams) {
            if (team.members().contains(viewerId)) {
                return team;
            }
        }
        return null;
    }

    private BarColor toBarColor(String colorId) {
        return switch (colorId) {
            case "RED" -> BarColor.RED;
            case "BLUE", "LIGHT_BLUE" -> BarColor.BLUE;
            case "GREEN" -> BarColor.GREEN;
            case "YELLOW", "ORANGE" -> BarColor.YELLOW;
            case "PURPLE", "PINK" -> BarColor.PURPLE;
            case "WHITE" -> BarColor.WHITE;
            default -> BarColor.WHITE;
        };
    }

    private NamedTextColor toNamedColor(String colorId) {
        return switch (colorId) {
            case "RED" -> NamedTextColor.RED;
            case "BLUE" -> NamedTextColor.BLUE;
            case "LIGHT_BLUE" -> NamedTextColor.AQUA;
            case "GREEN" -> NamedTextColor.GREEN;
            case "YELLOW" -> NamedTextColor.YELLOW;
            case "ORANGE" -> NamedTextColor.GOLD;
            case "PURPLE" -> NamedTextColor.DARK_PURPLE;
            case "PINK" -> NamedTextColor.LIGHT_PURPLE;
            case "BLACK" -> NamedTextColor.BLACK;
            default -> NamedTextColor.WHITE;
        };
    }

    private String formatPercent(double value) {
        return String.format("%.1f%%", value);
    }

    private String formatTime(long seconds) {
        long minutes = seconds / 60;
        long remainSeconds = seconds % 60;
        return String.format("%02d:%02d", minutes, remainSeconds);
    }
}
