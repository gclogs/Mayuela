package org.minecraft.eden.Eden.mayuela.service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import org.minecraft.eden.Eden.mayuela.model.Altar;
import org.minecraft.eden.Eden.mayuela.model.GameTeam;

public final class DisplayService {

    private final JavaPlugin plugin;
    private final Map<UUID, ItemDisplay> playerDisplays = new HashMap<>();
    private final Map<UUID, TextDisplay> bountyDisplays = new HashMap<>();
    private final Map<String, TextDisplay> altarDisplays = new HashMap<>();
    private BukkitTask followTask;
    private static final double PLAYER_DISPLAY_Y_OFFSET = 2.85;
    private static final double BOUNTY_DISPLAY_Y_OFFSET = 2.45;

    public DisplayService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void spawnPlayerDisplay(Player player, GameTeam team) {
        spawnPlayerDisplay(player, team, 0);
    }

    public void spawnPlayerDisplay(Player player, GameTeam team, int bountyRewardCoins) {
        removePlayerDisplay(player.getUniqueId());
        ItemDisplay display = player.getWorld().spawn(player.getLocation().add(0, PLAYER_DISPLAY_Y_OFFSET, 0), ItemDisplay.class, entity -> {
            entity.setItemStack(new ItemStack(team.color().blockMaterial()));
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setTeleportDuration(1);
            entity.setTransformation(new Transformation(
                    new Vector3f(),
                    new AxisAngle4f(),
                    new Vector3f(0.45f, 0.45f, 0.45f),
                    new AxisAngle4f()
            ));
            entity.setPersistent(false);
            entity.setGravity(false);
            entity.setInvulnerable(true);
        });
        playerDisplays.put(player.getUniqueId(), display);
        updatePlayerIdentity(player, team, bountyRewardCoins);
        updateBountyDisplay(player, team, bountyRewardCoins);
    }

    public void updateBountyDisplay(Player player, GameTeam team) {
        updateBountyDisplay(player, team, 0);
    }

    public void updateBountyDisplay(Player player, GameTeam team, int bountyRewardCoins) {
        if (!team.bountyWanted()) {
            removeBountyDisplay(player.getUniqueId());
            updatePlayerIdentity(player, team, bountyRewardCoins);
            return;
        }
        TextDisplay display = bountyDisplays.get(player.getUniqueId());
        Location location = player.getLocation().add(0, BOUNTY_DISPLAY_Y_OFFSET, 0);
        if (display == null || display.isDead()) {
            display = player.getWorld().spawn(location, TextDisplay.class, entity -> {
                entity.setBillboard(Display.Billboard.CENTER);
                entity.setTeleportDuration(1);
                entity.setPersistent(false);
                entity.setGravity(false);
                entity.setInvulnerable(true);
            });
            bountyDisplays.put(player.getUniqueId(), display);
        }
        display.teleport(location);
        display.text(bountyTitle(bountyRewardCoins));
        updatePlayerIdentity(player, team, bountyRewardCoins);
    }

    public void updatePlayerIdentity(Player player, GameTeam team, int bountyRewardCoins) {
        Component name = Component.text(player.getName(), teamTextColor(team));
        player.displayName(name);
        if (team.bountyWanted()) {
            player.playerListName(bountyTitle(bountyRewardCoins)
                    .append(Component.space())
                    .append(name));
            return;
        }
        player.playerListName(name);
    }

    public void resetPlayerIdentity(Player player) {
        Component name = Component.text(player.getName());
        player.displayName(name);
        player.playerListName(name);
    }

    private Component bountyTitle(int bountyRewardCoins) {
        return Component.text("[현상수배 +", NamedTextColor.RED)
                .append(Component.text(Math.max(0, bountyRewardCoins), NamedTextColor.GOLD))
                .append(Component.text("코인]", NamedTextColor.RED));
    }

    private TextColor teamTextColor(GameTeam team) {
        TextColor color = TextColor.fromHexString(team.color().hex());
        return color == null ? NamedTextColor.WHITE : color;
    }

    public void startFollowTask() {
        stopFollowTask();
        followTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Map.Entry<UUID, ItemDisplay> entry : playerDisplays.entrySet()) {
                Player player = Bukkit.getPlayer(entry.getKey());
                ItemDisplay display = entry.getValue();
                if (player == null || !player.isOnline() || display.isDead()) {
                    continue;
                }
                display.teleport(player.getLocation().add(0, PLAYER_DISPLAY_Y_OFFSET, 0));
            }
            for (Map.Entry<UUID, TextDisplay> entry : bountyDisplays.entrySet()) {
                Player player = Bukkit.getPlayer(entry.getKey());
                TextDisplay display = entry.getValue();
                if (player == null || !player.isOnline() || display.isDead()) {
                    continue;
                }
                display.teleport(player.getLocation().add(0, BOUNTY_DISPLAY_Y_OFFSET, 0));
            }
        }, 1L, 1L);
    }

    public void stopFollowTask() {
        if (followTask != null) {
            followTask.cancel();
            followTask = null;
        }
    }

    public void updateAltarDisplay(Altar altar, GameTeam owner) {
        TextDisplay display = altarDisplays.get(altar.id());
        Location location = altar.location().add(0.5, 3, 0.5);
        if (display == null || display.isDead()) {
            display = location.getWorld().spawn(location, TextDisplay.class, entity -> {
                entity.setBillboard(Display.Billboard.CENTER);
                entity.setPersistent(false);
                entity.setGravity(false);
                entity.setInvulnerable(true);
                entity.setGlowing(true);
            });
            altarDisplays.put(altar.id(), display);
        }
        display.teleport(location);
        display.text(Component.text(owner.color().displayName() + " 팀", TextColor.fromHexString(owner.color().hex()))
                .append(Component.newline())
                .append(Component.text("누적 활성화율 " + String.format("%.1f%%", owner.activationRate()))));
    }

    public void showEmptyAltarDisplay(Altar altar) {
        TextDisplay display = altarDisplays.get(altar.id());
        Location location = altar.location().add(0.5, 1.8, 0.5);
        if (display == null || display.isDead()) {
            display = location.getWorld().spawn(location, TextDisplay.class, entity -> {
                entity.setBillboard(Display.Billboard.CENTER);
                entity.setPersistent(false);
                entity.setGravity(false);
                entity.setInvulnerable(true);
            });
            altarDisplays.put(altar.id(), display);
        }
        display.setGlowing(false);
        display.teleport(location);
        display.text(Component.text("제단 설치 위치")
                .append(Component.newline())
                .append(Component.text(altar.id())));
    }

    public void removeAltarDisplay(String altarId) {
        Entity entity = altarDisplays.remove(altarId);
        if (entity != null) {
            entity.remove();
        }
    }

    public void removePlayerDisplay(UUID uuid) {
        Entity entity = playerDisplays.remove(uuid);
        if (entity != null) {
            entity.remove();
        }
        removeBountyDisplay(uuid);
    }

    public void removeAll() {
        stopFollowTask();
        playerDisplays.values().forEach(Entity::remove);
        bountyDisplays.values().forEach(Entity::remove);
        altarDisplays.values().forEach(Entity::remove);
        playerDisplays.clear();
        bountyDisplays.clear();
        altarDisplays.clear();
    }

    private void removeBountyDisplay(UUID uuid) {
        Entity entity = bountyDisplays.remove(uuid);
        if (entity != null) {
            entity.remove();
        }
    }
}
