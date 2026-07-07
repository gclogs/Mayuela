package org.minecraft.eden.Eden.mayuela.service;

import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.minecraft.eden.Eden.mayuela.message.MayuelaMessage;
import org.minecraft.eden.Eden.mayuela.message.MessageService;
import org.minecraft.eden.Eden.mayuela.repository.ConfigRepository;
import org.minecraft.eden.Eden.mayuela.sound.MayuelaSound;

public final class ShopService {

    public static final String SHOP_TITLE = "은행";
    private static final NamespacedKey SHOP_ITEM_KEY = new NamespacedKey("mayuela", "shop_item");
    private static final NamespacedKey SHOP_ITEMSADDER_ID_KEY = new NamespacedKey("mayuela", "shop_itemsadder_id");

    private final ConfigRepository configRepository;
    private final CoinService coinService;
    private final MatchStatsService matchStatsService;
    private final MessageService messageService;

    public ShopService(ConfigRepository configRepository, CoinService coinService, MatchStatsService matchStatsService, MessageService messageService) {
        this.configRepository = configRepository;
        this.coinService = coinService;
        this.matchStatsService = matchStatsService;
        this.messageService = messageService;
    }

    public void open(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 9, Component.text(SHOP_TITLE));
        inventory.setItem(0, shopItem(ShopItem.ARROW));
        inventory.setItem(2, shopItem(ShopItem.KNOCKBACK_STICK));
        inventory.setItem(4, shopItem(ShopItem.JUMP_BOOST));
        inventory.setItem(6, shopItem(ShopItem.KNOCKBACK_RESISTANCE));
        player.openInventory(inventory);
        MayuelaSound.SHOP_OPEN.play(player);
    }

    public boolean isShopItem(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir() || !itemStack.hasItemMeta()) {
            return false;
        }
        return itemStack.getItemMeta().getPersistentDataContainer().has(SHOP_ITEM_KEY, PersistentDataType.STRING);
    }

    public ShopItem readShopItem(ItemStack itemStack) {
        if (!isShopItem(itemStack)) {
            return null;
        }
        String id = itemStack.getItemMeta().getPersistentDataContainer().get(SHOP_ITEM_KEY, PersistentDataType.STRING);
        return ShopItem.fromId(id);
    }

    public void buy(Player player, ShopItem item) {
        int price = price(item);
        if (item == ShopItem.KNOCKBACK_STICK && hasReachedKnockbackStickLimit(player)) {
            int limit = configRepository.knockbackStickMaxHeld();
            matchStatsService.recordShopPurchase(matchStatsService.elapsedSeconds(), player, item, price, false, "LIMIT_REACHED");
            messageService.send(player, MayuelaMessage.SHOP_KNOCKBACK_STICK_LIMIT, limit);
            MayuelaSound.SHOP_PURCHASE_FAILED.play(player);
            return;
        }
        if (!takeCoins(player, price)) {
            matchStatsService.recordShopPurchase(matchStatsService.elapsedSeconds(), player, item, price, false, "NOT_ENOUGH_COINS");
            messageService.send(player, MayuelaMessage.SHOP_NOT_ENOUGH_COINS, price);
            MayuelaSound.SHOP_PURCHASE_FAILED.play(player);
            return;
        }
        player.getInventory().addItem(purchasedItem(item));
        matchStatsService.recordShopPurchase(matchStatsService.elapsedSeconds(), player, item, price, true, "OK");
        messageService.send(player, MayuelaMessage.SHOP_PURCHASED, item.displayName(), price);
        MayuelaSound.SHOP_PURCHASED.play(player);
    }

    public ItemStack purchasedItem(ShopItem item) {
        String itemsAdderId = configRepository.shopItemItemsAdderId(item.id());
        ItemStack itemStack = createConfiguredItem(itemsAdderId, configRepository.shopItemFallbackMaterial(item.id(), item.material()), item.amount());
        ItemMeta meta = itemStack.getItemMeta();
        meta.displayName(Component.text(item.displayName()));
        meta.lore(itemLore(item));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(SHOP_ITEM_KEY, PersistentDataType.STRING, item.id());
        meta.getPersistentDataContainer().set(SHOP_ITEMSADDER_ID_KEY, PersistentDataType.STRING, itemsAdderId);
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    public int price(ShopItem item) {
        return switch (item) {
            case KNOCKBACK_STICK -> configRepository.knockbackStickPrice();
            case JUMP_BOOST -> configRepository.jumpBoostPrice();
            case KNOCKBACK_RESISTANCE -> configRepository.knockbackResistancePrice();
            case ARROW -> configRepository.arrowPrice();
        };
    }

    private ItemStack shopItem(ShopItem item) {
        ItemStack itemStack = purchasedItem(item);
        ItemMeta meta = itemStack.getItemMeta();
        if (item == ShopItem.KNOCKBACK_STICK) {
            meta.lore(List.of(
                    Component.text("§f가격: " + price(item) + " 코인"),
                    Component.text("§f" + item.description()),
                    Component.text("§f보유 한도: " + configRepository.knockbackStickMaxHeld() + "개")
            ));
        } else {
            meta.lore(List.of(
                    Component.text("§f가격: " + price(item) + " 코인"),
                    Component.text("§f" + item.description())
            ));
        }
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    private List<Component> itemLore(ShopItem item) {
        return switch (item) {
            case KNOCKBACK_STICK -> List.of(
                    Component.text("§f타격한 플레이어를 " + configRepository.knockbackStickBlocks() + "블록 밀칩니다."),
                    Component.text("§f보유 한도: " + configRepository.knockbackStickMaxHeld() + "개")
            );
            case JUMP_BOOST -> List.of(Component.text("§f우클릭 시 점프 강화 " + configRepository.jumpBoostSeconds() + "초"));
            case KNOCKBACK_RESISTANCE -> List.of(Component.text("§f우클릭 시 밀치기 저항 " + configRepository.knockbackResistanceSeconds() + "초"));
            case ARROW -> List.of(Component.text("§f구매 시 화살 1개를 지급합니다."));
        };
    }

    private ItemStack createConfiguredItem(String itemsAdderId, Material fallbackMaterial, int amount) {
        ItemStack itemsAdderItem = createItemsAdderItem(itemsAdderId);
        if (itemsAdderItem != null) {
            itemsAdderItem.setAmount(amount);
            return itemsAdderItem;
        }
        return new ItemStack(fallbackMaterial, amount);
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

    private boolean hasReachedKnockbackStickLimit(Player player) {
        return countShopItem(player, ShopItem.KNOCKBACK_STICK) + ShopItem.KNOCKBACK_STICK.amount() > configRepository.knockbackStickMaxHeld();
    }

    private int countShopItem(Player player, ShopItem targetItem) {
        int count = 0;
        for (ItemStack itemStack : player.getInventory().getContents()) {
            if (readShopItem(itemStack) == targetItem) {
                count += itemStack.getAmount();
            }
        }
        return count;
    }

    private boolean takeCoins(Player player, int amount) {
        int remaining = amount;
        for (ItemStack content : player.getInventory().getStorageContents()) {
            if (!coinService.isCoin(content)) {
                continue;
            }
            remaining -= content.getAmount();
            if (remaining <= 0) {
                break;
            }
        }
        if (remaining > 0) {
            return false;
        }
        remaining = amount;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int index = 0; index < contents.length && remaining > 0; index++) {
            ItemStack content = contents[index];
            if (!coinService.isCoin(content)) {
                continue;
            }
            int taken = Math.min(remaining, content.getAmount());
            content.setAmount(content.getAmount() - taken);
            if (content.getAmount() <= 0) {
                contents[index] = null;
            }
            remaining -= taken;
        }
        player.getInventory().setStorageContents(contents);
        return true;
    }

    public enum ShopItem {
        KNOCKBACK_STICK("knockback_stick", "밀치기 막대기", "타격한 상대를 강하게 밀칩니다.", Material.STICK, 1),
        JUMP_BOOST("jump_boost", "점프강화 아이템", "일정 시간 점프 강화 버프를 받습니다.", Material.RABBIT_FOOT, 1),
        KNOCKBACK_RESISTANCE("knockback_resistance", "밀치기 저항 아이템", "일정 시간 밀치기 효과를 무시합니다.", Material.SHIELD, 1),
        ARROW("arrow", "화살", "화살 1개를 구매합니다.", Material.ARROW, 1);

        private final String id;
        private final String displayName;
        private final String description;
        private final Material material;
        private final int amount;

        ShopItem(String id, String displayName, String description, Material material, int amount) {
            this.id = id;
            this.displayName = displayName;
            this.description = description;
            this.material = material;
            this.amount = amount;
        }

        public String id() {
            return id;
        }

        public String displayName() {
            return displayName;
        }

        public String description() {
            return description;
        }

        public Material material() {
            return material;
        }

        public int amount() {
            return amount;
        }

        public static ShopItem fromId(String id) {
            for (ShopItem item : values()) {
                if (item.id.equals(id)) {
                    return item;
                }
            }
            return null;
        }
    }
}
