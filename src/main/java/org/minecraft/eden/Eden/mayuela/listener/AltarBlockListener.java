package org.minecraft.eden.Eden.mayuela.listener;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.minecraft.eden.Eden.mayuela.message.MayuelaMessage;
import org.minecraft.eden.Eden.mayuela.message.MessageService;
import org.minecraft.eden.Eden.mayuela.model.GameTeam;
import org.minecraft.eden.Eden.mayuela.model.GamePhase;
import org.minecraft.eden.Eden.mayuela.model.PlayerColor;
import org.minecraft.eden.Eden.mayuela.service.AltarService;
import org.minecraft.eden.Eden.mayuela.service.GamePlayerService;
import org.minecraft.eden.Eden.mayuela.service.GameSessionService;
import org.minecraft.eden.Eden.mayuela.service.InventoryService;
import org.minecraft.eden.Eden.mayuela.service.TeamService;

public final class AltarBlockListener implements Listener {

    private final AltarService altarService;
    private final GameSessionService gameSessionService;
    private final GamePlayerService gamePlayerService;
    private final InventoryService inventoryService;
    private final TeamService teamService;
    private final MessageService messageService;
    private final Set<UUID> pendingInstallPlayers = new HashSet<>();
    private final Set<String> pendingInstallBlocks = new HashSet<>();

    public AltarBlockListener(AltarService altarService, GameSessionService gameSessionService, GamePlayerService gamePlayerService, InventoryService inventoryService, TeamService teamService, MessageService messageService) {
        this.altarService = altarService;
        this.gameSessionService = gameSessionService;
        this.gamePlayerService = gamePlayerService;
        this.inventoryService = inventoryService;
        this.teamService = teamService;
        this.messageService = messageService;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBeaconInteract(PlayerInteractEvent event) {
        if (gameSessionService.phase() != GamePhase.PLAYING || gamePlayerService.find(event.getPlayer().getUniqueId()).isEmpty()) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null || clickedBlock.getType() != Material.BEACON) {
            return;
        }
        event.setCancelled(true);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        if (!hasAltarDye(event.getPlayer(), event.getItem())) {
            messageService.send(event.getPlayer(), MayuelaMessage.BEACON_GUI_BLOCKED);
            return;
        }
        Player player = event.getPlayer();
        String blockKey = blockKey(clickedBlock);
        if (!pendingInstallPlayers.add(player.getUniqueId())) {
            return;
        }
        if (!pendingInstallBlocks.add(blockKey)) {
            pendingInstallPlayers.remove(player.getUniqueId());
            return;
        }
        Bukkit.getScheduler().runTask(JavaPlugin.getProvidingPlugin(getClass()), () -> {
            try {
                installAltarFromBeaconClick(player, clickedBlock);
            } finally {
                pendingInstallPlayers.remove(player.getUniqueId());
                pendingInstallBlocks.remove(blockKey);
            }
        });
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (gameSessionService.phase() == GamePhase.READY && gamePlayerService.find(event.getPlayer().getUniqueId()).isPresent()) {
            event.setCancelled(true);
            messageService.send(event.getPlayer(), MayuelaMessage.READY_BLOCK_PLACE_BLOCKED);
            return;
        }
        if (gameSessionService.phase() == GamePhase.PLAYING && gamePlayerService.find(event.getPlayer().getUniqueId()).isPresent()) {
            event.setCancelled(true);
            messageService.send(event.getPlayer(), MayuelaMessage.PLAYING_DIRECT_BLOCK_PLACE_BLOCKED);
            return;
        }
        if (gameSessionService.phase() != GamePhase.PLAYING || !inventoryService.isColorBlock(event.getItemInHand())) {
            return;
        }
        if (event.getBlockPlaced().getRelative(BlockFace.DOWN).getType() != Material.BEACON) {
            event.setCancelled(true);
            messageService.send(event.getPlayer(), MayuelaMessage.ALTAR_MUST_BE_ABOVE_BEACON);
            return;
        }
        if (!altarService.capture(event.getPlayer(), event.getBlockPlaced())) {
            event.setCancelled(true);
            messageService.send(event.getPlayer(), MayuelaMessage.ALTAR_CAPTURE_FAILED, altarService.captureFailureReason(event.getPlayer(), event.getBlockPlaced()));
        }
    }

    private void installAltarFromBeaconClick(Player player, Block beaconBlock) {
        if (!player.isOnline() || gameSessionService.phase() != GamePhase.PLAYING) {
            return;
        }
        GameTeam team = teamService.findByPlayer(player.getUniqueId()).orElse(null);
        if (team == null) {
            messageService.send(player, MayuelaMessage.ALTAR_CAPTURE_FAILED, altarService.captureFailureReason(player, beaconBlock.getRelative(BlockFace.UP)));
            return;
        }
        Block altarBlock = beaconBlock.getRelative(BlockFace.UP);
        if (!altarBlock.getType().isAir() && !isTeamAltarMaterial(altarBlock.getType())) {
            messageService.send(player, MayuelaMessage.ALTAR_BLOCKED_ABOVE);
            return;
        }
        Material previousMaterial = altarBlock.getType();
        altarBlock.setType(team.color().blockMaterial(), false);
        if (!altarService.capture(player, altarBlock)) {
            altarBlock.setType(previousMaterial, false);
            messageService.send(player, MayuelaMessage.ALTAR_CAPTURE_FAILED, altarService.captureFailureReason(player, altarBlock));
            return;
        }
    }

    private boolean hasAltarDye(Player player, ItemStack eventItem) {
        return inventoryService.isColorBlock(eventItem)
                || inventoryService.isColorBlock(player.getInventory().getItemInMainHand())
                || inventoryService.isColorBlock(player.getInventory().getItem(InventoryService.COLOR_BLOCK_SLOT));
    }

    private boolean isTeamAltarMaterial(Material material) {
        return PlayerColor.defaults().stream().anyMatch(color -> color.blockMaterial() == material);
    }

    private String blockKey(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (gamePlayerService.find(event.getPlayer().getUniqueId()).isEmpty()) {
            return;
        }
        if (gameSessionService.phase() == GamePhase.READY) {
            event.setCancelled(true);
            return;
        }
        if (gameSessionService.phase() != GamePhase.PLAYING) {
            return;
        }
        if (!altarService.isCapturedAltarBlock(event.getBlock())) {
            event.setCancelled(true);
            return;
        }
        event.setDropItems(false);
        event.setExpToDrop(0);
        altarService.breakAltarBlock(event.getPlayer(), event.getBlock());
    }
}
