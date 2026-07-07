package org.minecraft.eden.Eden.mayuela.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.minecraft.eden.Eden.mayuela.message.MayuelaMessage;
import org.minecraft.eden.Eden.mayuela.message.MessageService;
import org.minecraft.eden.Eden.mayuela.model.GamePhase;
import org.minecraft.eden.Eden.mayuela.service.GamePlayerService;
import org.minecraft.eden.Eden.mayuela.service.GameSessionService;
import org.minecraft.eden.Eden.mayuela.service.ShopService;

public final class ShopCommand implements CommandExecutor {

    private final GamePlayerService gamePlayerService;
    private final GameSessionService gameSessionService;
    private final ShopService shopService;
    private final MessageService messageService;

    public ShopCommand(GamePlayerService gamePlayerService, GameSessionService gameSessionService, ShopService shopService, MessageService messageService) {
        this.gamePlayerService = gamePlayerService;
        this.gameSessionService = gameSessionService;
        this.shopService = shopService;
        this.messageService = messageService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            messageService.send(sender, MayuelaMessage.PLAYER_ONLY);
            return true;
        }
        if (gameSessionService.phase() != GamePhase.PLAYING || gamePlayerService.find(player.getUniqueId()).isEmpty()) {
            messageService.send(player, MayuelaMessage.SHOP_UNAVAILABLE);
            return true;
        }
        shopService.open(player);
        return true;
    }
}
