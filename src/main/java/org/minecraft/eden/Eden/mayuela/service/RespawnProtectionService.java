package org.minecraft.eden.Eden.mayuela.service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class RespawnProtectionService {

    private static final int KNOCKBACK_HIT_WINDOW_MILLIS = 2000;
    private static final int KNOCKBACK_HIT_THRESHOLD = 3;
    private static final int BIND_SECONDS = 3;

    private final Map<UUID, Long> frozenUntil = new HashMap<>();
    private final Map<UUID, Long> invulnerableUntil = new HashMap<>();
    private final Map<UUID, HitCounter> protectedTargetHits = new HashMap<>();

    public void freeze(Player player, int seconds) {
        frozenUntil.put(player.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
        invulnerableUntil.remove(player.getUniqueId());
    }

    public void startInvulnerability(Player player, int seconds) {
        frozenUntil.remove(player.getUniqueId());
        invulnerableUntil.put(player.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
    }

    public boolean isFrozen(Player player) {
        return isActive(frozenUntil, player.getUniqueId());
    }

    public boolean isInvulnerable(Player player) {
        return isActive(invulnerableUntil, player.getUniqueId());
    }

    public int remainingInvulnerabilitySeconds(Player player) {
        Long until = invulnerableUntil.get(player.getUniqueId());
        if (until == null) {
            return 0;
        }
        long remainingMillis = until - System.currentTimeMillis();
        if (remainingMillis <= 0L) {
            invulnerableUntil.remove(player.getUniqueId());
            return 0;
        }
        return (int) Math.ceil(remainingMillis / 1000.0);
    }

    public boolean countProtectedKnockbackHit(Player attacker) {
        long now = System.currentTimeMillis();
        HitCounter counter = protectedTargetHits.get(attacker.getUniqueId());
        if (counter == null || now - counter.windowStartedAt > KNOCKBACK_HIT_WINDOW_MILLIS) {
            protectedTargetHits.put(attacker.getUniqueId(), new HitCounter(now, 1));
            return false;
        }
        counter.count++;
        if (counter.count < KNOCKBACK_HIT_THRESHOLD) {
            return false;
        }
        protectedTargetHits.remove(attacker.getUniqueId());
        attacker.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, BIND_SECONDS * 20, 4, false, true));
        attacker.sendMessage(Component.text("스폰 보호 중인 플레이어를 연속으로 밀쳐 속박에 걸렸습니다."));
        return true;
    }

    public void clear(Player player) {
        UUID uuid = player.getUniqueId();
        frozenUntil.remove(uuid);
        invulnerableUntil.remove(uuid);
        protectedTargetHits.remove(uuid);
    }

    private boolean isActive(Map<UUID, Long> map, UUID uuid) {
        Long until = map.get(uuid);
        if (until == null) {
            return false;
        }
        if (System.currentTimeMillis() >= until) {
            map.remove(uuid);
            return false;
        }
        return true;
    }

    private static final class HitCounter {
        private final long windowStartedAt;
        private int count;

        private HitCounter(long windowStartedAt, int count) {
            this.windowStartedAt = windowStartedAt;
            this.count = count;
        }
    }
}
