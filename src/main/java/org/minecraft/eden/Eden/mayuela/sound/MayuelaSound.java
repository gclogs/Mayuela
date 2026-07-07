package org.minecraft.eden.Eden.mayuela.sound;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

public enum MayuelaSound {

    DEATH(Sound.ENTITY_PLAYER_DEATH, 1.0f, 0.9f),
    RESPAWN_COUNTDOWN(Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.2f),
    SHOP_OPEN(Sound.BLOCK_CHEST_OPEN, 1.0f, 1.1f),
    SHOP_PURCHASE_FAILED(Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.7f),
    SHOP_PURCHASED(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.3f),
    COIN_PICKUP(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.7f),
    KNOCKBACK_STICK_HIT(Sound.BLOCK_NOTE_BLOCK_BANJO, 1.0f, 1.5f),
    KNOCKBACK_RESISTANCE_USED(Sound.ITEM_SHIELD_BLOCK, 1.0f, 1.2f),
    GAME_WIN(Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

    private final Sound sound;
    private final float volume;
    private final float pitch;

    MayuelaSound(Sound sound, float volume, float pitch) {
        this.sound = sound;
        this.volume = volume;
        this.pitch = pitch;
    }

    public void play(Player player) {
        player.playSound(player.getLocation(), sound, volume, pitch);
    }
}
