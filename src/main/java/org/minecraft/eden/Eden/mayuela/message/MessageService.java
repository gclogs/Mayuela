package org.minecraft.eden.Eden.mayuela.message;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

public interface MessageService {

    void send(CommandSender sender, MayuelaMessage message, Object... values);

    Component component(MayuelaMessage message, Object... values);

    void sendComponent(CommandSender sender, Component message);

    void reload();
}
