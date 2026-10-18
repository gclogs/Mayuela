package org.minecraft.eden.Eden.mayuela.model;

import java.util.List;
import org.bukkit.Material;

public record PlayerColor(String id, String displayName, String hex, Material blockMaterial, Material dyeMaterial) {

    public static List<PlayerColor> defaults() {
        return List.of(
                new PlayerColor("RED", "빨강", "#FF3B30", Material.RED_STAINED_GLASS, Material.RED_DYE),
                new PlayerColor("YELLOW", "노랑", "#FFD60A", Material.YELLOW_STAINED_GLASS, Material.YELLOW_DYE),
                new PlayerColor("BLUE", "파랑", "#007AFF", Material.BLUE_STAINED_GLASS, Material.BLUE_DYE),
                new PlayerColor("GREEN", "초록", "#34C759", Material.GREEN_STAINED_GLASS, Material.GREEN_DYE),
                new PlayerColor("PINK", "분홍", "#FF2D55", Material.PINK_STAINED_GLASS, Material.PINK_DYE),
                new PlayerColor("PURPLE", "보라", "#AF52DE", Material.PURPLE_STAINED_GLASS, Material.PURPLE_DYE),
                new PlayerColor("ORANGE", "주황", "#FF9500", Material.ORANGE_STAINED_GLASS, Material.ORANGE_DYE),
                new PlayerColor("LIGHT_BLUE", "하늘", "#5AC8FA", Material.LIGHT_BLUE_STAINED_GLASS, Material.LIGHT_BLUE_DYE),
                new PlayerColor("WHITE", "흰색", "#F2F2F7", Material.WHITE_STAINED_GLASS, Material.WHITE_DYE),
                new PlayerColor("BLACK", "검정", "#1C1C1E", Material.BLACK_STAINED_GLASS, Material.BLACK_DYE)
        );
    }
}
