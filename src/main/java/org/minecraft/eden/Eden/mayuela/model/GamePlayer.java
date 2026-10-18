package org.minecraft.eden.Eden.mayuela.model;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class GamePlayer {

    private final UUID uuid;
    private final String name;
    private final Set<String> capturedAltars = new HashSet<>();
    private double activationRate;
    private int coins;
    private long capturedSeconds;

    public GamePlayer(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
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

    public void resetGameState() {
        capturedAltars.clear();
        activationRate = 0.0;
        capturedSeconds = 0L;
        coins = 0;
    }

    public int coins() {
        return coins;
    }

    public void addCoins(int amount) {
        coins += amount;
    }
}
