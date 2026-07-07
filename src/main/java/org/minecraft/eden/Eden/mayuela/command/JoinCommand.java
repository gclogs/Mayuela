package org.minecraft.eden.Eden.mayuela.command;

import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.minecraft.eden.Eden.mayuela.message.MayuelaMessage;
import org.minecraft.eden.Eden.mayuela.message.MessageService;
import org.minecraft.eden.Eden.mayuela.model.GamePhase;
import org.minecraft.eden.Eden.mayuela.model.GamePlayer;
import org.minecraft.eden.Eden.mayuela.repository.ConfigRepository;
import org.minecraft.eden.Eden.mayuela.service.GamePlayerService;
import org.minecraft.eden.Eden.mayuela.service.GameSessionService;
import org.minecraft.eden.Eden.mayuela.service.ScoreboardService;
import org.minecraft.eden.Eden.mayuela.service.TeamService;

public final class JoinCommand implements CommandExecutor, TabCompleter {

    private final ConfigRepository configRepository;
    private final GamePlayerService gamePlayerService;
    private final TeamService teamService;
    private final ScoreboardService scoreboardService;
    private final GameSessionService gameSessionService;
    private final MessageService messageService;

    public JoinCommand(ConfigRepository configRepository, GamePlayerService gamePlayerService, TeamService teamService, ScoreboardService scoreboardService, GameSessionService gameSessionService, MessageService messageService) {
        this.configRepository = configRepository;
        this.gamePlayerService = gamePlayerService;
        this.teamService = teamService;
        this.scoreboardService = scoreboardService;
        this.gameSessionService = gameSessionService;
        this.messageService = messageService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            messageService.send(sender, MayuelaMessage.PLAYER_ONLY);
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("로비등록")) {
            if (!player.hasPermission("mayuela.admin")) {
                messageService.send(player, MayuelaMessage.NO_PERMISSION);
                return true;
            }
            configRepository.saveLobby(player.getLocation());
            messageService.send(player, MayuelaMessage.LOBBY_REGISTERED);
            return true;
        }
        if (gameSessionService.phase() != GamePhase.WAITING) {
            messageService.send(player, MayuelaMessage.JOIN_UNAVAILABLE_NOW);
            return true;
        }
        if (gamePlayerService.find(player.getUniqueId()).isPresent()) {
            messageService.send(player, MayuelaMessage.ALREADY_JOINED, gamePlayerService.all().size());
            return true;
        }
        gamePlayerService.join(player, configRepository.maxPlayers()).ifPresentOrElse(gamePlayer -> {
            scoreboardService.updateBoard(gamePlayerService.all(), teamService);
            messageService.send(player, MayuelaMessage.JOIN_READY);
            broadcastParticipantCount(gamePlayer.name());
        }, () -> messageService.send(player, MayuelaMessage.JOIN_FULL));
        return true;
    }

    private void broadcastParticipantCount(String joinedPlayerName) {
        int count = gamePlayerService.all().size();
        int maxPlayers = configRepository.maxPlayers();
        for (GamePlayer participant : gamePlayerService.all()) {
            Player target = Bukkit.getPlayer(participant.uuid());
            if (target != null) {
                messageService.send(target, MayuelaMessage.PARTICIPANT_JOINED, joinedPlayerName, count, maxPlayers);
            }
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("로비등록");
        }
        return List.of();
    }
}
