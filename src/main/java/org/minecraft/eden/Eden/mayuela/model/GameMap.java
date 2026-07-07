package org.minecraft.eden.Eden.mayuela.model;

import org.bukkit.Location;

public record GameMap(String id, Location position1, Location position2) {

    public boolean contains(Location location) {
        if (location == null || location.getWorld() == null || !location.getWorld().equals(position1.getWorld())) {
            return false;
        }
        int minX = Math.min(position1.getBlockX(), position2.getBlockX());
        int maxX = Math.max(position1.getBlockX(), position2.getBlockX());
        int minY = Math.min(position1.getBlockY(), position2.getBlockY());
        int maxY = Math.max(position1.getBlockY(), position2.getBlockY());
        int minZ = Math.min(position1.getBlockZ(), position2.getBlockZ());
        int maxZ = Math.max(position1.getBlockZ(), position2.getBlockZ());
        return location.getBlockX() >= minX && location.getBlockX() <= maxX
                && location.getBlockY() >= minY && location.getBlockY() <= maxY
                && location.getBlockZ() >= minZ && location.getBlockZ() <= maxZ;
    }
}
