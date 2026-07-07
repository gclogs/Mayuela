package org.minecraft.eden.Eden.mayuela.service;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.minecraft.eden.Eden.mayuela.message.MayuelaMessage;
import org.minecraft.eden.Eden.mayuela.message.MessageService;
import org.minecraft.eden.Eden.mayuela.model.Altar;
import org.minecraft.eden.Eden.mayuela.model.GamePlayer;
import org.minecraft.eden.Eden.mayuela.model.GameTeam;
import org.minecraft.eden.Eden.mayuela.repository.ConfigRepository;

public final class AltarService {

    private final ConfigRepository configRepository;
    private final GamePlayerService gamePlayerService;
    private final TeamService teamService;
    private final InventoryService inventoryService;
    private final ScoreboardService scoreboardService;
    private final DisplayService displayService;
    private final MatchStatsService matchStatsService;
    private final MessageService messageService;
    private final Map<String, Altar> altars = new LinkedHashMap<>();
    private long lastBountyTransitionSeconds = Long.MIN_VALUE;

    public AltarService(ConfigRepository configRepository, GamePlayerService gamePlayerService, TeamService teamService, InventoryService inventoryService, ScoreboardService scoreboardService, DisplayService displayService, MatchStatsService matchStatsService, MessageService messageService) {
        this.configRepository = configRepository;
        this.gamePlayerService = gamePlayerService;
        this.teamService = teamService;
        this.inventoryService = inventoryService;
        this.scoreboardService = scoreboardService;
        this.displayService = displayService;
        this.matchStatsService = matchStatsService;
        this.messageService = messageService;
        this.altars.putAll(configRepository.loadAltars());
    }

    public void register(String id, Location location) {
        Altar altar = new Altar(id, location.getBlock().getLocation());
        altars.put(id, altar);
        configRepository.saveAltar(id, altar.location());
    }

    public void reload() {
        altars.clear();
        altars.putAll(configRepository.loadAltars());
    }

    public Collection<Altar> all() {
        return altars.values();
    }

    public int count() {
        return altars.size();
    }

    public Optional<Altar> findByPlacedBlock(Block block) {
        Location placed = block.getLocation();
        Location below = block.getRelative(BlockFace.DOWN).getLocation();
        return altars.values().stream()
                .filter(altar -> sameBlock(altar.location(), placed)
                        || sameBlock(altar.location(), below)
                        || sameBlock(altar.location().clone().add(0, 1, 0), placed))
                .findFirst();
    }

    public String captureFailureReason(Player player, Block block) {
        if (teamService.findByPlayer(player.getUniqueId()).isEmpty()) {
            return "팀 정보가 없습니다. 게임 시작 후 팀 편성이 완료되었는지 확인하세요.";
        }
        if (block.getRelative(BlockFace.DOWN).getType() != Material.BEACON) {
            return "제단 색유리는 신호기 바로 위에만 설치할 수 있습니다.";
        }
        if (findByPlacedBlock(block).isEmpty()) {
            return "등록된 제단 위치가 아닙니다. /game 제단목록으로 좌표를 확인하세요.";
        }
        return "제단을 점령할 수 없습니다. 관리자에게 제단 등록 상태를 확인해 주세요.";
    }

    public boolean capture(Player player, Block block) {
        Optional<GameTeam> team = teamService.findByPlayer(player.getUniqueId());
        Optional<Altar> altar = findByPlacedBlock(block);
        if (team.isEmpty() || altar.isEmpty()) {
            return false;
        }
        GameTeam owner = team.get();
        Altar target = altar.get();
        String previousOwnerId = target.ownerTeamId();
        if (previousOwnerId != null) {
            teamService.find(previousOwnerId).ifPresent(previous -> previous.capturedAltars().remove(target.id()));
        }
        target.setOwnerTeamId(owner.id());
        owner.capturedAltars().add(target.id());
        owner.resetZeroAltarSeconds();
        removeStrength(owner);
        updateTeamItems(owner);
        refreshBountyStatus(matchStatsService.elapsedSeconds());
        scoreboardService.updateBoard(gamePlayerService.all(), teamService.all());
        displayService.updateAltarDisplay(target, owner);
        matchStatsService.recordAltarCaptured(matchStatsService.elapsedSeconds(), player, target, previousOwnerId, owner);
        broadcastToParticipants(Component.text(owner.color().displayName(), TextColor.fromHexString(owner.color().hex()))
                .append(Component.text(" 팀이 제단 " + target.id() + "을(를) 설치했습니다.")));
        return true;
    }

    public boolean isCapturedAltarBlock(Block block) {
        return findByPlacedBlock(block)
                .map(altar -> altar.ownerTeamId() != null)
                .orElse(false);
    }

    public boolean breakAltarBlock(Player breaker, Block block) {
        Optional<Altar> altar = findByPlacedBlock(block);
        if (altar.isEmpty() || altar.get().ownerTeamId() == null) {
            return false;
        }
        Altar target = altar.get();
        String previousOwnerId = target.ownerTeamId();
        target.setOwnerTeamId(null);
        teamService.find(previousOwnerId).ifPresent(previous -> {
            previous.capturedAltars().remove(target.id());
            if (previous.capturedAltars().isEmpty()) {
                previous.resetZeroAltarSeconds();
            }
            updateTeamItems(previous);
            refreshBountyStatus(matchStatsService.elapsedSeconds());
            broadcastToParticipants(Component.text(previous.color().displayName(), TextColor.fromHexString(previous.color().hex()))
                    .append(Component.text(" 팀의 제단 " + target.id() + "이(가) 제거되었습니다.")));
        });
        teamService.findByPlayer(breaker.getUniqueId()).ifPresent(this::updateTeamItems);
        messageService.send(breaker, MayuelaMessage.ALTAR_BLOCK_BROKEN);
        displayService.showEmptyAltarDisplay(target);
        refreshBountyStatus(matchStatsService.elapsedSeconds());
        scoreboardService.updateBoard(gamePlayerService.all(), teamService.all());
        matchStatsService.recordAltarBroken(matchStatsService.elapsedSeconds(), breaker, target, previousOwnerId);
        return true;
    }

    public void showEmptyAltarDisplays() {
        altars.values().forEach(displayService::showEmptyAltarDisplay);
    }

    public void tickActivation(double baseIncrease, int elapsedSeconds) {
        for (GameTeam team : teamService.all()) {
            if (team.capturedAltars().isEmpty()) {
                team.addZeroAltarSeconds(elapsedSeconds);
                applyStrengthIfNeeded(team);
                continue;
            }
            team.resetZeroAltarSeconds();
            removeStrength(team);
            team.increaseActivation(baseIncrease * elapsedSeconds * team.capturedAltars().size());
            team.addCapturedSeconds(elapsedSeconds);
            updateTeamItems(team);
            for (String altarId : team.capturedAltars()) {
                Altar altar = altars.get(altarId);
                if (altar != null) {
                    displayService.updateAltarDisplay(altar, team);
                }
            }
        }
        long matchElapsedSeconds = matchStatsService.elapsedSeconds();
        refreshBountyStatus(matchElapsedSeconds);
        scoreboardService.updateBoard(gamePlayerService.all(), teamService.all());
        updateBountyTabFooter(matchElapsedSeconds);
    }

    public Optional<GameTeam> findWinner() {
        return teamService.all().stream()
                .filter(team -> team.activationRate() >= 100.0)
                .findFirst();
    }

    public void resetRuntimeState() {
        teamService.all().forEach(this::removeStrength);
        teamService.all().forEach(this::clearBountyGlow);
        lastBountyTransitionSeconds = Long.MIN_VALUE;
        removePlacedAltarBlocks();
        altars.values().forEach(altar -> altar.setOwnerTeamId(null));
        teamService.all().forEach(GameTeam::resetGameState);
    }

    public void releaseTeam(GameTeam team) {
        removeStrength(team);
        clearBountyGlow(team);
        for (Altar altar : altars.values()) {
            if (team.id().equals(altar.ownerTeamId())) {
                Block placedBlock = altar.location().getBlock().getRelative(BlockFace.UP);
                if (isTeamAltarMaterial(placedBlock.getType())) {
                    placedBlock.setType(Material.AIR, false);
                }
                altar.setOwnerTeamId(null);
                displayService.showEmptyAltarDisplay(altar);
            }
        }
        team.resetGameState();
        refreshBountyStatus(matchStatsService.elapsedSeconds());
        scoreboardService.updateBoard(gamePlayerService.all(), teamService.all());
    }

    private void removePlacedAltarBlocks() {
        for (Altar altar : altars.values()) {
            Block placedBlock = altar.location().getBlock().getRelative(BlockFace.UP);
            if (isTeamAltarMaterial(placedBlock.getType())) {
                placedBlock.setType(Material.AIR, false);
            }
        }
    }

    private boolean isTeamAltarMaterial(Material material) {
        return org.minecraft.eden.Eden.mayuela.model.PlayerColor.defaults().stream()
                .anyMatch(color -> color.blockMaterial() == material);
    }

    private void applyStrengthIfNeeded(GameTeam team) {
        if (team.strengthBuffActive() || team.zeroAltarSeconds() < configRepository.strengthDelaySeconds()) {
            return;
        }
        grantStrength(team, true);
        team.setStrengthBuffActive(true);
        matchStatsService.recordStrengthGranted(matchStatsService.elapsedSeconds(), team);
    }

    public void restoreStrengthIfActive(Player player, GameTeam team) {
        if (team.strengthBuffActive()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, PotionEffect.INFINITE_DURATION, configRepository.strengthAmplifier(), false, false));
        }
    }

    public void restoreBountyGlowIfActive(Player player, GameTeam team) {
        player.setGlowing(team.bountyWanted());
        displayService.updateBountyDisplay(player, team, configRepository.bountyRewardCoins());
    }

    private void grantStrength(GameTeam team, boolean notify) {
        for (java.util.UUID member : team.members()) {
            Player player = org.bukkit.Bukkit.getPlayer(member);
            if (player != null) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, PotionEffect.INFINITE_DURATION, configRepository.strengthAmplifier(), false, false));
                if (notify) {
                    messageService.send(player, MayuelaMessage.STRENGTH_GRANTED);
                }
            }
        }
    }

    private void removeStrength(GameTeam team) {
        if (!team.strengthBuffActive()) {
            return;
        }
        for (java.util.UUID member : team.members()) {
            Player player = org.bukkit.Bukkit.getPlayer(member);
            if (player != null) {
                player.removePotionEffect(PotionEffectType.STRENGTH);
            }
        }
        team.setStrengthBuffActive(false);
    }

    private void updateTeamItems(GameTeam team) {
        for (java.util.UUID member : team.members()) {
            Player player = org.bukkit.Bukkit.getPlayer(member);
            if (player != null) {
                inventoryService.updateColorBlock(player, team, count());
            }
        }
    }

    public List<Component> bountyMonitoringLines(long elapsedSeconds) {
        BountyEvaluation evaluation = evaluateBounty(elapsedSeconds);
        java.util.ArrayList<Component> lines = new java.util.ArrayList<>();
        lines.add(Component.text("현상수배 설정: " + (configRepository.bountyEnabled() ? "ON" : "OFF")
                + ", 발동 격차 " + formatPercent(configRepository.bountyActivateGapPercent())
                + ", 해제 격차 " + formatPercent(configRepository.bountyReleaseGapPercent())
                + ", 보상 " + configRepository.bountyRewardCoins() + "코인"));
        lines.add(Component.text("현상수배 상태: " + wantedTeamName()));
        lines.add(Component.text("선두: " + teamName(evaluation.leader()) + ", 추격: " + teamName(evaluation.runnerUp())
                + ", 리드 격차: " + formatPercent(evaluation.leadGap())));
        lines.add(Component.text("발동 가능 여부: " + (evaluation.canActivate() ? "가능" : "불가") + " (" + evaluation.reason() + ")"));
        return lines;
    }

    private void refreshBountyStatus(long elapsedSeconds) {
        BountyEvaluation evaluation = evaluateBounty(elapsedSeconds);
        GameTeam wantedTeam = currentWantedTeam().orElse(null);
        if (wantedTeam != null) {
            if (evaluation.shouldRelease(wantedTeam, configRepository.bountyEnabled(), configRepository.bountyReleaseGapPercent())) {
                setBountyWanted(wantedTeam, false, elapsedSeconds);
                broadcastToParticipants(Component.text("[현상수배 해제] ")
                        .append(Component.text(wantedTeam.color().displayName(), TextColor.fromHexString(wantedTeam.color().hex())))
                        .append(Component.text(" 팀의 현상수배가 해제되었습니다.")));
                scoreboardService.updateBoard(gamePlayerService.all(), teamService.all());
            } else {
                applyBountyGlow(wantedTeam, true);
            }
            return;
        }
        if (!evaluation.canActivate()) {
            clearUnexpectedBounties();
            return;
        }
        GameTeam leader = evaluation.leader();
        setBountyWanted(leader, true, elapsedSeconds);
        broadcastToParticipants(Component.text("[현상수배] ")
                .append(Component.text(leader.color().displayName(), TextColor.fromHexString(leader.color().hex())))
                .append(Component.text(" 팀이 현상수배 상태가 되었습니다. 처치 보상: +"))
                .append(Component.text(configRepository.bountyRewardCoins() + "코인", NamedTextColor.GOLD)));
        scoreboardService.updateBoard(gamePlayerService.all(), teamService.all());
    }

    public void updateBountyTabFooter(long elapsedSeconds) {
        Component footer = bountyFooter(elapsedSeconds);
        for (GamePlayer gamePlayer : gamePlayerService.all()) {
            Player player = org.bukkit.Bukkit.getPlayer(gamePlayer.uuid());
            if (player != null) {
                player.sendPlayerListHeaderAndFooter(Component.empty(), footer);
            }
        }
    }

    public void clearBountyTabFooter() {
        for (GamePlayer gamePlayer : gamePlayerService.all()) {
            Player player = org.bukkit.Bukkit.getPlayer(gamePlayer.uuid());
            if (player != null) {
                player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
            }
        }
    }

    private Component bountyFooter(long elapsedSeconds) {
        BountyEvaluation evaluation = evaluateBounty(elapsedSeconds);
        GameTeam wantedTeam = currentWantedTeam().orElse(null);
        if (wantedTeam != null) {
            return Component.text("현상수배: ", NamedTextColor.RED)
                    .append(Component.text(wantedTeam.color().displayName() + " 팀", TextColor.fromHexString(wantedTeam.color().hex())))
                    .append(Component.text(" +", NamedTextColor.WHITE))
                    .append(Component.text(configRepository.bountyRewardCoins() + "코인", NamedTextColor.GOLD))
                    .append(Component.text(" | 격차: ", NamedTextColor.GRAY))
                    .append(Component.text(gapStatus(evaluation.leadGap()), gapColor(evaluation.leadGap())));
        }
        int remainingSeconds = bountyRemainingSeconds(elapsedSeconds);
        Component footer = Component.text("현상수배: ", NamedTextColor.RED);
        if (remainingSeconds > 0) {
            footer = footer.append(Component.text(remainingSeconds + "초 후 가능", NamedTextColor.YELLOW));
        } else if (evaluation.canActivate()) {
            footer = footer.append(Component.text("발동 가능", NamedTextColor.GOLD));
        } else {
            footer = footer.append(Component.text("대기", NamedTextColor.GRAY));
        }
        return footer
                .append(Component.text(" | 격차: ", NamedTextColor.GRAY))
                .append(Component.text(gapStatus(evaluation.leadGap()), gapColor(evaluation.leadGap())));
    }

    private int bountyRemainingSeconds(long elapsedSeconds) {
        int warmupRemaining = Math.max(0, configRepository.bountyWarmupSeconds() - (int) elapsedSeconds);
        int cooldownRemaining = 0;
        if (lastBountyTransitionSeconds != Long.MIN_VALUE) {
            cooldownRemaining = Math.max(0, configRepository.bountyTransitionCooldownSeconds() - (int) (elapsedSeconds - lastBountyTransitionSeconds));
        }
        return Math.max(warmupRemaining, cooldownRemaining);
    }

    private String gapStatus(double leadGap) {
        if (leadGap >= configRepository.bountyActivateGapPercent()) {
            return "심각";
        }
        if (leadGap >= configRepository.bountyReleaseGapPercent()) {
            return "벌어짐";
        }
        return "안정";
    }

    private NamedTextColor gapColor(double leadGap) {
        if (leadGap >= configRepository.bountyActivateGapPercent()) {
            return NamedTextColor.RED;
        }
        if (leadGap >= configRepository.bountyReleaseGapPercent()) {
            return NamedTextColor.YELLOW;
        }
        return NamedTextColor.GREEN;
    }

    public void releaseBountyAfterDeath(GameTeam team, Player victim, long elapsedSeconds) {
        if (team == null || !team.bountyWanted()) {
            return;
        }
        setBountyWanted(team, false, elapsedSeconds);
        broadcastToParticipants(Component.text("[현상수배 해제] ")
                .append(Component.text(team.color().displayName(), TextColor.fromHexString(team.color().hex())))
                .append(Component.text(" 팀의 "))
                .append(Component.text(victim.getName(), NamedTextColor.YELLOW))
                .append(Component.text("님이 사망하여 현상수배가 해제되었습니다.")));
        scoreboardService.updateBoard(gamePlayerService.all(), teamService.all());
    }

    private BountyEvaluation evaluateBounty(long elapsedSeconds) {
        List<GameTeam> ranking = sortedRanking();
        if (!configRepository.bountyEnabled()) {
            return BountyEvaluation.failed(ranking, "현상수배 기능 OFF");
        }
        if (ranking.size() < 2) {
            return BountyEvaluation.failed(ranking, "팀이 2개 미만");
        }
        GameTeam leader = ranking.get(0);
        GameTeam runnerUp = ranking.get(1);
        double leadGap = Math.max(0.0, leader.activationRate() - runnerUp.activationRate());
        if (elapsedSeconds < configRepository.bountyWarmupSeconds()) {
            return BountyEvaluation.failed(ranking, leadGap, "준비 시간 미충족");
        }
        if (leader.activationRate() < configRepository.bountyMinLeaderActivationPercent()) {
            return BountyEvaluation.failed(ranking, leadGap, "선두 최소 진행률 미충족");
        }
        if (!bountyCooldownReady(elapsedSeconds)) {
            return BountyEvaluation.failed(ranking, leadGap, "상태 변경 쿨다운");
        }
        if (leadGap < configRepository.bountyActivateGapPercent()) {
            return BountyEvaluation.failed(ranking, leadGap, "리드 격차 부족");
        }
        return BountyEvaluation.active(ranking, leadGap);
    }

    private boolean bountyCooldownReady(long elapsedSeconds) {
        if (lastBountyTransitionSeconds == Long.MIN_VALUE) {
            return true;
        }
        return elapsedSeconds - lastBountyTransitionSeconds >= configRepository.bountyTransitionCooldownSeconds();
    }

    private Optional<GameTeam> currentWantedTeam() {
        return teamService.all().stream()
                .filter(GameTeam::bountyWanted)
                .findFirst();
    }

    private void clearUnexpectedBounties() {
        for (GameTeam team : teamService.all()) {
            if (team.bountyWanted()) {
                clearBountyGlow(team);
            } else {
                applyBountyGlow(team, false);
            }
        }
    }

    private void setBountyWanted(GameTeam team, boolean wanted, long elapsedSeconds) {
        team.clearBountyRewardClaims();
        team.setBountyWanted(wanted);
        applyBountyGlow(team, wanted);
        lastBountyTransitionSeconds = elapsedSeconds;
    }

    private List<GameTeam> sortedRanking() {
        java.util.ArrayList<GameTeam> ranking = new java.util.ArrayList<>(teamService.all());
        ranking.sort(Comparator
                .comparingDouble(GameTeam::activationRate).reversed()
                .thenComparing(GameTeam::capturedSeconds, Comparator.reverseOrder())
                .thenComparing(GameTeam::id));
        return ranking;
    }

    private String wantedTeamName() {
        return currentWantedTeam()
                .map(this::teamName)
                .orElse("없음");
    }

    private String teamName(GameTeam team) {
        return team == null ? "없음" : team.color().displayName();
    }

    private String formatPercent(double value) {
        return String.format("%.1f%%", value);
    }

    private void refreshBountyStatus() {
        for (GameTeam team : teamService.all()) {
            boolean shouldBeWanted = false;
            if (team.bountyWanted() == shouldBeWanted) {
                applyBountyGlow(team, shouldBeWanted);
                continue;
            }
            team.clearBountyRewardClaims();
            team.setBountyWanted(shouldBeWanted);
            applyBountyGlow(team, shouldBeWanted);
            if (shouldBeWanted) {
                broadcastToParticipants(Component.text("[현상수배] ")
                        .append(Component.text(team.color().displayName(), TextColor.fromHexString(team.color().hex())))
                        .append(Component.text(" 팀이 제단 " + team.capturedAltars().size() + "개를 점령했습니다.")));
            } else {
                broadcastToParticipants(Component.text("[현상수배 해제] ")
                        .append(Component.text(team.color().displayName(), TextColor.fromHexString(team.color().hex())))
                        .append(Component.text(" 팀의 현상수배가 해제되었습니다.")));
            }
        }
    }

    private void applyBountyGlow(GameTeam team, boolean glowing) {
        for (java.util.UUID member : team.members()) {
            Player player = org.bukkit.Bukkit.getPlayer(member);
            if (player != null) {
                player.setGlowing(glowing);
                displayService.updateBountyDisplay(player, team, configRepository.bountyRewardCoins());
            }
        }
    }

    private void clearBountyGlow(GameTeam team) {
        team.clearBountyRewardClaims();
        team.setBountyWanted(false);
        applyBountyGlow(team, false);
    }

    private record BountyEvaluation(List<GameTeam> ranking, double leadGap, boolean canActivate, String reason) {

        private static BountyEvaluation failed(List<GameTeam> ranking, String reason) {
            return failed(ranking, 0.0, reason);
        }

        private static BountyEvaluation failed(List<GameTeam> ranking, double leadGap, String reason) {
            return new BountyEvaluation(ranking, leadGap, false, reason);
        }

        private static BountyEvaluation active(List<GameTeam> ranking, double leadGap) {
            return new BountyEvaluation(ranking, leadGap, true, "발동 조건 충족");
        }

        private GameTeam leader() {
            return ranking.isEmpty() ? null : ranking.get(0);
        }

        private GameTeam runnerUp() {
            return ranking.size() < 2 ? null : ranking.get(1);
        }

        private boolean shouldRelease(GameTeam wantedTeam, boolean enabled, double releaseGapPercent) {
            if (!enabled || ranking.isEmpty()) {
                return true;
            }
            GameTeam leader = ranking.get(0);
            return !leader.id().equals(wantedTeam.id()) || leadGap <= releaseGapPercent;
        }
    }

    private boolean sameBlock(Location first, Location second) {
        return first.getWorld().equals(second.getWorld())
                && first.getBlockX() == second.getBlockX()
                && first.getBlockY() == second.getBlockY()
                && first.getBlockZ() == second.getBlockZ();
    }

    private void broadcastToParticipants(Component message) {
        for (GamePlayer player : gamePlayerService.all()) {
            Player bukkitPlayer = org.bukkit.Bukkit.getPlayer(player.uuid());
            if (bukkitPlayer != null) {
                messageService.sendComponent(bukkitPlayer, message);
            }
        }
    }
}
