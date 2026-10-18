package org.minecraft.eden.Eden.mayuela.listener;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.minecraft.eden.Eden.mayuela.message.MayuelaMessage;
import org.minecraft.eden.Eden.mayuela.message.MessageService;
import org.minecraft.eden.Eden.mayuela.model.GamePhase;
import org.minecraft.eden.Eden.mayuela.model.GamePlayer;
import org.minecraft.eden.Eden.mayuela.model.GameTeam;
import org.minecraft.eden.Eden.mayuela.repository.ConfigRepository;
import org.minecraft.eden.Eden.mayuela.service.AltarService;
import org.minecraft.eden.Eden.mayuela.service.CoinService;
import org.minecraft.eden.Eden.mayuela.service.DisplayService;
import org.minecraft.eden.Eden.mayuela.service.GamePlayerService;
import org.minecraft.eden.Eden.mayuela.service.GameSessionService;
import org.minecraft.eden.Eden.mayuela.service.InventoryService;
import org.minecraft.eden.Eden.mayuela.service.MatchStatsService;
import org.minecraft.eden.Eden.mayuela.service.RespawnProtectionService;
import org.minecraft.eden.Eden.mayuela.service.ScoreboardService;
import org.minecraft.eden.Eden.mayuela.service.TeamService;
import org.minecraft.eden.Eden.mayuela.service.KnockbackService;
import org.minecraft.eden.Eden.mayuela.service.GameMusicService;
import org.minecraft.eden.Eden.mayuela.sound.MayuelaSound;

public final class PlayerLifecycleListener implements Listener {

    private static final int FIXED_FOOD_LEVEL = 19;
    private static final int RESPAWN_INVULNERABLE_SECONDS = 5;

    private final ConfigRepository configRepository;
    private final GamePlayerService gamePlayerService;
    private final GameSessionService gameSessionService;
    private final TeamService teamService;
    private final AltarService altarService;
    private final InventoryService inventoryService;
    private final DisplayService displayService;
    private final ScoreboardService scoreboardService;
    private final CoinService coinService;
    private final MatchStatsService matchStatsService;
    private final RespawnProtectionService respawnProtectionService;
    private final MessageService messageService;
    private final KnockbackService knockbackService;
    private final GameMusicService musicService;

    public PlayerLifecycleListener(ConfigRepository configRepository, GamePlayerService gamePlayerService, GameSessionService gameSessionService, TeamService teamService, AltarService altarService, InventoryService inventoryService, DisplayService displayService, ScoreboardService scoreboardService, CoinService coinService, MatchStatsService matchStatsService, RespawnProtectionService respawnProtectionService, MessageService messageService, KnockbackService knockbackService, GameMusicService musicService) {
        this.configRepository = configRepository;
        this.gamePlayerService = gamePlayerService;
        this.gameSessionService = gameSessionService;
        this.teamService = teamService;
        this.altarService = altarService;
        this.inventoryService = inventoryService;
        this.displayService = displayService;
        this.scoreboardService = scoreboardService;
        this.coinService = coinService;
        this.matchStatsService = matchStatsService;
        this.respawnProtectionService = respawnProtectionService;
        this.messageService = messageService;
        this.knockbackService = knockbackService;
        this.musicService = musicService;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        knockbackService.clear(event.getPlayer());
        if (gamePlayerService.find(event.getPlayer().getUniqueId()).isEmpty()) {
            return;
        }
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.getDrops().clear();
        if (gameSessionService.phase() == GamePhase.PLAYING) {
            event.deathMessage(null);
            matchStatsService.recordDeath(matchStatsService.elapsedSeconds(), event.getPlayer(), event.getPlayer().getKiller(), event.getPlayer().getLastDamageCause() == null ? null : event.getPlayer().getLastDamageCause().getCause());
            GameTeam victimTeam = teamService.findByPlayer(event.getPlayer().getUniqueId()).orElse(null);
            // getKiller() can retain an earlier attacker after an unrelated suicide.
            Player bountyKiller = event.getDamageSource().getCausingEntity() instanceof Player player ? player : null;
            GameTeam killerTeam = bountyKiller == null ? null : teamService.findByPlayer(bountyKiller.getUniqueId()).orElse(null);
            if (victimTeam != null && victimTeam.bountyWanted() && killerTeam != null
                    && !killerTeam.id().equals(victimTeam.id())
                    && !bountyKiller.getUniqueId().equals(event.getPlayer().getUniqueId())) {
                grantBountyReward(event.getPlayer(), bountyKiller, victimTeam);
                altarService.releaseBountyAfterDeath(victimTeam, event.getPlayer(), matchStatsService.elapsedSeconds());
            }
            broadcastDeathMessage(event.getPlayer(), event.getPlayer().getKiller());
            MayuelaSound.DEATH.play(event.getPlayer());
            int delaySeconds = Math.max(1, configRepository.respawnDelaySeconds());
            Player player = event.getPlayer();
            messageService.send(player, MayuelaMessage.RESPAWN_FREEZE_START, delaySeconds);
            player.sendTitle("부활 대기", "잠깐 누워있기 " + delaySeconds + "초", 0, 40, 10);
            Bukkit.getScheduler().runTaskLater(JavaPlugin.getProvidingPlugin(getClass()), () -> {
                if (player.isOnline() && player.isDead()) {
                    player.spigot().respawn();
                }
            }, 1L);
        }
    }

    private void broadcastDeathMessage(Player victim, Player killer) {
        Component message = deathActor(victim)
                .append(Component.text("님이 ", NamedTextColor.WHITE));
        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) {
            message = message.append(Component.text("쓰러졌습니다.", NamedTextColor.WHITE));
        } else {
            message = message.append(deathActor(killer))
                    .append(Component.text("님에게 처치되었습니다.", NamedTextColor.WHITE));
        }
        for (GamePlayer participant : gamePlayerService.all()) {
            Player player = Bukkit.getPlayer(participant.uuid());
            if (player != null) {
                messageService.sendComponent(player, message);
            }
        }
    }

    private Component deathActor(Player player) {
        GameTeam team = teamService.findByPlayer(player.getUniqueId()).orElse(null);
        if (team == null) {
            return Component.text("[팀 없음] " + player.getName(), NamedTextColor.GRAY);
        }
        TextColor color = TextColor.fromHexString(team.color().hex());
        if (color == null) {
            color = NamedTextColor.WHITE;
        }
        return Component.text("[", NamedTextColor.DARK_GRAY)
                .append(Component.text(team.color().displayName() + "팀", color))
                .append(Component.text("] ", NamedTextColor.DARK_GRAY))
                .append(Component.text(player.getName(), color));
    }

    private void grantBountyReward(Player victim, Player killer, GameTeam victimTeam) {
        if (victimTeam.bountyRewardClaimed(victim.getUniqueId())) {
            messageService.send(killer, MayuelaMessage.BOUNTY_ALREADY_REWARDED, victim.getName());
            return;
        }
        int rewardCoins = configRepository.bountyRewardCoins();
        if (!coinService.grantCoins(killer, rewardCoins)) {
            messageService.send(killer, MayuelaMessage.BOUNTY_REWARD_INVENTORY_FULL);
            return;
        }
        victimTeam.markBountyRewardClaimed(victim.getUniqueId());
        messageService.send(killer, MayuelaMessage.BOUNTY_REWARD_GRANTED, rewardCoins, victim.getName());
        messageService.send(victim, MayuelaMessage.BOUNTY_REWARD_PAID_TO_VICTIM);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (gamePlayerService.find(player.getUniqueId()).isPresent()) {
            if (configRepository.gameSpawnLocation() != null) {
                event.setRespawnLocation(configRepository.gameSpawnLocation());
            }
        }
        gamePlayerService.find(player.getUniqueId()).ifPresent(gamePlayer ->
                Bukkit.getScheduler().runTaskLater(JavaPlugin.getProvidingPlugin(getClass()), () -> {
                    GameTeam team = teamService.findByPlayer(gamePlayer.uuid()).orElse(null);
                    if (team != null) {
                        inventoryService.ensureColorBlock(player, team, altarService.count());
                        inventoryService.ensureOffhandLock(player);
                        inventoryService.applyGameBuffs(player);
                        applyFixedFoodLevel(player);
                        altarService.restoreStrengthIfActive(player, team);
                        altarService.restoreBountyGlowIfActive(player, team);
                        playRespawnFreeze(player);
                        musicService.playFor(player);
                    }
                }, 1L));
    }

    private void playRespawnFreeze(Player player) {
        int delaySeconds = Math.max(1, configRepository.respawnDelaySeconds());
        respawnProtectionService.freeze(player, delaySeconds);
        showRespawnCountdown(player, delaySeconds);
        Bukkit.getScheduler().runTaskLater(JavaPlugin.getProvidingPlugin(getClass()), () -> {
            if (!player.isOnline() || gameSessionService.phase() != GamePhase.PLAYING) {
                return;
            }
            boolean sleeping = false;
            try {
                sleeping = player.sleep(player.getLocation(), true);
            } catch (IllegalStateException ignored) {
                // Some locations cannot enter the sleeping pose; respawn should continue normally.
            }
            if (sleeping) {
                Bukkit.getScheduler().runTaskLater(JavaPlugin.getProvidingPlugin(getClass()), () -> {
                    if (player.isOnline() && player.isSleeping()) {
                        player.wakeup(false);
                    }
                }, delaySeconds * 20L);
            }
        }, 2L);
        Bukkit.getScheduler().runTaskLater(JavaPlugin.getProvidingPlugin(getClass()), () -> {
            respawnProtectionService.startInvulnerability(player, RESPAWN_INVULNERABLE_SECONDS);
            if (player.isOnline() && player.isSleeping()) {
                player.wakeup(false);
            }
            if (player.isOnline()) {
                messageService.send(player, MayuelaMessage.RESPAWN_CAN_MOVE_NOW);
            }
        }, delaySeconds * 20L);
    }

    private void showRespawnCountdown(Player player, int delaySeconds) {
        for (int secondsLeft = delaySeconds; secondsLeft >= 1; secondsLeft--) {
            int delayTicks = (delaySeconds - secondsLeft) * 20;
            int displaySeconds = secondsLeft;
            Bukkit.getScheduler().runTaskLater(JavaPlugin.getProvidingPlugin(getClass()), () -> {
                if (player.isOnline() && respawnProtectionService.isFrozen(player)) {
                    MayuelaSound.RESPAWN_COUNTDOWN.play(player);
                    player.sendTitle("부활 대기", displaySeconds + "초", 0, 25, 0);
                }
            }, delayTicks);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!respawnProtectionService.isFrozen(event.getPlayer())) {
            return;
        }
        if (event.getTo() != null && hasMoved(event.getFrom(), event.getTo())) {
            event.setTo(event.getFrom());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        GamePlayer gamePlayer = gamePlayerService.find(player.getUniqueId()).orElse(null);
        if (gamePlayer == null || gameSessionService.phase() == GamePhase.WAITING) {
            return;
        }
        matchStatsService.recordConnection(matchStatsService.elapsedSeconds(), player, "PLAYER_JOINED", gameSessionService.phase().name());
        Bukkit.getScheduler().runTaskLater(JavaPlugin.getProvidingPlugin(getClass()), () -> {
            GameTeam team = teamService.findByPlayer(gamePlayer.uuid()).orElse(null);
            if (team == null) {
                return;
            }
            inventoryService.ensureColorBlock(player, team, altarService.count());
            inventoryService.ensureOffhandLock(player);
            inventoryService.applyGameBuffs(player);
            applyFixedFoodLevel(player);
            altarService.restoreStrengthIfActive(player, team);
            altarService.restoreBountyGlowIfActive(player, team);
            if (gameSessionService.phase() == GamePhase.PLAYING) {
                musicService.playFor(player);
                displayService.spawnPlayerDisplay(player, team, configRepository.bountyRewardCoins());
                scoreboardService.updateBoard(gamePlayerService.all(), teamService.all());
            } else {
                displayService.updatePlayerIdentity(player, team, configRepository.bountyRewardCoins());
                scoreboardService.updateBoard(gamePlayerService.all(), teamService);
            }
        }, 1L);
    }

    @EventHandler
    public void onResourcePackLoaded(PlayerResourcePackStatusEvent event) {
        if (event.getStatus() == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) {
            musicService.playFor(event.getPlayer());
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || gamePlayerService.find(player.getUniqueId()).isEmpty()) {
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
            return;
        }
        if (gameSessionService.phase() == GamePhase.PLAYING && respawnProtectionService.isInvulnerable(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player) || gamePlayerService.find(player.getUniqueId()).isEmpty()) {
            return;
        }
        event.setCancelled(true);
        applyFixedFoodLevel(player);
    }

    @EventHandler
    public void onItemDamage(PlayerItemDamageEvent event) {
        if (gamePlayerService.find(event.getPlayer().getUniqueId()).isEmpty()) {
            return;
        }
        if (inventoryService.isArmor(event.getItem())
                || event.getItem().getType() == Material.IRON_SWORD
                || event.getItem().getType() == Material.BOW) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        knockbackService.clear(event.getPlayer());
        musicService.stopFor(event.getPlayer());
        GamePlayer gamePlayer = gamePlayerService.find(event.getPlayer().getUniqueId()).orElse(null);
        if (gamePlayer == null) {
            return;
        }
        displayService.removePlayerDisplay(gamePlayer.uuid());
        respawnProtectionService.clear(event.getPlayer());
        matchStatsService.recordConnection(matchStatsService.elapsedSeconds(), event.getPlayer(), "PLAYER_QUIT", gameSessionService.phase().name());
        if (gameSessionService.phase() == GamePhase.PLAYING) {
            scoreboardService.updateBoard(gamePlayerService.all(), teamService.all());
            broadcastParticipantQuitRecoverable(gamePlayer.name());
            return;
        }
        boolean wasReady = gameSessionService.phase() == GamePhase.READY;
        GameTeam team = teamService.removePlayer(gamePlayer.uuid()).orElse(null);
        gamePlayerService.remove(gamePlayer.uuid());
        displayService.resetPlayerIdentity(event.getPlayer());
        inventoryService.resetAll(java.util.List.of(gamePlayer));
        if (team != null && team.members().isEmpty()) {
            altarService.releaseTeam(team);
        }
        if (wasReady) {
            gameSessionService.cancelReadySession();
            broadcastParticipantQuitRemoved(gamePlayer.name());
            return;
        }
        if (gameSessionService.phase() == GamePhase.PLAYING) {
            scoreboardService.updateBoard(gamePlayerService.all(), teamService.all());
        } else {
            scoreboardService.updateBoard(gamePlayerService.all(), teamService);
        }
        broadcastParticipantQuitRemoved(gamePlayer.name());
    }

    private void broadcastParticipantQuitRecoverable(String playerName) {
        int count = gamePlayerService.all().size();
        for (GamePlayer participant : gamePlayerService.all()) {
            Player player = Bukkit.getPlayer(participant.uuid());
            if (player != null) {
                messageService.send(player, MayuelaMessage.PARTICIPANT_QUIT_RECOVERABLE, playerName, count);
            }
        }
    }

    private void broadcastParticipantQuitRemoved(String playerName) {
        int count = gamePlayerService.all().size();
        for (GamePlayer participant : gamePlayerService.all()) {
            Player player = Bukkit.getPlayer(participant.uuid());
            if (player != null) {
                messageService.send(player, MayuelaMessage.PARTICIPANT_QUIT_REMOVED, playerName, count);
            }
        }
    }

    private void applyFixedFoodLevel(Player player) {
        player.setFoodLevel(FIXED_FOOD_LEVEL);
        player.setSaturation(0.0f);
        player.setExhaustion(0.0f);
    }

    private boolean hasMoved(Location from, Location to) {
        return from.getX() != to.getX()
                || from.getY() != to.getY()
                || from.getZ() != to.getZ();
    }
}
