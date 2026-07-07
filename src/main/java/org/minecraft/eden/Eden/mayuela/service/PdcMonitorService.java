package org.minecraft.eden.Eden.mayuela.service;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class PdcMonitorService {

    public List<Component> describeHands(PlayerInventory inventory) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.text("메인핸드"));
        lines.addAll(describeItem(inventory.getItemInMainHand()));
        lines.add(Component.text("보조손"));
        lines.addAll(describeItem(inventory.getItemInOffHand()));
        return lines;
    }

    public List<Component> describeInventory(PlayerInventory inventory) {
        List<Component> lines = new ArrayList<>();
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack itemStack = contents[slot];
            if (!hasMayuelaPdc(itemStack)) {
                continue;
            }
            lines.add(Component.text("슬롯 " + slot + " - " + itemSummary(itemStack)));
            lines.addAll(describePdc(itemStack.getItemMeta().getPersistentDataContainer()));
        }
        if (lines.isEmpty()) {
            lines.add(Component.text("Mayuela PDC 태그가 있는 아이템이 없습니다."));
        }
        return lines;
    }

    private List<Component> describeItem(ItemStack itemStack) {
        List<Component> lines = new ArrayList<>();
        if (itemStack == null || itemStack.getType().isAir()) {
            lines.add(Component.text("- 아이템 없음"));
            return lines;
        }
        lines.add(Component.text("- " + itemSummary(itemStack)));
        if (!itemStack.hasItemMeta() || itemStack.getItemMeta().getPersistentDataContainer().getKeys().isEmpty()) {
            lines.add(Component.text("- PDC 태그 없음"));
            return lines;
        }
        lines.addAll(describePdc(itemStack.getItemMeta().getPersistentDataContainer()));
        return lines;
    }

    private List<Component> describePdc(PersistentDataContainer container) {
        List<Component> lines = new ArrayList<>();
        for (NamespacedKey key : container.getKeys()) {
            String value = readValue(container, key);
            lines.add(Component.text("- " + key + " = " + value));
        }
        return lines;
    }

    private boolean hasMayuelaPdc(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir() || !itemStack.hasItemMeta()) {
            return false;
        }
        return itemStack.getItemMeta().getPersistentDataContainer().getKeys().stream()
                .anyMatch(key -> key.getNamespace().equals("mayuela"));
    }

    private String itemSummary(ItemStack itemStack) {
        return itemStack.getType().name() + " x" + itemStack.getAmount();
    }

    private String readValue(PersistentDataContainer container, NamespacedKey key) {
        if (container.has(key, PersistentDataType.STRING)) {
            return container.get(key, PersistentDataType.STRING);
        }
        if (container.has(key, PersistentDataType.INTEGER)) {
            return String.valueOf(container.get(key, PersistentDataType.INTEGER));
        }
        if (container.has(key, PersistentDataType.BYTE)) {
            return String.valueOf(container.get(key, PersistentDataType.BYTE));
        }
        if (container.has(key, PersistentDataType.LONG)) {
            return String.valueOf(container.get(key, PersistentDataType.LONG));
        }
        if (container.has(key, PersistentDataType.DOUBLE)) {
            return String.valueOf(container.get(key, PersistentDataType.DOUBLE));
        }
        return "<지원하지 않는 타입>";
    }
}
