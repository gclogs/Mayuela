package org.minecraft.eden.Eden.mayuela.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.minecraft.eden.Eden.mayuela.message.MayuelaMessage;
import org.minecraft.eden.Eden.mayuela.message.MessageService;
import org.minecraft.eden.Eden.mayuela.model.GamePhase;
import org.minecraft.eden.Eden.mayuela.model.GamePlayer;
import org.minecraft.eden.Eden.mayuela.service.GamePlayerService;
import org.minecraft.eden.Eden.mayuela.service.GameSessionService;
import org.minecraft.eden.Eden.mayuela.service.ScoreboardService;
import org.minecraft.eden.Eden.mayuela.service.TeamService;

public final class LeaveCommand implements CommandExecutor {

    private final GamePlayerService gamePlayerService;
    private final TeamService teamService;
    private final ScoreboardService scoreboardService;
    private final GameSessionService gameSessionService;
    private final MessageService messageService;

    public LeaveCommand(GamePlayerService gamePlayerService, TeamService teamService, ScoreboardService scoreboardService, GameSessionService gameSessionService, MessageService messageService) {
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
        if (gameSessionService.phase() != GamePhase.WAITING) {
            messageService.send(player, MayuelaMessage.LEAVE_UNAVAILABLE_WHILE_PLAYING);
            return true;
        }
        GamePlayer gamePlayer = gamePlayerService.find(player.getUniqueId()).orElse(null);
        if (gamePlayer == null) {
            messageService.send(player, MayuelaMessage.LEAVE_NOT_PARTICIPATING);
            return true;
        }
        teamService.clearManualColor(player.getUniqueId());
        gamePlayerService.remove(player.getUniqueId());
        scoreboardService.clearPlayer(player);
        scoreboardService.updateBoard(gamePlayerService.all(), teamService);
        broadcastParticipantCount(gamePlayer.name());
        messageService.send(player, MayuelaMessage.LEAVE_READY_CANCELLED, gamePlayerService.all().size());
        return true;
    }

    private void broadcastParticipantCount(String playerName) {
        int count = gamePlayerService.all().size();
        for (GamePlayer participant : gamePlayerService.all()) {
            Player target = Bukkit.getPlayer(participant.uuid());
            if (target != null) {
                messageService.send(target, MayuelaMessage.PARTICIPANT_LEFT_READY, playerName, count);
            }
        }
    }
}
