package org.minecraft.eden.Eden.mayuela.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.minecraft.eden.Eden.mayuela.message.MayuelaMessage;
import org.minecraft.eden.Eden.mayuela.message.MessageService;
import org.minecraft.eden.Eden.mayuela.model.GameMap;
import org.minecraft.eden.Eden.mayuela.model.GamePlayer;
import org.minecraft.eden.Eden.mayuela.repository.ConfigRepository;
import org.minecraft.eden.Eden.mayuela.sound.MayuelaSound;

public final class CoinService {

    private static final NamespacedKey COIN_KEY = new NamespacedKey("mayuela", "coin");
    private static final NamespacedKey COIN_ITEMSADDER_ID_KEY = new NamespacedKey("mayuela", "coin_itemsadder_id");
    private static final String FIELD_COIN_MODEL_ID = "field_coin";

    private final JavaPlugin plugin;
    private final ConfigRepository configRepository;
    private final GamePlayerService gamePlayerService;
    private final TeamService teamService;
    private final ScoreboardService scoreboardService;
    private final MatchStatsService matchStatsService;
    private final MessageService messageService;
    private final Map<UUID, TrackedCoin> droppedCoins = new HashMap<>();
    private final Map<UUID, Integer> anchorSelections = new HashMap<>();
    private final Map<UUID, Integer> pickupCounts = new HashMap<>();
    private final Map<UUID, Integer> pickupCoins = new HashMap<>();
    private int successfulDrops;
    private int failedDropSearches;
    private BukkitTask dropTask;
    private BukkitTask displayTask;

    public CoinService(JavaPlugin plugin, ConfigRepository configRepository, GamePlayerService gamePlayerService, TeamService teamService, ScoreboardService scoreboardService, MatchStatsService matchStatsService, MessageService messageService) {
        this.plugin = plugin;
        this.configRepository = configRepository;
        this.gamePlayerService = gamePlayerService;
        this.teamService = teamService;
        this.scoreboardService = scoreboardService;
        this.matchStatsService = matchStatsService;
        this.messageService = messageService;
    }

    public void start() {
        stop();
        int playerCount = ConfigRepository.normalizedCoinRulePlayerCount(gamePlayerService.all().size());
        long period = Math.max(1, configRepository.coinDropIntervalSeconds(playerCount)) * 20L;
        dropTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::dropCoin, 20L, period);
    }

    public void stop() {
        if (dropTask != null) {
            dropTask.cancel();
            dropTask = null;
        }
        if (displayTask != null) {
            displayTask.cancel();
            displayTask = null;
        }
        for (Map.Entry<UUID, TrackedCoin> entry : droppedCoins.entrySet()) {
            removeDisplay(entry.getValue());
            UUID uuid = entry.getKey();
            for (org.bukkit.World world : plugin.getServer().getWorlds()) {
                Entity entity = world.getEntity(uuid);
                if (entity != null) {
                    entity.remove();
                }
            }
        }
        droppedCoins.clear();
        anchorSelections.clear();
        pickupCounts.clear();
        pickupCoins.clear();
        successfulDrops = 0;
        failedDropSearches = 0;
    }

    public int droppedCoinCount() {
        cleanupExpiredCoins();
        return droppedCoins.size();
    }

    public boolean isCoin(ItemStack itemStack) {
        if (itemStack == null || !itemStack.hasItemMeta()) {
            return false;
        }
        return itemStack.getItemMeta().getPersistentDataContainer().has(COIN_KEY, PersistentDataType.INTEGER);
    }

    public boolean isTrackedFieldCoin(Item item) {
        return droppedCoins.containsKey(item.getUniqueId());
    }

    public List<Component> monitoringLines() {
        cleanupExpiredCoins();
        List<Component> lines = new ArrayList<>();
        int participantCount = gamePlayerService.all().size();
        int rulePlayers = ConfigRepository.normalizedCoinRulePlayerCount(participantCount);
        lines.add(Component.text("코인 드랍 방식: 참가자 1명을 균등 랜덤 앵커로 선택한 뒤 주변 반경 지상에 드랍"));
        lines.add(Component.text("청크 단위 드랍: 아님, 현재는 플레이어 주변 반경 " + Math.max(8, configRepository.coinSearchRadiusBlocks()) + "블록 기준"));
        lines.add(Component.text("현재 필드 코인: " + droppedCoins.size() + " / " + configRepository.maxDroppedCoins()));
        lines.add(Component.text("적용 룰: " + rulePlayers + "명, " + configRepository.coinDropIntervalSeconds(rulePlayers) + "초마다 +" + configRepository.coinDropAmount(rulePlayers)));
        lines.add(Component.text("성공 드랍/위치탐색 실패: " + successfulDrops + " / " + failedDropSearches));
        for (GamePlayer player : gamePlayerService.all()) {
            int anchors = anchorSelections.getOrDefault(player.uuid(), 0);
            int pickups = pickupCounts.getOrDefault(player.uuid(), 0);
            int coins = pickupCoins.getOrDefault(player.uuid(), 0);
            double anchorShare = successfulDrops == 0 ? 0.0 : anchors * 100.0 / successfulDrops;
            lines.add(Component.text(player.name() + " 앵커 " + anchors + "회(" + String.format("%.1f%%", anchorShare) + "), 획득 " + pickups + "회/+" + coins));
        }
        return lines;
    }

    public ItemStack createCoinItem(int amount) {
        String itemsAdderId = configRepository.systemItemItemsAdderId(FIELD_COIN_MODEL_ID);
        ItemStack coin = createConfiguredCoinItem(itemsAdderId, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = coin.getItemMeta();
        meta.displayName(Component.text("코인"));
        meta.getPersistentDataContainer().set(COIN_KEY, PersistentDataType.INTEGER, amount);
        meta.getPersistentDataContainer().set(COIN_ITEMSADDER_ID_KEY, PersistentDataType.STRING, itemsAdderId);
        coin.setItemMeta(meta);
        return coin;
    }

    public boolean grantCoins(Player player, int amount) {
        GamePlayer gamePlayer = gamePlayerService.find(player.getUniqueId()).orElse(null);
        if (gamePlayer == null || amount <= 0) {
            return false;
        }
        ItemStack coinStack = createCoinItem(amount);
        if (!canFit(player, coinStack)) {
            return false;
        }
        player.getInventory().addItem(coinStack);
        gamePlayer.addCoins(amount);
        scoreboardService.updateBoard(gamePlayerService.all(), teamService.all());
        return true;
    }

    public void pickup(Player player, Item item) {
        if (!isCoin(item.getItemStack())) {
            return;
        }
        if (!isTrackedFieldCoin(item)) {
            return;
        }
        GamePlayer gamePlayer = gamePlayerService.find(player.getUniqueId()).orElse(null);
        if (gamePlayer == null) {
            return;
        }
        int amount = item.getItemStack().getItemMeta().getPersistentDataContainer().getOrDefault(COIN_KEY, PersistentDataType.INTEGER, 1);
        ItemStack coinStack = item.getItemStack().clone();
        coinStack.setAmount(amount);
        if (!canFit(player, coinStack)) {
            messageService.send(player, MayuelaMessage.COIN_INVENTORY_FULL);
            return;
        }
        player.getInventory().addItem(coinStack);
        gamePlayer.addCoins(amount);
        matchStatsService.recordCoinPickedUp(matchStatsService.elapsedSeconds(), player, item, amount);
        pickupCounts.merge(player.getUniqueId(), 1, Integer::sum);
        pickupCoins.merge(player.getUniqueId(), amount, Integer::sum);
        TrackedCoin trackedCoin = droppedCoins.remove(item.getUniqueId());
        removeDisplay(trackedCoin);
        item.remove();
        MayuelaSound.COIN_PICKUP.play(player);
        messageService.send(player, MayuelaMessage.COIN_PICKED_UP, amount);
        scoreboardService.updateBoard(gamePlayerService.all(), teamService.all());
    }

    private void dropCoin() {
        cleanupExpiredCoins();
        if (droppedCoins.size() >= configRepository.maxDroppedCoins()) {
            return;
        }
        CoinDropTarget target = findGroundDropLocation();
        if (target == null || target.location().getWorld() == null) {
            failedDropSearches++;
            return;
        }
        int playerCount = ConfigRepository.normalizedCoinRulePlayerCount(gamePlayerService.all().size());
        ItemStack coin = createCoinItem(configRepository.coinDropAmount(playerCount));
        Location dropLocation = target.location();
        Item dropped = dropLocation.getWorld().dropItem(dropLocation, coin);
        dropped.setVelocity(new Vector(0, 0.05, 0));
        dropped.setCanMobPickup(false);
        dropped.setGlowing(true);
        TrackedCoin trackedCoin = new TrackedCoin(System.currentTimeMillis(), target.anchorUuid(), spawnCoinDisplay(dropLocation, configRepository.coinDespawnSeconds()));
        droppedCoins.put(dropped.getUniqueId(), trackedCoin);
        anchorSelections.merge(target.anchorUuid(), 1, Integer::sum);
        successfulDrops++;
        startDisplayTask();
        matchStatsService.recordCoinDropped(matchStatsService.elapsedSeconds(), dropped, coin.getAmount(), dropLocation, target.anchor(), target.candidateCount(), target.distanceFromAnchor());
    }

    private ItemStack createConfiguredCoinItem(String itemsAdderId, int amount) {
        ItemStack itemsAdderItem = createItemsAdderItem(itemsAdderId);
        if (itemsAdderItem != null) {
            itemsAdderItem.setAmount(amount);
            return itemsAdderItem;
        }
        return new ItemStack(configRepository.systemItemFallbackMaterial(FIELD_COIN_MODEL_ID, Material.GOLD_NUGGET), amount);
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

    private TextDisplay spawnCoinDisplay(Location dropLocation, int despawnSeconds) {
        Location displayLocation = dropLocation.clone().add(0, 0.85, 0);
        return dropLocation.getWorld().spawn(displayLocation, TextDisplay.class, entity -> {
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setPersistent(false);
            entity.setGravity(false);
            entity.setInvulnerable(true);
            entity.text(coinDisplayText(despawnSeconds));
        });
    }

    private void startDisplayTask() {
        if (displayTask != null) {
            return;
        }
        displayTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            cleanupExpiredCoins();
            updateCoinDisplays();
            if (droppedCoins.isEmpty() && displayTask != null) {
                displayTask.cancel();
                displayTask = null;
            }
        }, 20L, 20L);
    }

    private void updateCoinDisplays() {
        long now = System.currentTimeMillis();
        int despawnSeconds = Math.max(1, configRepository.coinDespawnSeconds());
        for (TrackedCoin trackedCoin : droppedCoins.values()) {
            if (trackedCoin.display() == null || trackedCoin.display().isDead()) {
                continue;
            }
            long elapsedMillis = now - trackedCoin.droppedAtMillis();
            int secondsLeft = Math.max(0, despawnSeconds - (int) (elapsedMillis / 1000L));
            trackedCoin.display().text(coinDisplayText(secondsLeft));
        }
    }

    private Component coinDisplayText(int secondsLeft) {
        return Component.text(secondsLeft + "초 뒤 제거됩니다.", NamedTextColor.YELLOW);
    }

    private CoinDropTarget findGroundDropLocation() {
        GameMap map = configRepository.firstMap().orElse(null);
        if (map == null) {
            return null;
        }
        List<Player> candidates = onlineParticipantsInMap(map);
        Player anchor = randomOnlineParticipant(candidates);
        if (anchor == null) {
            return null;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int radius = Math.max(8, configRepository.coinSearchRadiusBlocks());
        World world = anchor.getWorld();
        for (int attempt = 0; attempt < 32; attempt++) {
            int x = anchor.getLocation().getBlockX() + random.nextInt(-radius, radius + 1);
            int z = anchor.getLocation().getBlockZ() + random.nextInt(-radius, radius + 1);
            int y = world.getHighestBlockYAt(x, z);
            Location dropLocation = new Location(world, x + 0.5, y + 1.0, z + 0.5);
            Location groundLocation = new Location(world, x, y, z);
            Material groundType = groundLocation.getBlock().getType();
            if (map.contains(dropLocation)
                    && !groundType.isAir()
                    && !groundLocation.getBlock().isLiquid()
                    && !Tag.LEAVES.isTagged(groundType)) {
                return new CoinDropTarget(dropLocation, anchor, candidates.size(), dropLocation.distance(anchor.getLocation()));
            }
        }
        return null;
    }

    private boolean canFit(Player player, ItemStack itemStack) {
        int remaining = itemStack.getAmount();
        for (ItemStack content : player.getInventory().getStorageContents()) {
            if (content == null || content.getType().isAir()) {
                return true;
            }
            if (content.isSimilar(itemStack) && content.getAmount() < content.getMaxStackSize()) {
                remaining -= content.getMaxStackSize() - content.getAmount();
                if (remaining <= 0) {
                    return true;
                }
            }
        }
        return remaining <= 0;
    }

    private List<Player> onlineParticipantsInMap(GameMap map) {
        List<Player> candidates = new ArrayList<>();
        for (GamePlayer gamePlayer : gamePlayerService.all()) {
            Player player = plugin.getServer().getPlayer(gamePlayer.uuid());
            if (player != null && map.contains(player.getLocation())) {
                candidates.add(player);
            }
        }
        return candidates;
    }

    private Player randomOnlineParticipant(List<Player> candidates) {
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    private void cleanupExpiredCoins() {
        long now = System.currentTimeMillis();
        long maxAgeMillis = Math.max(1, configRepository.coinDespawnSeconds()) * 1000L;
        java.util.Iterator<Map.Entry<UUID, TrackedCoin>> iterator = droppedCoins.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, TrackedCoin> entry = iterator.next();
            Entity entity = null;
            for (org.bukkit.World world : plugin.getServer().getWorlds()) {
                entity = world.getEntity(entry.getKey());
                if (entity != null) {
                    break;
                }
            }
            if (entity == null || now - entry.getValue().droppedAtMillis() >= maxAgeMillis) {
                if (entity != null) {
                    entity.remove();
                }
                removeDisplay(entry.getValue());
                matchStatsService.recordCoinExpired(matchStatsService.elapsedSeconds(), entry.getKey());
                iterator.remove();
            }
        }
    }

    private void removeDisplay(TrackedCoin trackedCoin) {
        if (trackedCoin != null && trackedCoin.display() != null && !trackedCoin.display().isDead()) {
            trackedCoin.display().remove();
        }
    }

    private record TrackedCoin(long droppedAtMillis, UUID anchorUuid, TextDisplay display) {
    }

    private record CoinDropTarget(Location location, Player anchor, int candidateCount, double distanceFromAnchor) {
        UUID anchorUuid() {
            return anchor.getUniqueId();
        }
    }
}
