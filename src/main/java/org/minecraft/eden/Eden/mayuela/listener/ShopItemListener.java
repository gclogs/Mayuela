package org.minecraft.eden.Eden.mayuela.listener;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.minecraft.eden.Eden.mayuela.message.MayuelaMessage;
import org.minecraft.eden.Eden.mayuela.message.MessageService;
import org.minecraft.eden.Eden.mayuela.model.GameTeam;
import org.minecraft.eden.Eden.mayuela.repository.ConfigRepository;
import org.minecraft.eden.Eden.mayuela.service.MatchStatsService;
import org.minecraft.eden.Eden.mayuela.service.RespawnProtectionService;
import org.minecraft.eden.Eden.mayuela.service.ShopService;
import org.minecraft.eden.Eden.mayuela.service.TeamService;
import org.minecraft.eden.Eden.mayuela.service.KnockbackService;
import org.minecraft.eden.Eden.mayuela.sound.MayuelaSound;

public final class ShopItemListener implements Listener {

    private final ConfigRepository configRepository;
    private final ShopService shopService;
    private final MatchStatsService matchStatsService;
    private final RespawnProtectionService respawnProtectionService;
    private final TeamService teamService;
    private final MessageService messageService;
    private final KnockbackService knockbackService;
    private final Map<UUID, Long> knockbackResistanceExpiresAt = new HashMap<>();
    private final Map<UUID, Long> jumpBoostExpiresAt = new HashMap<>();
    private final Map<UUID, Long> buffFeedbackTokens = new HashMap<>();

    public ShopItemListener(ConfigRepository configRepository, ShopService shopService, MatchStatsService matchStatsService, RespawnProtectionService respawnProtectionService, TeamService teamService, MessageService messageService, KnockbackService knockbackService) {
        this.configRepository = configRepository;
        this.shopService = shopService;
        this.matchStatsService = matchStatsService;
        this.respawnProtectionService = respawnProtectionService;
        this.teamService = teamService;
        this.messageService = messageService;
        this.knockbackService = knockbackService;
    }

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        ShopService.ShopItem item = shopService.readShopItem(event.getItem());
        if (item == null) {
            return;
        }
        switch (item) {
            case JUMP_BOOST -> {
                event.setCancelled(true);
                consumeOne(event.getPlayer());
                int durationSeconds = configRepository.jumpBoostSeconds();
                long expiresAt = System.currentTimeMillis() + durationSeconds * 1000L;
                jumpBoostExpiresAt.put(event.getPlayer().getUniqueId(), expiresAt);
                event.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, durationSeconds * 20, 1, false, false));
                matchStatsService.recordShopItemUsed(matchStatsService.elapsedSeconds(), event.getPlayer(), item);
                startBuffFeedback(event.getPlayer());
                event.getPlayer().sendMessage(Component.text("점프 강화가 적용되었습니다."));
            }
            case KNOCKBACK_RESISTANCE -> {
                event.setCancelled(true);
                consumeOne(event.getPlayer());
                int durationSeconds = configRepository.knockbackResistanceSeconds();
                long expiresAt = System.currentTimeMillis() + durationSeconds * 1000L;
                knockbackResistanceExpiresAt.put(event.getPlayer().getUniqueId(), expiresAt);
                matchStatsService.recordShopItemUsed(matchStatsService.elapsedSeconds(), event.getPlayer(), item);
                MayuelaSound.KNOCKBACK_RESISTANCE_USED.play(event.getPlayer());
                startBuffFeedback(event.getPlayer());
                event.getPlayer().sendMessage(Component.text("밀치기 저항이 적용되었습니다."));
            }
            case KNOCKBACK_STICK -> {
            }
            case ARROW -> {
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        boolean holdingKnockbackStick = shopService.readShopItem(attacker.getInventory().getItemInMainHand()) == ShopService.ShopItem.KNOCKBACK_STICK;
        if (event.isCancelled() && (!(event.getEntity() instanceof Player player)
                || !holdingKnockbackStick || !respawnProtectionService.isInvulnerable(player))) {
            return;
        }
        if (!(event.getEntity() instanceof Player victim)) {
            if (!holdingKnockbackStick) {
                return;
            }
            event.setDamage(0.0);
            consumeOne(attacker);
            return;
        }
        if (isSameTeam(attacker, victim)) {
            event.setCancelled(true);
            event.setDamage(0.0);
            if (holdingKnockbackStick) {
                messageService.send(attacker, MayuelaMessage.SHOP_KNOCKBACK_STICK_SAME_TEAM);
            }
            return;
        }
        if (!holdingKnockbackStick) {
            return;
        }
        if (respawnProtectionService.isFrozen(victim)) {
            event.setCancelled(true);
            return;
        }
        event.setDamage(0.0);
        consumeOne(attacker);
        if (respawnProtectionService.isInvulnerable(victim)) {
            event.setCancelled(true);
            int remainingSeconds = respawnProtectionService.remainingInvulnerabilitySeconds(victim);
            boolean bound = respawnProtectionService.countProtectedKnockbackHit(attacker);
            attacker.sendMessage(Component.text(victim.getName() + "님은 스폰 보호 중입니다. 남은 시간: " + remainingSeconds + "초"));
            victim.sendMessage(Component.text(attacker.getName() + "님이 스폰 보호 중 밀치기를 시도했습니다."));
            if (bound) {
                victim.sendMessage(Component.text(attacker.getName() + "님이 속박에 걸렸습니다."));
            }
            return;
        }
        if (hasKnockbackResistance(victim)) {
            event.setCancelled(true);
            matchStatsService.recordKnockbackHit(matchStatsService.elapsedSeconds(), attacker, victim, true);
            attacker.sendMessage(Component.text(victim.getName() + "님이 밀치기 저항 상태입니다."));
            return;
        }
        event.setCancelled(true);
        knockbackService.push(attacker, victim, configRepository.knockbackStickBlocks());
        MayuelaSound.KNOCKBACK_STICK_HIT.play(attacker);
        MayuelaSound.KNOCKBACK_STICK_HIT.play(victim);
        matchStatsService.recordKnockbackHit(matchStatsService.elapsedSeconds(), attacker, victim, false);
    }

    private boolean isSameTeam(Player attacker, Player victim) {
        GameTeam attackerTeam = teamService.findByPlayer(attacker.getUniqueId()).orElse(null);
        GameTeam victimTeam = teamService.findByPlayer(victim.getUniqueId()).orElse(null);
        return attackerTeam != null && victimTeam != null && attackerTeam.id().equals(victimTeam.id());
    }

    private void startBuffFeedback(Player player) {
        long token = System.nanoTime();
        buffFeedbackTokens.put(player.getUniqueId(), token);
        int durationSeconds = Math.max(remainingSeconds(knockbackResistanceExpiresAt.get(player.getUniqueId())),
                remainingSeconds(jumpBoostExpiresAt.get(player.getUniqueId())));
        for (int tick = 0; tick < durationSeconds; tick++) {
            int delayTicks = tick * 20;
            Bukkit.getScheduler().runTaskLater(JavaPlugin.getProvidingPlugin(getClass()), () -> {
                Long currentToken = buffFeedbackTokens.get(player.getUniqueId());
                if (!player.isOnline() || currentToken == null || currentToken != token) {
                    return;
                }
                Component actionBar = buffActionBar(player.getUniqueId());
                if (actionBar == null) {
                    buffFeedbackTokens.remove(player.getUniqueId());
                    return;
                }
                player.sendActionBar(actionBar);
                if (hasKnockbackResistance(player)) {
                    player.getWorld().spawnParticle(Particle.ENCHANT, player.getLocation().add(0, 1.0, 0), 12, 0.45, 0.55, 0.45, 0.02);
                }
            }, delayTicks);
        }
    }

    private Component buffActionBar(UUID playerId) {
        int knockbackResistanceSeconds = remainingSeconds(knockbackResistanceExpiresAt.get(playerId));
        int jumpBoostSeconds = remainingSeconds(jumpBoostExpiresAt.get(playerId));
        if (knockbackResistanceSeconds <= 0) {
            knockbackResistanceExpiresAt.remove(playerId);
        }
        if (jumpBoostSeconds <= 0) {
            jumpBoostExpiresAt.remove(playerId);
        }
        if (knockbackResistanceSeconds <= 0 && jumpBoostSeconds <= 0) {
            return null;
        }
        Component message = Component.empty();
        if (knockbackResistanceSeconds > 0) {
            message = message.append(Component.text("밀치기 저항 " + knockbackResistanceSeconds + "초", NamedTextColor.AQUA));
        }
        if (knockbackResistanceSeconds > 0 && jumpBoostSeconds > 0) {
            message = message.append(Component.text(" | ", NamedTextColor.GRAY));
        }
        if (jumpBoostSeconds > 0) {
            message = message.append(Component.text("점프강화 " + jumpBoostSeconds + "초", NamedTextColor.GREEN));
        }
        return message;
    }

    private int remainingSeconds(Long expiresAt) {
        if (expiresAt == null) {
            return 0;
        }
        long remainingMillis = expiresAt - System.currentTimeMillis();
        if (remainingMillis <= 0) {
            return 0;
        }
        return (int) ((remainingMillis + 999L) / 1000L);
    }

    private boolean hasKnockbackResistance(Player player) {
        Long expiresAt = knockbackResistanceExpiresAt.get(player.getUniqueId());
        if (expiresAt == null) {
            return false;
        }
        if (System.currentTimeMillis() > expiresAt) {
            knockbackResistanceExpiresAt.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    private void consumeOne(Player player) {
        int slot = player.getInventory().getHeldItemSlot();
        ItemStack itemStack = player.getInventory().getItem(slot);
        if (itemStack == null) {
            return;
        }
        if (itemStack.getAmount() <= 1) {
            player.getInventory().setItem(slot, null);
            return;
        }
        itemStack.setAmount(itemStack.getAmount() - 1);
        player.getInventory().setItem(slot, itemStack);
    }
}
