package org.minecraft.eden.Eden.mayuela.listener;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.minecraft.eden.Eden.mayuela.service.ShopService;

public final class ShopListener implements Listener {

    private final ShopService shopService;

    public ShopListener(ShopService shopService) {
        this.shopService = shopService;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !event.getView().title().equals(Component.text(ShopService.SHOP_TITLE))) {
            return;
        }
        event.setCancelled(true);
        ShopService.ShopItem item = shopService.readShopItem(event.getCurrentItem());
        if (item == null) {
            return;
        }
        shopService.buy(player, item);
    }
}
