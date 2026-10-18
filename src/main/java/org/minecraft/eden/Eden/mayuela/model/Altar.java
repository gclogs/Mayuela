package org.minecraft.eden.Eden.mayuela.model;

import org.bukkit.Location;

public final class Altar {

    private final String id;
    private final Location location;
    private String ownerTeamId;

    public Altar(String id, Location location) {
        this.id = id;
        this.location = location;
    }

    public String id() {
        return id;
    }

    public Location location() {
        return location.clone();
    }

    public String ownerTeamId() {
        return ownerTeamId;
    }

    public void setOwnerTeamId(String ownerTeamId) {
        this.ownerTeamId = ownerTeamId;
    }
}
