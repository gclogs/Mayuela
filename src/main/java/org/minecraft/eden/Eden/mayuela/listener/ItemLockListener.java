package org.minecraft.eden.Eden.mayuela.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.minecraft.eden.Eden.mayuela.model.GamePhase;
import org.minecraft.eden.Eden.mayuela.service.CoinService;
import org.minecraft.eden.Eden.mayuela.service.GamePlayerService;
import org.minecraft.eden.Eden.mayuela.service.GameSessionService;
import org.minecraft.eden.Eden.mayuela.service.InventoryService;
import org.minecraft.eden.Eden.mayuela.service.ShopService;

public final class ItemLockListener implements Listener {

    private final InventoryService inventoryService;
    private final GamePlayerService gamePlayerService;
    private final GameSessionService gameSessionService;
    private final CoinService coinService;
    private final ShopService shopService;

    public ItemLockListener(InventoryService inventoryService, GamePlayerService gamePlayerService, GameSessionService gameSessionService, CoinService coinService, ShopService shopService) {
        this.inventoryService = inventoryService;
        this.gamePlayerService = gamePlayerService;
        this.gameSessionService = gameSessionService;
        this.coinService = coinService;
        this.shopService = shopService;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || gamePlayerService.find(player.getUniqueId()).isEmpty()) {
            return;
        }
        if (event.getSlot() == InventoryService.COLOR_BLOCK_SLOT
                || event.getHotbarButton() == InventoryService.COLOR_BLOCK_SLOT
                || event.getSlot() == InventoryService.OFFHAND_SLOT
                || event.getSlotType() == InventoryType.SlotType.ARMOR
                || inventoryService.isArmor(event.getCurrentItem())
                || inventoryService.isArmor(event.getCursor())
                || inventoryService.isColorBlock(event.getCurrentItem())
                || inventoryService.isColorBlock(event.getCursor())
                || inventoryService.isOffhandLockItem(event.getCurrentItem())
                || inventoryService.isOffhandLockItem(event.getCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || gamePlayerService.find(player.getUniqueId()).isEmpty()) {
            return;
        }
        if (event.getRawSlots().contains(InventoryService.COLOR_BLOCK_SLOT) || event.getRawSlots().contains(InventoryService.OFFHAND_SLOT)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (gamePlayerService.find(event.getPlayer().getUniqueId()).isPresent()
                && (gameSessionService.phase() == GamePhase.READY || gameSessionService.phase() == GamePhase.PLAYING)) {
            event.setCancelled(true);
            return;
        }
        if (inventoryService.isColorBlock(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            return;
        }
        if (inventoryService.isOffhandLockItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            return;
        }
        if (coinService.isCoin(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("코인은 버릴 수 없습니다.");
        }
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (gamePlayerService.find(event.getPlayer().getUniqueId()).isEmpty()) {
            return;
        }
        event.setCancelled(true);
        if (gameSessionService.phase() == GamePhase.PLAYING) {
            shopService.open(event.getPlayer());
        }
    }
}
