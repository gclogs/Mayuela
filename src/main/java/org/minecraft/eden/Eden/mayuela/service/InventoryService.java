package org.minecraft.eden.Eden.mayuela.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.minecraft.eden.Eden.mayuela.model.GamePlayer;
import org.minecraft.eden.Eden.mayuela.model.GameTeam;
import org.minecraft.eden.Eden.mayuela.repository.ConfigRepository;

public final class InventoryService {

    public static final int COLOR_BLOCK_SLOT = 2;
    public static final int OFFHAND_SLOT = 40;
    private static final NamespacedKey COLOR_BLOCK_KEY = new NamespacedKey("mayuela", "color_block");
    private static final NamespacedKey FLAG_ITEMSADDER_ID_KEY = new NamespacedKey("mayuela", "flag_itemsadder_id");
    private static final NamespacedKey OFFHAND_LOCK_KEY = new NamespacedKey("mayuela", "offhand_lock");
    private static final NamespacedKey OFFHAND_ITEMSADDER_ID_KEY = new NamespacedKey("mayuela", "offhand_itemsadder_id");
    private static final String OFFHAND_LOCK_MODEL_ID = "offhand_lock";
    private final ConfigRepository configRepository;
    private final Map<UUID, InventorySnapshot> snapshots = new HashMap<>();

    public InventoryService(ConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    public void giveStartItems(Player player, GameTeam team, int totalAltars) {
        PlayerInventory inventory = player.getInventory();
        snapshots.putIfAbsent(player.getUniqueId(), InventorySnapshot.capture(inventory));
        inventory.clear();
        inventory.setHelmet(coloredLeatherArmor(Material.LEATHER_HELMET, team));
        inventory.setChestplate(coloredLeatherArmor(Material.LEATHER_CHESTPLATE, team));
        inventory.setLeggings(coloredLeatherArmor(Material.LEATHER_LEGGINGS, team));
        inventory.setBoots(coloredLeatherArmor(Material.LEATHER_BOOTS, team));
        inventory.addItem(new ItemStack(Material.IRON_SWORD));

        inventory.addItem(new ItemStack(Material.BOW));
        inventory.setItemInOffHand(createOffhandLockItem());
        applyGameBuffs(player);
        updateColorBlock(player, team, totalAltars);
    }

    public void applyGameBuffs(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 1, false, false));
        player.setFoodLevel(19);
        player.setSaturation(0.0f);
        player.setExhaustion(0.0f);
    }

    public void updateColorBlock(Player player, GameTeam team, int totalAltars) {
        String itemsAdderId = configRepository.flagItemsAdderId(team.color());
        ItemStack item = createFlagItem(itemsAdderId, configRepository.flagFallbackMaterial(team.color()));
        ItemMeta meta = item.getItemMeta();
        TextColor teamColor = TextColor.fromHexString(team.color().hex());

        meta.displayName(Component.text(team.color().displayName(), teamColor)
                .append(Component.text(" 팀 제단 설치기")));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("깃발을 들고, 신호기를 클릭하면 ", NamedTextColor.WHITE)
                .append(Component.text(team.color().displayName(), teamColor))
                .append(Component.text(" 제단이 설치됩니다.", NamedTextColor.WHITE)));
        lore.add(Component.text(""));
        lore.add(Component.text("§f팀 활성화율: §6" + formatPercent(team.activationRate())));
        lore.add(Component.text("§f팀 점령 제단: §6" + team.capturedAltars().size() + " / " + totalAltars));
        lore.add(Component.text("§f초당 증가량: §6" + formatPercent(team.capturedAltars().size())));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(COLOR_BLOCK_KEY, PersistentDataType.STRING, team.id());
        container.set(FLAG_ITEMSADDER_ID_KEY, PersistentDataType.STRING, itemsAdderId);
        item.setItemMeta(meta);
        player.getInventory().setItem(COLOR_BLOCK_SLOT, item);
    }

    private ItemStack createFlagItem(String itemsAdderId, Material fallbackMaterial) {
        ItemStack itemsAdderItem = createItemsAdderItem(itemsAdderId);
        if (itemsAdderItem != null) {
            return itemsAdderItem;
        }
        return new ItemStack(fallbackMaterial);
    }

    private ItemStack createItemsAdderItem(String itemsAdderId) {
        if (itemsAdderId == null || itemsAdderId.isBlank()) {
            return null;
        }
        try {
            Class<?> customStackClass = Class.forName("dev.lone.itemsadder.api.CustomStack");
            Object customStack = customStackClass.getMethod("getInstance", String.class).invoke(null, itemsAdderId);
            if (customStack == null) {
                return null;
            }
            Object itemStack = customStackClass.getMethod("getItemStack").invoke(customStack);
            return itemStack instanceof ItemStack stack ? stack : null;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    public ItemStack createOffhandLockItem() {
        String itemsAdderId = configRepository.systemItemItemsAdderId(OFFHAND_LOCK_MODEL_ID);
        ItemStack item = createFlagItem(itemsAdderId, configRepository.systemItemFallbackMaterial(OFFHAND_LOCK_MODEL_ID, Material.PAPER));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("§f상점"));
        meta.lore(List.of(Component.text("§fF 키보드를 누르면 상점이 열립니다.")));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(OFFHAND_LOCK_KEY, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(OFFHAND_ITEMSADDER_ID_KEY, PersistentDataType.STRING, itemsAdderId);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isColorBlock(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(COLOR_BLOCK_KEY, PersistentDataType.STRING);
    }

    public boolean isOffhandLockItem(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(OFFHAND_LOCK_KEY, PersistentDataType.BYTE);
    }

    public boolean isArmor(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        String name = item.getType().name();
        return name.endsWith("_HELMET")
                || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS")
                || name.endsWith("_BOOTS");
    }

    public void ensureColorBlock(Player player, GameTeam team, int totalAltars) {
        ItemStack current = player.getInventory().getItem(COLOR_BLOCK_SLOT);
        if (!isColorBlock(current)) {
            updateColorBlock(player, team, totalAltars);
        }
    }

    public void ensureOffhandLock(Player player) {
        if (!isOffhandLockItem(player.getInventory().getItemInOffHand())) {
            player.getInventory().setItemInOffHand(createOffhandLockItem());
        }
    }

    public void restore(Player player) {
        InventorySnapshot snapshot = snapshots.remove(player.getUniqueId());
        if (snapshot == null) {
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            player.getInventory().setItemInOffHand(null);
            return;
        }
        PlayerInventory inventory = player.getInventory();
        inventory.setStorageContents(snapshot.storageContents());
        inventory.setArmorContents(snapshot.armorContents());
        inventory.setItemInOffHand(snapshot.offhand());
    }

    public void restoreAll(Collection<GamePlayer> players) {
        for (GamePlayer gamePlayer : players) {
            Player player = org.bukkit.Bukkit.getPlayer(gamePlayer.uuid());
            if (player != null) {
                restore(player);
            }
        }
    }

    public void resetAll(Collection<GamePlayer> players) {
        for (GamePlayer gamePlayer : players) {
            Player player = org.bukkit.Bukkit.getPlayer(gamePlayer.uuid());
            if (player != null) {
                PlayerInventory inventory = player.getInventory();
                inventory.clear();
                inventory.setArmorContents(null);
                inventory.setItemInOffHand(null);
                player.removePotionEffect(PotionEffectType.SPEED);
                player.removePotionEffect(PotionEffectType.STRENGTH);
            }
            snapshots.remove(gamePlayer.uuid());
        }
    }

    private String formatPercent(double value) {
        return String.format("%.1f%%", value);
    }

    private ItemStack coloredLeatherArmor(Material material, GameTeam team) {
        ItemStack item = new ItemStack(material);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        meta.setColor(colorFromHex(team.color().hex()));
        meta.displayName(Component.text(team.color().displayName() + " 팀 가죽 방어구", TextColor.fromHexString(team.color().hex())));
        item.setItemMeta(meta);
        return item;
    }

    private Color colorFromHex(String hex) {
        int rgb = Integer.parseInt(hex.replace("#", ""), 16);
        return Color.fromRGB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
    }

    private record InventorySnapshot(ItemStack[] storageContents, ItemStack[] armorContents, ItemStack offhand) {

        private static InventorySnapshot capture(PlayerInventory inventory) {
            return new InventorySnapshot(
                    cloneItems(inventory.getStorageContents()),
                    cloneItems(inventory.getArmorContents()),
                    inventory.getItemInOffHand().clone()
            );
        }

        private static ItemStack[] cloneItems(ItemStack[] items) {
            ItemStack[] cloned = new ItemStack[items.length];
            for (int i = 0; i < items.length; i++) {
                cloned[i] = items[i] == null ? null : items[i].clone();
            }
            return cloned;
        }
    }
}
