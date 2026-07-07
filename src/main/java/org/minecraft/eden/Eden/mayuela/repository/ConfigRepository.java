package org.minecraft.eden.Eden.mayuela.repository;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.minecraft.eden.Eden.mayuela.model.Altar;
import org.minecraft.eden.Eden.mayuela.model.GameMap;
import org.minecraft.eden.Eden.mayuela.model.PlayerColor;

public final class ConfigRepository {

    private final JavaPlugin plugin;
    private final File dataFolder;
    private YamlConfiguration gameConfig;
    private YamlConfiguration altarConfig;
    private YamlConfiguration mapConfig;
    private YamlConfiguration modelConfig;

    public ConfigRepository(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFolder = plugin.getDataFolder();
    }

    public void load() {
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        gameConfig = loadFile("game.yml");
        altarConfig = loadFile("alter.yml");
        mapConfig = loadFile("maps.yml");
        modelConfig = loadFile("model.yml");
        gameConfig.addDefault("game.max-players", 10);
        gameConfig.addDefault("game.ready-time-seconds", 10);
        gameConfig.addDefault("game.respawn-delay-seconds", 3);
        gameConfig.addDefault("altar.activation-tick-seconds", 1);
        gameConfig.addDefault("altar.base-increase-per-altar", 0.04);
        gameConfig.addDefault("coin.drop-interval-seconds", 10);
        gameConfig.addDefault("coin.drop-amount", 5);
        gameConfig.addDefault("coin.max-dropped-items", 40);
        gameConfig.addDefault("coin.despawn-seconds", 30);
        gameConfig.addDefault("coin.search-radius-blocks", 32);
        gameConfig.addDefault("coin.drop-offset.forward", 1.5);
        gameConfig.addDefault("coin.drop-offset.up", 0.5);
        gameConfig.addDefault("coin.rules.1.interval-seconds", 12);
        gameConfig.addDefault("coin.rules.1.amount", 2);
        gameConfig.addDefault("coin.rules.2.interval-seconds", 10);
        gameConfig.addDefault("coin.rules.2.amount", 3);
        gameConfig.addDefault("coin.rules.3.interval-seconds", 9);
        gameConfig.addDefault("coin.rules.3.amount", 3);
        gameConfig.addDefault("coin.rules.4.interval-seconds", 8);
        gameConfig.addDefault("coin.rules.4.amount", 4);
        gameConfig.addDefault("coin.rules.5.interval-seconds", 8);
        gameConfig.addDefault("coin.rules.5.amount", 4);
        gameConfig.addDefault("coin.rules.6.interval-seconds", 7);
        gameConfig.addDefault("coin.rules.6.amount", 5);
        gameConfig.addDefault("coin.rules.7.interval-seconds", 7);
        gameConfig.addDefault("coin.rules.7.amount", 5);
        gameConfig.addDefault("coin.rules.8.interval-seconds", 6);
        gameConfig.addDefault("coin.rules.8.amount", 6);
        gameConfig.addDefault("coin.rules.9.interval-seconds", 6);
        gameConfig.addDefault("coin.rules.9.amount", 6);
        gameConfig.addDefault("coin.rules.10.interval-seconds", 5);
        gameConfig.addDefault("coin.rules.10.amount", 7);
        gameConfig.addDefault("item.knockback-stick-blocks", 15);
        gameConfig.addDefault("shop.knockback-stick-price", 8);
        gameConfig.addDefault("shop.knockback-stick-max-held", 3);
        gameConfig.addDefault("shop.jump-boost-price", 6);
        gameConfig.addDefault("shop.knockback-resistance-price", 6);
        gameConfig.addDefault("shop.arrow-price", 1);
        gameConfig.addDefault("shop.jump-boost-seconds", 15);
        gameConfig.addDefault("shop.knockback-resistance-seconds", 20);
        gameConfig.addDefault("buff.strength-delay-seconds", 30);
        gameConfig.addDefault("buff.strength-amplifier", 0);
        gameConfig.addDefault("bounty.enabled", true);
        gameConfig.addDefault("bounty.warmup-seconds", 120);
        gameConfig.addDefault("bounty.min-leader-activation-percent", 20.0);
        gameConfig.addDefault("bounty.activate-gap-percent", 10.0);
        gameConfig.addDefault("bounty.release-gap-percent", 7.0);
        gameConfig.addDefault("bounty.transition-cooldown-seconds", 30);
        gameConfig.addDefault("bounty.reward-coins", 8);
        gameConfig.addDefault("stats.excluded-player-names", List.of("Gaensta"));
        gameConfig.options().copyDefaults(true);
        for (PlayerColor color : PlayerColor.defaults()) {
            String path = "flags." + color.id();
            modelConfig.addDefault(path + ".itemsadder-id", defaultFlagItemsAdderId(color.id()));
            modelConfig.addDefault(path + ".fallback-material", color.dyeMaterial().name());
        }
        addShopItemModelDefaults("knockback_stick", Material.STICK);
        addShopItemModelDefaults("jump_boost", Material.RABBIT_FOOT);
        addShopItemModelDefaults("knockback_resistance", Material.SHIELD);
        addShopItemModelDefaults("arrow", Material.ARROW);
        addSystemItemModelDefaults("offhand_lock", Material.PAPER);
        addSystemItemModelDefaults("field_coin", Material.GOLD_NUGGET);
        modelConfig.options().copyDefaults(true);
        migrateOldDefaults();
        saveGameConfig();
        saveAltarConfig();
        saveMapConfig();
        saveModelConfig();
    }

    private YamlConfiguration loadFile(String name) {
        File file = new File(dataFolder, name);
        return YamlConfiguration.loadConfiguration(file);
    }

    public int maxPlayers() {
        return gameConfig.getInt("game.max-players", 10);
    }

    public int readySeconds() {
        return gameConfig.getInt("game.ready-time-seconds", 10);
    }

    public int respawnDelaySeconds() {
        return gameConfig.getInt("game.respawn-delay-seconds", 3);
    }

    public int activationTickSeconds() {
        return gameConfig.getInt("altar.activation-tick-seconds", 1);
    }

    public double baseIncreasePerAltar() {
        return gameConfig.getDouble("altar.base-increase-per-altar", 0.04);
    }

    public int coinDropIntervalSeconds() {
        return gameConfig.getInt("coin.drop-interval-seconds", 10);
    }

    public int coinDropIntervalSeconds(int playerCount) {
        int rulePlayerCount = normalizedCoinRulePlayerCount(playerCount);
        return gameConfig.getInt("coin.rules." + rulePlayerCount + ".interval-seconds", coinDropIntervalSeconds());
    }

    public int coinDropAmount() {
        return gameConfig.getInt("coin.drop-amount", 5);
    }

    public int coinDropAmount(int playerCount) {
        int rulePlayerCount = normalizedCoinRulePlayerCount(playerCount);
        return gameConfig.getInt("coin.rules." + rulePlayerCount + ".amount", coinDropAmount());
    }

    public static int normalizedCoinRulePlayerCount(int playerCount) {
        return Math.max(1, Math.min(10, playerCount));
    }

    public int maxDroppedCoins() {
        return gameConfig.getInt("coin.max-dropped-items", 40);
    }

    public int coinDespawnSeconds() {
        return gameConfig.getInt("coin.despawn-seconds", 30);
    }

    public int coinSearchRadiusBlocks() {
        return gameConfig.getInt("coin.search-radius-blocks", 32);
    }

    public double coinDropForwardOffset() {
        return gameConfig.getDouble("coin.drop-offset.forward", 1.5);
    }

    public double coinDropUpOffset() {
        return gameConfig.getDouble("coin.drop-offset.up", 0.5);
    }

    public int strengthDelaySeconds() {
        return gameConfig.getInt("buff.strength-delay-seconds", 30);
    }

    public int strengthAmplifier() {
        return gameConfig.getInt("buff.strength-amplifier", 0);
    }

    public boolean bountyEnabled() {
        return gameConfig.getBoolean("bounty.enabled", true);
    }

    public int bountyWarmupSeconds() {
        return gameConfig.getInt("bounty.warmup-seconds", 120);
    }

    public double bountyMinLeaderActivationPercent() {
        return gameConfig.getDouble("bounty.min-leader-activation-percent", 20.0);
    }

    public double bountyActivateGapPercent() {
        return gameConfig.getDouble("bounty.activate-gap-percent", 10.0);
    }

    public double bountyReleaseGapPercent() {
        return gameConfig.getDouble("bounty.release-gap-percent", 7.0);
    }

    public int bountyTransitionCooldownSeconds() {
        return gameConfig.getInt("bounty.transition-cooldown-seconds", 30);
    }

    public int bountyRewardCoins() {
        return gameConfig.getInt("bounty.reward-coins", 8);
    }

    public int knockbackStickBlocks() {
        return gameConfig.getInt("item.knockback-stick-blocks", 15);
    }

    public List<String> statsExcludedPlayerNames() {
        return gameConfig.getStringList("stats.excluded-player-names");
    }

    public int knockbackStickPrice() {
        return gameConfig.getInt("shop.knockback-stick-price", 8);
    }

    public int knockbackStickMaxHeld() {
        return normalizedKnockbackStickMaxHeld(gameConfig.getInt("shop.knockback-stick-max-held", 3));
    }

    public static int normalizedKnockbackStickMaxHeld(int maxHeld) {
        return Math.max(1, maxHeld);
    }

    public static String defaultFlagItemsAdderId(String colorId) {
        return "mayuela:flag_" + colorId.toLowerCase(java.util.Locale.ROOT);
    }

    public String flagItemsAdderId(PlayerColor color) {
        return modelConfig.getString("flags." + color.id() + ".itemsadder-id", defaultFlagItemsAdderId(color.id()));
    }

    public Material flagFallbackMaterial(PlayerColor color) {
        String materialName = modelConfig.getString("flags." + color.id() + ".fallback-material", color.dyeMaterial().name());
        Material material = Material.matchMaterial(materialName);
        return material == null || material.isAir() ? color.dyeMaterial() : material;
    }

    public static String defaultShopItemItemsAdderId(String itemId) {
        return "mayuela:" + itemId;
    }

    public String shopItemItemsAdderId(String itemId) {
        return modelConfig.getString("shop-items." + itemId + ".itemsadder-id", defaultShopItemItemsAdderId(itemId));
    }

    public Material shopItemFallbackMaterial(String itemId, Material defaultMaterial) {
        String materialName = modelConfig.getString("shop-items." + itemId + ".fallback-material", defaultMaterial.name());
        Material material = Material.matchMaterial(materialName);
        return material == null || material.isAir() ? defaultMaterial : material;
    }

    public static String defaultSystemItemItemsAdderId(String itemId) {
        return "mayuela:" + itemId;
    }

    public String systemItemItemsAdderId(String itemId) {
        return modelConfig.getString("system-items." + itemId + ".itemsadder-id", defaultSystemItemItemsAdderId(itemId));
    }

    public Material systemItemFallbackMaterial(String itemId, Material defaultMaterial) {
        String materialName = modelConfig.getString("system-items." + itemId + ".fallback-material", defaultMaterial.name());
        Material material = Material.matchMaterial(materialName);
        return material == null || material.isAir() ? defaultMaterial : material;
    }

    public int jumpBoostPrice() {
        return gameConfig.getInt("shop.jump-boost-price", 6);
    }

    public int knockbackResistancePrice() {
        return gameConfig.getInt("shop.knockback-resistance-price", 6);
    }

    public int arrowPrice() {
        return gameConfig.getInt("shop.arrow-price", 1);
    }

    public int jumpBoostSeconds() {
        return gameConfig.getInt("shop.jump-boost-seconds", 15);
    }

    public int knockbackResistanceSeconds() {
        return gameConfig.getInt("shop.knockback-resistance-seconds", 20);
    }

    public void saveCoinRule(int playerCount, int intervalSeconds, int amount) {
        gameConfig.set("coin.rules." + playerCount + ".interval-seconds", intervalSeconds);
        gameConfig.set("coin.rules." + playerCount + ".amount", amount);
        saveGameConfig();
    }

    public void saveCoinDespawnSeconds(int seconds) {
        gameConfig.set("coin.despawn-seconds", seconds);
        saveGameConfig();
    }

    public void saveMaxDroppedCoins(int maxDroppedCoins) {
        gameConfig.set("coin.max-dropped-items", maxDroppedCoins);
        saveGameConfig();
    }

    public void saveKnockbackStickBlocks(int blocks) {
        gameConfig.set("item.knockback-stick-blocks", blocks);
        saveGameConfig();
    }

    public void saveKnockbackStickMaxHeld(int maxHeld) {
        gameConfig.set("shop.knockback-stick-max-held", normalizedKnockbackStickMaxHeld(maxHeld));
        saveGameConfig();
    }

    public boolean hasLobby() {
        return gameConfig.getBoolean("lobby.enabled", false);
    }

    public Location lobbyLocation() {
        return readLocation(gameConfig.getConfigurationSection("lobby"));
    }

    public void saveLobby(Location location) {
        gameConfig.set("lobby.enabled", true);
        writeLocation(gameConfig, "lobby", location);
        saveGameConfig();
    }

    public void saveShop(Location location) {
        writeLocation(gameConfig, "shop", location);
        saveGameConfig();
    }

    public Location shopLocation() {
        return readLocation(gameConfig.getConfigurationSection("shop"));
    }

    public boolean hasGameSpawn() {
        return gameSpawnLocation() != null;
    }

    public Location gameSpawnLocation() {
        return readLocation(gameConfig.getConfigurationSection("game-spawn"));
    }

    public void saveGameSpawn(Location location) {
        writeLocation(gameConfig, "game-spawn", location);
        saveGameConfig();
    }

    public void saveAltar(String id, Location location) {
        writeLocation(altarConfig, "altars." + id, location);
        saveAltarConfig();
    }

    public void saveMap(String name, Location position1, Location position2) {
        String path = "maps." + name;
        mapConfig.set(path + ".world", position1.getWorld().getName());
        writeLocation(mapConfig, path + ".position1", position1);
        writeLocation(mapConfig, path + ".position2", position2);
        saveMapConfig();
    }

    public boolean hasAnyMap() {
        ConfigurationSection section = mapConfig.getConfigurationSection("maps");
        return section != null && !section.getKeys(false).isEmpty();
    }

    public Optional<GameMap> firstMap() {
        ConfigurationSection section = mapConfig.getConfigurationSection("maps");
        if (section == null) {
            return Optional.empty();
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection map = section.getConfigurationSection(id);
            if (map == null) {
                continue;
            }
            Location position1 = readLocation(map.getConfigurationSection("position1"));
            Location position2 = readLocation(map.getConfigurationSection("position2"));
            if (position1 != null && position2 != null) {
                return Optional.of(new GameMap(id, position1, position2));
            }
        }
        return Optional.empty();
    }

    public Map<String, Altar> loadAltars() {
        Map<String, Altar> altars = new LinkedHashMap<>();
        ConfigurationSection section = altarConfig.getConfigurationSection("altars");
        if (section == null) {
            return altars;
        }
        for (String id : section.getKeys(false)) {
            Location location = readLocation(section.getConfigurationSection(id));
            if (location != null) {
                altars.put(id, new Altar(id, location));
            }
        }
        return altars;
    }

    private void writeLocation(YamlConfiguration config, String path, Location location) {
        config.set(path + ".world", location.getWorld().getName());
        config.set(path + ".x", location.getBlockX());
        config.set(path + ".y", location.getBlockY());
        config.set(path + ".z", location.getBlockZ());
        config.set(path + ".yaw", location.getYaw());
        config.set(path + ".pitch", location.getPitch());
    }

    private Location readLocation(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        World world = Bukkit.getWorld(section.getString("world", ""));
        if (world == null) {
            return null;
        }
        return new Location(
                world,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw"),
                (float) section.getDouble("pitch")
        );
    }

    private void saveGameConfig() {
        save(gameConfig, "game.yml");
    }

    private void migrateOldDefaults() {
        if (Math.abs(gameConfig.getDouble("altar.base-increase-per-altar", 0.04) - 0.2) < 0.000001) {
            gameConfig.set("altar.base-increase-per-altar", 0.04);
        }
    }

    private void saveAltarConfig() {
        save(altarConfig, "alter.yml");
    }

    private void saveMapConfig() {
        save(mapConfig, "maps.yml");
    }

    private void addShopItemModelDefaults(String itemId, Material fallbackMaterial) {
        String path = "shop-items." + itemId;
        modelConfig.addDefault(path + ".itemsadder-id", defaultShopItemItemsAdderId(itemId));
        modelConfig.addDefault(path + ".fallback-material", fallbackMaterial.name());
    }

    private void addSystemItemModelDefaults(String itemId, Material fallbackMaterial) {
        String path = "system-items." + itemId;
        modelConfig.addDefault(path + ".itemsadder-id", defaultSystemItemItemsAdderId(itemId));
        modelConfig.addDefault(path + ".fallback-material", fallbackMaterial.name());
    }

    private void saveModelConfig() {
        save(modelConfig, "model.yml");
    }

    private void save(YamlConfiguration config, String name) {
        try {
            config.save(new File(dataFolder, name));
        } catch (IOException exception) {
            plugin.getLogger().severe(name + " 저장 실패: " + exception.getMessage());
        }
    }
}
