package org.minecraft.eden.Eden.mayuela.service;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.minecraft.eden.Eden.mayuela.model.Altar;
import org.minecraft.eden.Eden.mayuela.model.GameMap;
import org.minecraft.eden.Eden.mayuela.model.GamePlayer;
import org.minecraft.eden.Eden.mayuela.model.GameTeam;
import org.minecraft.eden.Eden.mayuela.repository.ConfigRepository;
import org.minecraft.eden.Eden.mayuela.service.ShopService.ShopItem;

public final class MatchStatsService {

    private static final DateTimeFormatter MATCH_ID_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneId.systemDefault());
    private static final int MAX_NORMAL_EVENTS = 5000;
    private static final int MAX_LOW_EVENTS = 1000;
    private static final int MAX_SNAPSHOTS = 7200;

    private final JavaPlugin plugin;
    private final ConfigRepository configRepository;
    private final GamePlayerService gamePlayerService;
    private final TeamService teamService;
    private final EnumMap<EventLevel, List<Map<String, Object>>> events = new EnumMap<>(EventLevel.class);
    private final List<Map<String, Object>> snapshots = new ArrayList<>();
    private String matchId;
    private long startedAtMillis;
    private boolean active;
    private int sequence;
    private int droppedNormalEvents;
    private int droppedLowEvents;

    public MatchStatsService(JavaPlugin plugin, ConfigRepository configRepository, GamePlayerService gamePlayerService, TeamService teamService) {
        this.plugin = plugin;
        this.configRepository = configRepository;
        this.gamePlayerService = gamePlayerService;
        this.teamService = teamService;
        for (EventLevel level : EventLevel.values()) {
            events.put(level, new ArrayList<>());
        }
    }

    public boolean isActive() {
        return active;
    }

    public long elapsedSeconds() {
        if (!active || startedAtMillis <= 0L) {
            return 0L;
        }
        return Math.max(0L, (System.currentTimeMillis() - startedAtMillis) / 1000L);
    }

    public void startMatch() {
        reset();
        active = true;
        startedAtMillis = System.currentTimeMillis();
        matchId = MATCH_ID_FORMATTER.format(Instant.ofEpochMilli(startedAtMillis)) + "-" + Long.toHexString(startedAtMillis);
        record(EventLevel.CRITICAL, 0L, "MATCH_STARTED", Map.of(
                "settings", settingsData(),
                "map", mapData(),
                "participants", participantsData(),
                "teams", teamsData(teamService.all())
        ));
        saveCheckpoint();
    }

    public void recordSnapshot(long elapsedSeconds, Collection<GameTeam> teams, int droppedCoinCount) {
        if (!active) {
            return;
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("elapsed-seconds", elapsedSeconds);
        snapshot.put("dropped-coin-count", droppedCoinCount);
        List<Map<String, Object>> teamSnapshots = new ArrayList<>();
        for (GameTeam team : teams) {
            Map<String, Object> teamData = new LinkedHashMap<>();
            teamData.put("team-id", team.id());
            teamData.put("color", team.color().displayName());
            teamData.put("activation-rate", round(team.activationRate()));
            teamData.put("captured-altars", team.capturedAltars().size());
            teamData.put("captured-seconds", team.capturedSeconds());
            teamData.put("zero-altar-seconds", team.zeroAltarSeconds());
            teamData.put("strength-buff-active", team.strengthBuffActive());
            teamSnapshots.add(teamData);
        }
        snapshot.put("teams", teamSnapshots);
        snapshots.add(snapshot);
        if (snapshots.size() > MAX_SNAPSHOTS) {
            snapshots.remove(0);
        }
    }

    public void finishMatch(GameTeam winner, long elapsedSeconds) {
        if (!active) {
            return;
        }
        record(EventLevel.CRITICAL, elapsedSeconds, "MATCH_FINISHED", Map.of(
                "reason", "WINNER_REACHED_100",
                "winner", teamData(winner),
                "final-teams", teamsData(teamService.all())
        ));
        save(elapsedSeconds, "WINNER_REACHED_100", winner);
    }

    public void forceFinish(long elapsedSeconds, String reason) {
        if (!active) {
            return;
        }
        record(EventLevel.CRITICAL, elapsedSeconds, "MATCH_FINISHED", Map.of(
                "reason", reason,
                "winner", "none",
                "final-teams", teamsData(teamService.all())
        ));
        save(elapsedSeconds, reason, null);
    }

    public void recordAltarCaptured(long elapsedSeconds, Player player, Altar altar, String previousTeamId, GameTeam newOwner) {
        if (!active || isStatsExcluded(player)) {
            return;
        }
        EventLevel level = previousTeamId == null || previousTeamId.equals(newOwner.id()) ? EventLevel.NORMAL : EventLevel.HIGH;
        String type = previousTeamId == null ? "ALTAR_CAPTURED" : previousTeamId.equals(newOwner.id()) ? "ALTAR_REFRESHED" : "ALTAR_TAKEN";
        record(level, elapsedSeconds, type, Map.of(
                "player", playerData(player),
                "altar", altarData(altar),
                "previous-team-id", previousTeamId == null ? "none" : previousTeamId,
                "new-team", teamData(newOwner)
        ));
    }

    public void recordAltarBroken(long elapsedSeconds, Player player, Altar altar, String previousTeamId) {
        if (!active || isStatsExcluded(player)) {
            return;
        }
        record(EventLevel.HIGH, elapsedSeconds, "ALTAR_BROKEN", Map.of(
                "player", playerData(player),
                "altar", altarData(altar),
                "previous-team-id", previousTeamId == null ? "none" : previousTeamId
        ));
    }

    public void recordCoinDropped(long elapsedSeconds, Item item, int amount, Location location) {
        recordCoinDropped(elapsedSeconds, item, amount, location, null, 0, 0.0);
    }

    public void recordCoinDropped(long elapsedSeconds, Item item, int amount, Location location, Player anchor, int candidateCount, double distanceFromAnchor) {
        if (!active) {
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("entity-uuid", item.getUniqueId().toString());
        data.put("amount", amount);
        data.put("location", locationData(location));
        data.put("chunk", chunkData(location));
        data.put("anchor-player", anchor == null ? "none" : playerData(anchor));
        data.put("anchor-team-id", anchor == null ? "none" : teamId(anchor));
        data.put("candidate-count", candidateCount);
        data.put("distance-from-anchor", round(distanceFromAnchor));
        record(EventLevel.NORMAL, elapsedSeconds, "COIN_DROPPED", data);
    }

    public void recordCoinPickedUp(long elapsedSeconds, Player player, Item item, int amount) {
        if (!active || isStatsExcluded(player)) {
            return;
        }
        record(EventLevel.HIGH, elapsedSeconds, "COIN_PICKED_UP", Map.of(
                "player", playerData(player),
                "team-id", teamId(player),
                "entity-uuid", item.getUniqueId().toString(),
                "amount", amount,
                "location", locationData(item.getLocation()),
                "chunk", chunkData(item.getLocation())
        ));
    }

    public void recordCoinExpired(long elapsedSeconds, UUID entityId) {
        if (!active) {
            return;
        }
        record(EventLevel.NORMAL, elapsedSeconds, "COIN_EXPIRED", Map.of(
                "entity-uuid", entityId.toString()
        ));
    }

    public void recordShopPurchase(long elapsedSeconds, Player player, ShopItem item, int price, boolean success, String reason) {
        if (!active || isStatsExcluded(player)) {
            return;
        }
        record(EventLevel.HIGH, elapsedSeconds, success ? "SHOP_PURCHASED" : "SHOP_PURCHASE_FAILED", Map.of(
                "player", playerData(player),
                "team-id", teamId(player),
                "item-id", item.id(),
                "item-name", item.displayName(),
                "price", price,
                "success", success,
                "reason", reason
        ));
    }

    public void recordShopItemUsed(long elapsedSeconds, Player player, ShopItem item) {
        if (!active || isStatsExcluded(player)) {
            return;
        }
        record(EventLevel.NORMAL, elapsedSeconds, "SHOP_ITEM_USED", Map.of(
                "player", playerData(player),
                "team-id", teamId(player),
                "item-id", item.id(),
                "item-name", item.displayName()
        ));
    }

    public void recordKnockbackHit(long elapsedSeconds, Player attacker, Player victim, boolean resisted) {
        if (!active || isStatsExcluded(attacker) || isStatsExcluded(victim)) {
            return;
        }
        record(EventLevel.NORMAL, elapsedSeconds, resisted ? "KNOCKBACK_RESISTED" : "KNOCKBACK_HIT", Map.of(
                "attacker", playerData(attacker),
                "attacker-team-id", teamId(attacker),
                "victim", playerData(victim),
                "victim-team-id", teamId(victim)
        ));
    }

    public void recordDeath(long elapsedSeconds, Player victim, Player killer, EntityDamageEvent.DamageCause cause) {
        if (!active || isStatsExcluded(victim) || (killer != null && isStatsExcluded(killer))) {
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("victim", playerData(victim));
        data.put("victim-team-id", teamId(victim));
        data.put("killer", killer == null ? "none" : playerData(killer));
        data.put("killer-team-id", killer == null ? "none" : teamId(killer));
        data.put("cause", cause == null ? "unknown" : cause.name());
        record(EventLevel.HIGH, elapsedSeconds, "PLAYER_DEATH", data);
    }

    public void recordConnection(long elapsedSeconds, Player player, String type, String phase) {
        if (!active || isStatsExcluded(player)) {
            return;
        }
        record(EventLevel.LOW, elapsedSeconds, type, Map.of(
                "player", playerData(player),
                "team-id", teamId(player),
                "phase", phase
        ));
    }

    public void recordStrengthGranted(long elapsedSeconds, GameTeam team) {
        if (!active) {
            return;
        }
        record(EventLevel.NORMAL, elapsedSeconds, "STRENGTH_GRANTED", Map.of(
                "team", teamData(team)
        ));
    }

    private void record(EventLevel level, long elapsedSeconds, String type, Map<String, Object> data) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("seq", ++sequence);
        event.put("level", level.name());
        event.put("elapsed-seconds", elapsedSeconds);
        event.put("type", type);
        event.put("data", data);
        List<Map<String, Object>> bucket = events.get(level);
        bucket.add(event);
        if (level == EventLevel.NORMAL && bucket.size() > MAX_NORMAL_EVENTS) {
            bucket.remove(0);
            droppedNormalEvents++;
        }
        if (level == EventLevel.LOW && bucket.size() > MAX_LOW_EVENTS) {
            bucket.remove(0);
            droppedLowEvents++;
        }
    }

    private void save(long elapsedSeconds, String reason, GameTeam winner) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("match.id", matchId);
        config.set("match.status", "FINISHED");
        config.set("match.started-at-millis", startedAtMillis);
        config.set("match.ended-at-millis", System.currentTimeMillis());
        config.set("match.duration-seconds", elapsedSeconds);
        config.set("match.end-reason", reason);
        config.set("match.winner", winner == null ? "none" : teamData(winner));
        config.set("match.settings", settingsData());
        config.set("match.map", mapData());
        config.set("match.participants", participantsData());
        config.set("match.final-teams", teamsData(teamService.all()));
        config.set("stats.dropped-normal-events", droppedNormalEvents);
        config.set("stats.dropped-low-events", droppedLowEvents);
        config.set("snapshots", snapshots);
        config.set("events", orderedEvents());

        File directory = new File(plugin.getDataFolder(), "stats/matches");
        if (!directory.exists()) {
            directory.mkdirs();
        }
        File file = new File(directory, matchId + ".yml");
        try {
            config.save(file);
            plugin.getLogger().info("Mayuela match stats saved: " + file.getPath());
        } catch (IOException exception) {
            plugin.getLogger().severe("Mayuela match stats save failed: " + exception.getMessage());
        } finally {
            active = false;
        }
    }

    private void saveCheckpoint() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("match.id", matchId);
        config.set("match.status", "IN_PROGRESS");
        config.set("match.started-at-millis", startedAtMillis);
        config.set("match.duration-seconds", elapsedSeconds());
        config.set("match.settings", settingsData());
        config.set("match.map", mapData());
        config.set("match.participants", participantsData());
        config.set("match.initial-teams", teamsData(teamService.all()));
        config.set("events", orderedEvents());
        File directory = new File(plugin.getDataFolder(), "stats/matches");
        if (!directory.exists()) {
            directory.mkdirs();
        }
        File file = new File(directory, matchId + ".yml");
        try {
            config.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("Mayuela match stats checkpoint save failed: " + exception.getMessage());
        }
    }

    private List<Map<String, Object>> orderedEvents() {
        List<Map<String, Object>> ordered = new ArrayList<>();
        ordered.addAll(events.get(EventLevel.CRITICAL));
        ordered.addAll(events.get(EventLevel.HIGH));
        ordered.addAll(events.get(EventLevel.NORMAL));
        ordered.addAll(events.get(EventLevel.LOW));
        return ordered;
    }

    private Map<String, Object> settingsData() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("altar.activation-tick-seconds", configRepository.activationTickSeconds());
        data.put("altar.base-increase-per-altar", configRepository.baseIncreasePerAltar());
        data.put("coin.max-dropped-items", configRepository.maxDroppedCoins());
        data.put("coin.despawn-seconds", configRepository.coinDespawnSeconds());
        data.put("coin.search-radius-blocks", configRepository.coinSearchRadiusBlocks());
        int activeCoinRulePlayers = ConfigRepository.normalizedCoinRulePlayerCount(gamePlayerService.all().size());
        data.put("coin.active-rule-player-count", activeCoinRulePlayers);
        data.put("coin.active-rule.interval-seconds", configRepository.coinDropIntervalSeconds(activeCoinRulePlayers));
        data.put("coin.active-rule.amount", configRepository.coinDropAmount(activeCoinRulePlayers));
        data.put("coin.rules", coinRulesData());
        data.put("bounty.enabled", configRepository.bountyEnabled());
        data.put("bounty.warmup-seconds", configRepository.bountyWarmupSeconds());
        data.put("bounty.min-leader-activation-percent", configRepository.bountyMinLeaderActivationPercent());
        data.put("bounty.activate-gap-percent", configRepository.bountyActivateGapPercent());
        data.put("bounty.release-gap-percent", configRepository.bountyReleaseGapPercent());
        data.put("bounty.transition-cooldown-seconds", configRepository.bountyTransitionCooldownSeconds());
        data.put("bounty.reward-coins", configRepository.bountyRewardCoins());
        data.put("stats.excluded-player-names", configRepository.statsExcludedPlayerNames());
        data.put("shop.arrow-price", configRepository.arrowPrice());
        data.put("shop.knockback-stick-price", configRepository.knockbackStickPrice());
        data.put("shop.knockback-stick-max-held", configRepository.knockbackStickMaxHeld());
        data.put("shop.jump-boost-price", configRepository.jumpBoostPrice());
        data.put("shop.knockback-resistance-price", configRepository.knockbackResistancePrice());
        data.put("item.knockback-stick-blocks", configRepository.knockbackStickBlocks());
        return data;
    }

    private Map<String, Object> mapData() {
        GameMap map = configRepository.firstMap().orElse(null);
        if (map == null) {
            return Map.of("id", "none");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", map.id());
        data.put("position1", locationData(map.position1()));
        data.put("position2", locationData(map.position2()));
        return data;
    }

    private List<Map<String, Object>> participantsData() {
        List<Map<String, Object>> participants = new ArrayList<>();
        for (GamePlayer player : gamePlayerService.all()) {
            if (isStatsExcludedPlayerName(player.name(), configRepository.statsExcludedPlayerNames())) {
                continue;
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("uuid", player.uuid().toString());
            data.put("name", player.name());
            data.put("team-id", teamService.findByPlayer(player.uuid()).map(GameTeam::id).orElse("none"));
            participants.add(data);
        }
        return participants;
    }

    private List<Map<String, Object>> teamsData(Collection<GameTeam> teams) {
        List<Map<String, Object>> data = new ArrayList<>();
        for (GameTeam team : teams) {
            data.add(teamData(team));
        }
        return data;
    }

    private Map<String, Object> teamData(GameTeam team) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("team-id", team.id());
        data.put("color", team.color().displayName());
        data.put("hex", team.color().hex());
        data.put("activation-rate", round(team.activationRate()));
        data.put("captured-altars", new ArrayList<>(team.capturedAltars()));
        data.put("captured-altar-count", team.capturedAltars().size());
        data.put("captured-seconds", team.capturedSeconds());
        List<Map<String, Object>> members = new ArrayList<>();
        for (UUID member : team.members()) {
            GamePlayer player = gamePlayerService.find(member).orElse(null);
            if (player != null && isStatsExcludedPlayerName(player.name(), configRepository.statsExcludedPlayerNames())) {
                continue;
            }
            Map<String, Object> memberData = new LinkedHashMap<>();
            memberData.put("uuid", member.toString());
            memberData.put("name", player == null ? "unknown" : player.name());
            memberData.put("coins", player == null ? 0 : player.coins());
            members.add(memberData);
        }
        data.put("members", members);
        return data;
    }

    private Map<String, Object> altarData(Altar altar) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", altar.id());
        data.put("location", locationData(altar.location()));
        data.put("owner-team-id", altar.ownerTeamId() == null ? "none" : altar.ownerTeamId());
        return data;
    }

    private Map<String, Object> playerData(Player player) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("uuid", player.getUniqueId().toString());
        data.put("name", player.getName());
        data.put("location", locationData(player.getLocation()));
        return data;
    }

    private Map<String, Object> locationData(Location location) {
        if (location == null || location.getWorld() == null) {
            return Map.of("world", "none");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("world", location.getWorld().getName());
        data.put("x", round(location.getX()));
        data.put("y", round(location.getY()));
        data.put("z", round(location.getZ()));
        data.put("block-x", location.getBlockX());
        data.put("block-y", location.getBlockY());
        data.put("block-z", location.getBlockZ());
        return data;
    }

    private Map<String, Object> chunkData(Location location) {
        if (location == null || location.getWorld() == null) {
            return Map.of("world", "none");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("world", location.getWorld().getName());
        data.put("chunk-x", location.getBlockX() >> 4);
        data.put("chunk-z", location.getBlockZ() >> 4);
        return data;
    }

    private String teamId(Player player) {
        return teamService.findByPlayer(player.getUniqueId()).map(GameTeam::id).orElse("none");
    }

    private List<Map<String, Object>> coinRulesData() {
        List<Map<String, Object>> rules = new ArrayList<>();
        for (int playerCount = 1; playerCount <= 10; playerCount++) {
            Map<String, Object> rule = new LinkedHashMap<>();
            rule.put("players", playerCount);
            rule.put("interval-seconds", configRepository.coinDropIntervalSeconds(playerCount));
            rule.put("amount", configRepository.coinDropAmount(playerCount));
            rules.add(rule);
        }
        return rules;
    }

    private boolean isStatsExcluded(Player player) {
        return isStatsExcludedPlayerName(player.getName(), configRepository.statsExcludedPlayerNames());
    }

    public static boolean isStatsExcludedPlayerName(String playerName, Collection<String> excludedNames) {
        if (playerName == null || excludedNames == null) {
            return false;
        }
        for (String excludedName : excludedNames) {
            if (excludedName != null && playerName.equalsIgnoreCase(excludedName.trim())) {
                return true;
            }
        }
        return false;
    }

    private double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private void reset() {
        active = false;
        matchId = null;
        startedAtMillis = 0L;
        sequence = 0;
        droppedNormalEvents = 0;
        droppedLowEvents = 0;
        snapshots.clear();
        for (List<Map<String, Object>> bucket : events.values()) {
            bucket.clear();
        }
    }

    private enum EventLevel {
        CRITICAL,
        HIGH,
        NORMAL,
        LOW
    }
}
