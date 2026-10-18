package org.minecraft.eden.Eden.mayuela.model;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class GameTeam {

    private final String id;
    private final PlayerColor color;
    private final Set<UUID> members = new LinkedHashSet<>();
    private final Set<String> capturedAltars = new HashSet<>();
    private final Set<UUID> claimedBountyRewards = new HashSet<>();
    private double activationRate;
    private long capturedSeconds;
    private long zeroAltarSeconds;
    private boolean strengthBuffActive;
    private boolean bountyWanted;

    public GameTeam(String id, PlayerColor color) {
        this.id = id;
        this.color = color;
    }

    public String id() {
        return id;
    }

    public PlayerColor color() {
        return color;
    }

    public Set<UUID> members() {
        return members;
    }

    public Set<String> capturedAltars() {
        return capturedAltars;
    }

    public double activationRate() {
        return activationRate;
    }

    public void increaseActivation(double amount) {
        activationRate = Math.min(100.0, activationRate + amount);
    }

    public long capturedSeconds() {
        return capturedSeconds;
    }

    public void addCapturedSeconds(long seconds) {
        capturedSeconds += seconds;
    }

    public long zeroAltarSeconds() {
        return zeroAltarSeconds;
    }

    public void addZeroAltarSeconds(long seconds) {
        zeroAltarSeconds += seconds;
    }

    public void resetZeroAltarSeconds() {
        zeroAltarSeconds = 0L;
    }

    public boolean strengthBuffActive() {
        return strengthBuffActive;
    }

    public void setStrengthBuffActive(boolean strengthBuffActive) {
        this.strengthBuffActive = strengthBuffActive;
    }

    public boolean bountyWanted() {
        return bountyWanted;
    }

    public void setBountyWanted(boolean bountyWanted) {
        this.bountyWanted = bountyWanted;
    }

    public boolean bountyRewardClaimed(UUID playerId) {
        return claimedBountyRewards.contains(playerId);
    }

    public void markBountyRewardClaimed(UUID playerId) {
        claimedBountyRewards.add(playerId);
    }

    public void clearBountyRewardClaims() {
        claimedBountyRewards.clear();
    }

    public void resetGameState() {
        capturedAltars.clear();
        claimedBountyRewards.clear();
        activationRate = 0.0;
        capturedSeconds = 0L;
        zeroAltarSeconds = 0L;
        strengthBuffActive = false;
        bountyWanted = false;
    }
}
