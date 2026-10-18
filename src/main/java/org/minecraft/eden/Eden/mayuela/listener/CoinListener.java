package org.minecraft.eden.Eden.mayuela.listener;

import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.minecraft.eden.Eden.mayuela.service.CoinService;

public final class CoinListener implements Listener {

    private final CoinService coinService;

    public CoinListener(CoinService coinService) {
        this.coinService = coinService;
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Item item = event.getItem();
        if (!coinService.isCoin(item.getItemStack())) {
            return;
        }
        event.setCancelled(true);
        if (!coinService.isTrackedFieldCoin(item)) {
            return;
        }
        coinService.pickup(player, item);
    }
}
