package org.minecraft.eden.Eden.mayuela.command;

import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.minecraft.eden.Eden.mayuela.message.MessageService;
import org.minecraft.eden.Eden.mayuela.model.Altar;
import org.minecraft.eden.Eden.mayuela.model.GamePhase;
import org.minecraft.eden.Eden.mayuela.model.GamePlayer;
import org.minecraft.eden.Eden.mayuela.model.PlayerColor;
import org.minecraft.eden.Eden.mayuela.model.StartGameResult;
import org.minecraft.eden.Eden.mayuela.repository.ConfigRepository;
import org.minecraft.eden.Eden.mayuela.service.AltarService;
import org.minecraft.eden.Eden.mayuela.service.CoinService;
import org.minecraft.eden.Eden.mayuela.service.GamePlayerService;
import org.minecraft.eden.Eden.mayuela.service.GameSessionService;
import org.minecraft.eden.Eden.mayuela.service.MapService;
import org.minecraft.eden.Eden.mayuela.service.PdcMonitorService;
import org.minecraft.eden.Eden.mayuela.service.ScoreboardService;
import org.minecraft.eden.Eden.mayuela.service.TeamService;

public final class GameCommand implements CommandExecutor, TabCompleter {

    private final ConfigRepository configRepository;
    private final MapService mapService;
    private final AltarService altarService;
    private final GameSessionService gameSessionService;
    private final CoinService coinService;
    private final GamePlayerService gamePlayerService;
    private final TeamService teamService;
    private final ScoreboardService scoreboardService;
    private final PdcMonitorService pdcMonitorService;
    private final MessageService messageService;

    public GameCommand(ConfigRepository configRepository, MapService mapService, AltarService altarService, GameSessionService gameSessionService, CoinService coinService, GamePlayerService gamePlayerService, TeamService teamService, ScoreboardService scoreboardService, PdcMonitorService pdcMonitorService, MessageService messageService) {
        this.configRepository = configRepository;
        this.mapService = mapService;
        this.altarService = altarService;
        this.gameSessionService = gameSessionService;
        this.coinService = coinService;
        this.gamePlayerService = gamePlayerService;
        this.teamService = teamService;
        this.scoreboardService = scoreboardService;
        this.pdcMonitorService = pdcMonitorService;
        this.messageService = messageService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("mayuela.admin")) {
            sender.sendMessage(Component.text("권한이 없습니다."));
            return true;
        }
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        switch (args[0]) {
            case "시작" -> {
                return startGame(sender, TeamService.MatchMode.TEAM);
            }
            case "팀전" -> {
                if (args.length < 2 || !args[1].equals("시작")) {
                    sender.sendMessage(Component.text("/game 팀전 시작"));
                    return true;
                }
                return startGame(sender, TeamService.MatchMode.TEAM);
            }
            case "개인전" -> {
                if (args.length < 2 || !args[1].equals("시작")) {
                    sender.sendMessage(Component.text("/game 개인전 시작"));
                    return true;
                }
                return startGame(sender, TeamService.MatchMode.SOLO);
            }
            case "종료" -> {
                gameSessionService.forceStop();
                sender.sendMessage(Component.text("게임을 종료했습니다."));
                return true;
            }
            case "reload", "리로드" -> {
                if (gameSessionService.phase() != GamePhase.WAITING) {
                    sender.sendMessage(Component.text("게임 중에는 설정을 다시 로드할 수 없습니다. /game 종료 후 실행하세요."));
                    return true;
                }
                configRepository.load();
                messageService.reload();
                altarService.reload();
                sender.sendMessage(Component.text("게임 설정을 다시 로드했습니다: game.yml, alter.yml, maps.yml, model.yml, messages.yml"));
                sender.sendMessage(Component.text("로드된 제단: " + altarService.count() + "개"));
                return true;
            }
            case "맵" -> {
                return handleMap(sender, args);
            }
            case "제단등록" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("플레이어만 사용할 수 있습니다."));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Component.text("/game 제단등록 <이름>"));
                    return true;
                }
                Block targetBlock = player.getTargetBlockExact(8);
                if (targetBlock == null) {
                    sender.sendMessage(Component.text("바라보고 있는 신호기 또는 설치 위치를 찾을 수 없습니다."));
                    return true;
                }
                Block altarBlock = targetBlock.getType() == Material.BEACON
                        ? targetBlock
                        : targetBlock.getRelative(org.bukkit.block.BlockFace.DOWN).getType() == Material.BEACON
                                ? targetBlock.getRelative(org.bukkit.block.BlockFace.DOWN)
                                : targetBlock;
                altarService.register(args[1], altarBlock.getLocation());
                sender.sendMessage(Component.text("제단을 등록했습니다: " + args[1] + " - " + altarBlock.getWorld().getName()
                        + " (" + altarBlock.getX() + ", " + altarBlock.getY() + ", " + altarBlock.getZ() + ")"));
                return true;
            }
            case "제단목록" -> {
                for (Altar altar : altarService.all()) {
                    sender.sendMessage(Component.text(altar.id() + " - " + altar.location().getWorld().getName()
                            + " (" + altar.location().getBlockX() + ", " + altar.location().getBlockY() + ", " + altar.location().getBlockZ() + ")"));
                }
                return true;
            }
            case "상점등록" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("플레이어만 사용할 수 있습니다."));
                    return true;
                }
                configRepository.saveShop(player.getLocation());
                sender.sendMessage(Component.text("상점 위치를 등록했습니다."));
                return true;
            }
            case "스폰등록" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("플레이어만 사용할 수 있습니다."));
                    return true;
                }
                configRepository.saveGameSpawn(player.getLocation());
                sender.sendMessage(Component.text("게임 스폰 위치를 등록했습니다."));
                return true;
            }
            case "팀지정" -> {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("/game 팀지정 <플레이어> <색상>"));
                    return true;
                }
                GamePlayer target = findGamePlayer(args[1]);
                PlayerColor color = findColor(args[2]);
                if (target == null) {
                    sender.sendMessage(Component.text("참가 중인 플레이어를 찾을 수 없습니다: " + args[1]));
                    return true;
                }
                if (color == null) {
                    sender.sendMessage(Component.text("알 수 없는 색상입니다. 예: 빨강, 노랑, 파랑, 초록, 분홍"));
                    return true;
                }
                if (!teamService.assignManualColor(target, color)) {
                    sender.sendMessage(Component.text(color.displayName() + " 팀은 이미 2명이 지정되어 있습니다."));
                    return true;
                }
                scoreboardService.updateBoard(gamePlayerService.all(), teamService);
                sender.sendMessage(Component.text(target.name() + " 플레이어를 " + color.displayName() + " 팀으로 지정했습니다."));
                return true;
            }
            case "팀해제" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("/game 팀해제 <플레이어>"));
                    return true;
                }
                GamePlayer target = findGamePlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(Component.text("참가 중인 플레이어를 찾을 수 없습니다: " + args[1]));
                    return true;
                }
                teamService.clearManualColor(target.uuid());
                scoreboardService.updateBoard(gamePlayerService.all(), teamService);
                sender.sendMessage(Component.text(target.name() + " 플레이어의 팀 지정을 해제했습니다."));
                return true;
            }
            case "모니터링" -> {
                sender.sendMessage(Component.text("상태: " + gameSessionService.phase()));
                sender.sendMessage(Component.text("제단: " + altarService.count() + "개"));
                sender.sendMessage(Component.text("게임 스폰: " + (configRepository.hasGameSpawn() ? "등록됨" : "미등록")));
                for (Component line : coinService.monitoringLines()) {
                    sender.sendMessage(line);
                }
                for (Component line : altarService.bountyMonitoringLines(gameSessionService.playElapsedSeconds())) {
                    sender.sendMessage(line);
                }
                int coinRulePlayers = ConfigRepository.normalizedCoinRulePlayerCount(gamePlayerService.all().size());
                sender.sendMessage(Component.text("필드 코인: " + coinService.droppedCoinCount() + " / " + configRepository.maxDroppedCoins()));
                sender.sendMessage(Component.text("코인 적용 룰: " + coinRulePlayers + "명, " + configRepository.coinDropIntervalSeconds(coinRulePlayers) + "초마다 +" + configRepository.coinDropAmount(coinRulePlayers)));
                sender.sendMessage(Component.text("코인 자동 제거: " + configRepository.coinDespawnSeconds() + "초"));
                sender.sendMessage(Component.text("밀치기 막대기: " + configRepository.knockbackStickBlocks() + "블록"));
                sender.sendMessage(Component.text("밀치기 막대기 보유 한도: " + configRepository.knockbackStickMaxHeld() + "개"));
                return true;
            }
            case "pdc", "태그확인" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("플레이어만 사용할 수 있습니다."));
                    return true;
                }
                List<Component> lines = args.length >= 2 && args[1].equals("인벤토리")
                        ? pdcMonitorService.describeInventory(player.getInventory())
                        : pdcMonitorService.describeHands(player.getInventory());
                sender.sendMessage(Component.text("Mayuela PDC 태그 모니터링"));
                for (Component line : lines) {
                    sender.sendMessage(line);
                }
                return true;
            }
            case "코인설정" -> {
                if (args.length < 4) {
                    sender.sendMessage(Component.text("/game 코인설정 <1-10> <주기초> <금액>"));
                    return true;
                }
                Integer players = parseInt(args[1]);
                Integer interval = parseInt(args[2]);
                Integer amount = parseInt(args[3]);
                if (players == null || interval == null || amount == null || players < 1 || players > 10 || interval < 1 || amount < 1) {
                    sender.sendMessage(Component.text("인원은 1~10, 주기/금액은 1 이상으로 입력하세요."));
                    return true;
                }
                configRepository.saveCoinRule(players, interval, amount);
                sender.sendMessage(Component.text(players + "명 코인 규칙을 저장했습니다. 주기 " + interval + "초, 금액 " + amount));
                return true;
            }
            case "코인제거" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("/game 코인제거 <초>"));
                    return true;
                }
                Integer seconds = parseInt(args[1]);
                if (seconds == null || seconds < 1) {
                    sender.sendMessage(Component.text("1초 이상으로 입력하세요."));
                    return true;
                }
                configRepository.saveCoinDespawnSeconds(seconds);
                sender.sendMessage(Component.text("코인 자동 제거 시간을 " + seconds + "초로 저장했습니다."));
                return true;
            }
            case "코인최대" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("/game 코인최대 <개수>"));
                    return true;
                }
                Integer max = parseInt(args[1]);
                if (max == null || max < 1) {
                    sender.sendMessage(Component.text("1개 이상으로 입력하세요."));
                    return true;
                }
                configRepository.saveMaxDroppedCoins(max);
                sender.sendMessage(Component.text("필드 코인 최대 개수를 " + max + "개로 저장했습니다."));
                return true;
            }
            case "넉백설정" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("/game 넉백설정 <블록>"));
                    return true;
                }
                Integer blocks = parseInt(args[1]);
                if (blocks == null || blocks < 1) {
                    sender.sendMessage(Component.text("1블록 이상으로 입력하세요."));
                    return true;
                }
                configRepository.saveKnockbackStickBlocks(blocks);
                sender.sendMessage(Component.text("밀치기 막대기 거리를 " + blocks + "블록으로 저장했습니다."));
                return true;
            }
            case "막대기최대" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("/game 막대기최대 <개수>"));
                    return true;
                }
                Integer maxHeld = parseInt(args[1]);
                if (maxHeld == null || maxHeld < 1) {
                    sender.sendMessage(Component.text("1개 이상으로 입력하세요."));
                    return true;
                }
                configRepository.saveKnockbackStickMaxHeld(maxHeld);
                sender.sendMessage(Component.text("밀치기 막대기 보유 한도를 " + maxHeld + "개로 저장했습니다."));
                return true;
            }
            default -> {
                sendUsage(sender);
                return true;
            }
        }
    }

    private boolean handleMap(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("플레이어만 사용할 수 있습니다."));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("/game 맵 <pos1|pos2|저장>"));
            return true;
        }
        switch (args[1]) {
            case "pos1" -> {
                mapService.setPosition1(player);
                sender.sendMessage(Component.text("맵 pos1을 지정했습니다."));
                return true;
            }
            case "pos2" -> {
                mapService.setPosition2(player);
                sender.sendMessage(Component.text("맵 pos2를 지정했습니다."));
                return true;
            }
            case "저장" -> {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("/game 맵 저장 <이름>"));
                    return true;
                }
                boolean saved = mapService.save(player, args[2]);
                sender.sendMessage(Component.text(saved ? "맵을 저장했습니다: " + args[2] : "pos1과 pos2를 먼저 지정하세요."));
                return true;
            }
            default -> {
                sender.sendMessage(Component.text("/game 맵 <pos1|pos2|저장>"));
                return true;
            }
        }
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Component.text("/game <시작|팀전 시작|개인전 시작|종료|reload|맵|제단등록|제단목록|상점등록|스폰등록|팀지정|팀해제|모니터링|pdc|코인설정|코인제거|코인최대|넉백설정|막대기최대>"));
    }

    private boolean startGame(CommandSender sender, TeamService.MatchMode matchMode) {
        StartGameResult result = gameSessionService.start(matchMode);
        if (result.started()) {
            sender.sendMessage(Component.text(matchMode.displayName() + " 게임을 시작합니다."));
            return true;
        }
        sender.sendMessage(Component.text(matchMode.displayName() + " 게임을 시작할 수 없습니다. 준비되지 않은 항목:"));
        for (String reason : result.reasons()) {
            sender.sendMessage(Component.text("- " + reason));
        }
        return true;
    }

    private GamePlayer findGamePlayer(String name) {
        return gamePlayerService.all().stream()
                .filter(player -> player.name().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    private PlayerColor findColor(String value) {
        return PlayerColor.defaults().stream()
                .filter(color -> color.id().equalsIgnoreCase(value) || color.displayName().equalsIgnoreCase(value))
                .findFirst()
                .orElse(null);
    }

    private Integer parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("시작", "팀전", "개인전", "종료", "reload", "리로드", "맵", "제단등록", "제단목록", "상점등록", "스폰등록", "팀지정", "팀해제", "모니터링", "pdc", "태그확인", "코인설정", "코인제거", "코인최대", "넉백설정", "막대기최대");
        }
        if (args.length == 2 && (args[0].equals("팀전") || args[0].equals("개인전"))) {
            return List.of("시작");
        }
        if (args.length == 2 && args[0].equals("맵")) {
            return List.of("pos1", "pos2", "저장");
        }
        if (args.length == 2 && args[0].equals("코인설정")) {
            return List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10");
        }
        if (args.length == 3 && args[0].equals("팀지정")) {
            return PlayerColor.defaults().stream().map(PlayerColor::displayName).toList();
        }
        if (args.length == 2 && (args[0].equals("pdc") || args[0].equals("태그확인"))) {
            return List.of("인벤토리");
        }
        return List.of();
    }
}
