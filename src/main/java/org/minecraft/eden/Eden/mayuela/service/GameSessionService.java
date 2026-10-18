package org.minecraft.eden.Eden.mayuela.service;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.minecraft.eden.Eden.mayuela.message.MayuelaColor;
import org.minecraft.eden.Eden.mayuela.message.MayuelaMessage;
import org.minecraft.eden.Eden.mayuela.message.MessageService;
import org.minecraft.eden.Eden.mayuela.model.GamePhase;
import org.minecraft.eden.Eden.mayuela.model.GamePlayer;
import org.minecraft.eden.Eden.mayuela.model.GameTeam;
import org.minecraft.eden.Eden.mayuela.model.StartGameResult;
import org.minecraft.eden.Eden.mayuela.repository.ConfigRepository;
import org.minecraft.eden.Eden.mayuela.sound.MayuelaSound;

public final class GameSessionService {

    private final int MIN_GAME_PLAYERS = 4;
    private final int MAX_GAME_PLAYERS = 10;

    private final JavaPlugin plugin;
    private final ConfigRepository configRepository;
    private final GamePlayerService gamePlayerService;
    private final TeamService teamService;
    private final AltarService altarService;
    private final InventoryService inventoryService;
    private final ScoreboardService scoreboardService;
    private final DisplayService displayService;
    private final CoinService coinService;
    private final MatchStatsService matchStatsService;
    private final MessageService messageService;
    private final KnockbackService knockbackService;
    private GamePhase phase = GamePhase.WAITING;
    private BukkitTask readyTask;
    private BukkitTask activationTask;
    private long playStartedAtMillis;
    private TeamService.MatchMode matchMode = TeamService.MatchMode.TEAM;

    public GameSessionService(JavaPlugin plugin, ConfigRepository configRepository, GamePlayerService gamePlayerService, TeamService teamService, AltarService altarService, InventoryService inventoryService, ScoreboardService scoreboardService, DisplayService displayService, CoinService coinService, MatchStatsService matchStatsService, MessageService messageService, KnockbackService knockbackService) {
        this.plugin = plugin;
        this.configRepository = configRepository;
        this.gamePlayerService = gamePlayerService;
        this.teamService = teamService;
        this.altarService = altarService;
        this.inventoryService = inventoryService;
        this.scoreboardService = scoreboardService;
        this.displayService = displayService;
        this.coinService = coinService;
        this.matchStatsService = matchStatsService;
        this.messageService = messageService;
        this.knockbackService = knockbackService;
    }

    public GamePhase phase() {
        return phase;
    }

    public StartGameResult start() {
        return start(TeamService.MatchMode.TEAM);
    }

    public StartGameResult start(TeamService.MatchMode matchMode) {
        List<String> reasons = startBlockReasons(matchMode);
        if (!reasons.isEmpty()) {
            return StartGameResult.failed(reasons);
        }
        if (!teamService.formTeams(gamePlayerService.all(), matchMode)) {
            return StartGameResult.failed(List.of(matchMode.displayName() + " 편성에 실패했습니다. 참가자 수와 색상 슬롯을 확인하세요."));
        }
        this.matchMode = matchMode;
        beginReadySession();
        return StartGameResult.success();
    }

    public List<String> startBlockReasons() {
        return startBlockReasons(TeamService.MatchMode.TEAM);
    }

    public List<String> startBlockReasons(TeamService.MatchMode matchMode) {
        List<String> reasons = new ArrayList<>();
        int playerCount = gamePlayerService.all().size();
        if (phase != GamePhase.WAITING) {
            reasons.add("현재 세션이 WAITING이 아닙니다. 현재 상태: " + phase);
        }
        if (matchMode == TeamService.MatchMode.TEAM && playerCount < MIN_GAME_PLAYERS) {
            reasons.add("참가자가 부족합니다. 현재 " + playerCount + "명, 최소 " + MIN_GAME_PLAYERS + "명 필요");
        }
        if (matchMode == TeamService.MatchMode.SOLO && playerCount < 2) {
            reasons.add("개인전은 최소 2명이 필요합니다. 현재 " + playerCount + "명");
        }
        if (playerCount > MAX_GAME_PLAYERS) {
            reasons.add("참가자가 너무 많습니다. 현재 " + playerCount + "명, 최대 " + MAX_GAME_PLAYERS + "명");
        }
        if (matchMode == TeamService.MatchMode.TEAM && playerCount % 2 != 0) {
            reasons.add("2인 팀 편성을 위해 짝수 인원이 필요합니다. 현재 " + playerCount + "명");
        }
        if (altarService.count() == 0) {
            reasons.add("등록된 제단이 없습니다. /game 제단등록 <이름>을 먼저 실행하세요.");
        }
        if (!configRepository.hasAnyMap()) {
            reasons.add("등록된 맵이 없습니다. /game 맵 pos1, /game 맵 pos2, /game 맵 저장 <이름>을 먼저 실행하세요.");
        }
        if (!configRepository.hasGameSpawn()) {
            reasons.add("게임 스폰 위치가 없습니다. /game 스폰등록을 먼저 실행하세요.");
        }
        return reasons;
    }

    private void beginReadySession() {
        phase = GamePhase.READY;
        Location lobby = configRepository.lobbyLocation();
        for (GamePlayer gamePlayer : gamePlayerService.all()) {
            Player player = Bukkit.getPlayer(gamePlayer.uuid());
            if (player == null) {
                continue;
            }
            GameTeam team = teamService.findByPlayer(gamePlayer.uuid()).orElseThrow();
            if (configRepository.hasLobby() && lobby != null) {
                player.teleport(lobby);
            }
            inventoryService.giveStartItems(player, team, altarService.count());
            displayService.updatePlayerIdentity(player, team, configRepository.bountyRewardCoins());
            messageService.send(player, MayuelaMessage.GAME_READY_ENTERED, matchMode.displayName(), team.color().displayName());
        }
        scoreboardService.updateBoard(gamePlayerService.all(), teamService.all());
        startReadyCountdown(configRepository.readySeconds());
    }

    private void startReadyCountdown(int seconds) {
        final int[] remaining = {seconds};
        readyTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (remaining[0] <= 0) {
                readyTask.cancel();
                beginPlaying();
                return;
            }
            sendReadyCountdownNotice(remaining[0]);
            remaining[0]--;
        }, 0L, 20L);
    }

    private void sendReadyCountdownNotice(int seconds) {
        Component message = Component.text("게임 시작까지 " + seconds + "초..");
        for (GamePlayer gamePlayer : gamePlayerService.all()) {
            Player player = Bukkit.getPlayer(gamePlayer.uuid());
            if (player == null) {
                continue;
            }
            player.sendActionBar(message);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.2f);
        }
    }

    private void beginPlaying() {
        phase = GamePhase.PLAYING;
        playStartedAtMillis = System.currentTimeMillis();
        scoreboardService.setPlaytimeSeconds(0L);
        matchStatsService.startMatch();
        altarService.resetRuntimeState();
        altarService.showEmptyAltarDisplays();
        Location gameSpawn = configRepository.gameSpawnLocation();
        for (GamePlayer gamePlayer : gamePlayerService.all()) {
            Player player = Bukkit.getPlayer(gamePlayer.uuid());
            if (player != null) {
                GameTeam team = teamService.findByPlayer(gamePlayer.uuid()).orElseThrow();
                if (gameSpawn != null) {
                    player.teleport(gameSpawn);
                }
                displayService.spawnPlayerDisplay(player, team, configRepository.bountyRewardCoins());
                inventoryService.ensureColorBlock(player, team, altarService.count());
                inventoryService.ensureOffhandLock(player);
            }
        }
        displayService.startFollowTask();
        coinService.start();
        int activationTickSeconds = Math.max(1, configRepository.activationTickSeconds());
        long period = activationTickSeconds * 20L;
        activationTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            scoreboardService.setPlaytimeSeconds(playElapsedSeconds());
            altarService.tickActivation(configRepository.baseIncreasePerAltar(), activationTickSeconds);
            matchStatsService.recordSnapshot(playElapsedSeconds(), teamService.all(), coinService.droppedCoinCount());
            altarService.findWinner().ifPresent(this::finish);
        }, period, period);
        Bukkit.broadcast(messageService.component(MayuelaMessage.GAME_STARTED));
    }

    private void finish(GameTeam winner) {
        phase = GamePhase.ENDING;
        scoreboardService.setPlaytimeSeconds(playElapsedSeconds());
        Bukkit.broadcast(Component.text(winnerName(winner) + "이(가) 게임에서 승리했습니다."));
        playWinSound();
        broadcastGameSummary(winner);
        matchStatsService.finishMatch(winner, playElapsedSeconds());
        forceStop();
    }

    public void forceStop() {
        knockbackService.stop();
        if (readyTask != null) {
            readyTask.cancel();
            readyTask = null;
        }
        if (activationTask != null) {
            activationTask.cancel();
            activationTask = null;
        }
        if (matchStatsService.isActive()) {
            matchStatsService.forceFinish(playElapsedSeconds(), "FORCE_STOP");
        }
        closeParticipantInventories();
        resetParticipantIdentities();
        altarService.clearBountyTabFooter();
        displayService.removeAll();
        coinService.stop();
        scoreboardService.clear(gamePlayerService.all());
        inventoryService.resetAll(gamePlayerService.all());
        returnPlayersToGameSpawn();
        altarService.resetRuntimeState();
        teamService.clear();
        gamePlayerService.all().forEach(GamePlayer::resetGameState);
        playStartedAtMillis = 0L;
        scoreboardService.setPlaytimeSeconds(0L);
        phase = GamePhase.WAITING;
        matchMode = TeamService.MatchMode.TEAM;
        scoreboardService.updateBoard(gamePlayerService.all(), teamService);
    }

    public void cancelReadySession() {
        if (phase != GamePhase.READY) {
            return;
        }
        if (readyTask != null) {
            readyTask.cancel();
            readyTask = null;
        }
        closeParticipantInventories();
        resetParticipantIdentities();
        inventoryService.restoreAll(gamePlayerService.all());
        playStartedAtMillis = 0L;
        scoreboardService.setPlaytimeSeconds(0L);
        phase = GamePhase.WAITING;
        matchMode = TeamService.MatchMode.TEAM;
        scoreboardService.updateBoard(gamePlayerService.all(), teamService);
    }

    private void playWinSound() {
        for (GamePlayer gamePlayer : gamePlayerService.all()) {
            Player player = Bukkit.getPlayer(gamePlayer.uuid());
            if (player != null) {
                MayuelaSound.GAME_WIN.play(player);
            }
        }
    }

    private void resetParticipantIdentities() {
        for (GamePlayer gamePlayer : gamePlayerService.all()) {
            Player player = Bukkit.getPlayer(gamePlayer.uuid());
            if (player != null) {
                displayService.resetPlayerIdentity(player);
            }
        }
    }

    private void closeParticipantInventories() {
        for (GamePlayer gamePlayer : gamePlayerService.all()) {
            Player player = Bukkit.getPlayer(gamePlayer.uuid());
            if (player != null) {
                player.closeInventory();
            }
        }
    }

    private void broadcastGameSummary(GameTeam winner) {
        Bukkit.broadcast(Component.text("#게임 결과", color(MayuelaColor.HIGHLIGHT)));
        Bukkit.broadcast(Component.text("승리: ", color(MayuelaColor.TEXT))
                .append(winnerNameComponent(winner)));
        Bukkit.broadcast(Component.text("게임 플레이 타임: ", color(MayuelaColor.TEXT))
                .append(Component.text(formatTime(playElapsedSeconds()), color(MayuelaColor.VALUE))));
        for (GameTeam team : teamService.all()) {
            Bukkit.broadcast(Component.text("", color(MayuelaColor.TEXT))
                    .append(teamName(team))
                    .append(Component.text(" 코인: ", color(MayuelaColor.TEXT)))
                    .append(Component.text(teamCoins(team), color(MayuelaColor.VALUE)))
                    .append(Component.text(" | 점령율: ", color(MayuelaColor.TEXT)))
                    .append(Component.text(formatPercent(team.activationRate()), color(MayuelaColor.VALUE))));
        }
        Bukkit.broadcast(Component.text("============================", color(MayuelaColor.TEXT)));
    }

    private Component winnerNameComponent(GameTeam winner) {
        if (matchMode == TeamService.MatchMode.TEAM) {
            return teamName(winner);
        }
        return winner.members().stream()
                .findFirst()
                .flatMap(gamePlayerService::find)
                .<Component>map(player -> Component.text(player.name(), color(MayuelaColor.VALUE))
                        .append(Component.text(" (", color(MayuelaColor.TEXT)))
                        .append(teamName(winner))
                        .append(Component.text(")", color(MayuelaColor.TEXT))))
                .orElse(teamName(winner));
    }

    private Component teamName(GameTeam team) {
        return Component.text(team.color().displayName() + " 팀", teamColor(team));
    }

    private TextColor color(MayuelaColor color) {
        TextColor textColor = TextColor.fromHexString(color.hex());
        return textColor == null ? TextColor.color(0xFFFFFF) : textColor;
    }

    private TextColor teamColor(GameTeam team) {
        TextColor textColor = TextColor.fromHexString(team.color().hex());
        return textColor == null ? color(MayuelaColor.VALUE) : textColor;
    }

    private String winnerName(GameTeam winner) {
        if (matchMode == TeamService.MatchMode.TEAM) {
            return winner.color().displayName() + " 팀";
        }
        return winner.members().stream()
                .findFirst()
                .flatMap(gamePlayerService::find)
                .map(player -> player.name() + " (" + winner.color().displayName() + ")")
                .orElse(winner.color().displayName());
    }

    private int teamCoins(GameTeam team) {
        int coins = 0;
        for (java.util.UUID member : team.members()) {
            coins += gamePlayerService.find(member)
                    .map(GamePlayer::coins)
                    .orElse(0);
        }
        return coins;
    }

    public long playElapsedSeconds() {
        if (playStartedAtMillis <= 0L) {
            return 0L;
        }
        return Math.max(0L, (System.currentTimeMillis() - playStartedAtMillis) / 1000L);
    }

    private String formatTime(long seconds) {
        long minutes = seconds / 60;
        long remainSeconds = seconds % 60;
        return String.format("%02d:%02d", minutes, remainSeconds);
    }

    private String formatPercent(double value) {
        return String.format("%.1f%%", value);
    }

    private void returnPlayersToGameSpawn() {
        Location gameSpawn = configRepository.gameSpawnLocation();
        if (gameSpawn == null) {
            return;
        }
        for (GamePlayer gamePlayer : gamePlayerService.all()) {
            Player player = Bukkit.getPlayer(gamePlayer.uuid());
            if (player != null) {
                player.teleport(gameSpawn);
            }
        }
    }
}
