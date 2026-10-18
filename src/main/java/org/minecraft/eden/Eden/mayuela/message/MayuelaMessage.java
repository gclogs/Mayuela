package org.minecraft.eden.Eden.mayuela.message;

public enum MayuelaMessage {

    GAME_STARTED("game.started"),
    PLAYER_ONLY("common.player-only"),
    NO_PERMISSION("common.no-permission"),
    LOBBY_REGISTERED("lobby.registered"),
    JOIN_UNAVAILABLE_NOW("join.unavailable-now"),
    ALREADY_JOINED("join.already-joined", "count"),
    JOIN_READY("join.ready"),
    JOIN_FULL("join.full"),
    PARTICIPANT_JOINED("join.participant-joined", "player", "count", "max"),
    LEAVE_UNAVAILABLE_WHILE_PLAYING("leave.unavailable-while-playing"),
    LEAVE_NOT_PARTICIPATING("leave.not-participating"),
    LEAVE_READY_CANCELLED("leave.ready-cancelled", "count"),
    PARTICIPANT_LEFT_READY("leave.participant-left-ready", "player", "count"),
    SHOP_UNAVAILABLE("shop.unavailable"),
    SHOP_KNOCKBACK_STICK_LIMIT("shop.knockback-stick-limit", "limit"),
    SHOP_KNOCKBACK_STICK_SAME_TEAM("shop.knockback-stick-same-team"),
    SHOP_NOT_ENOUGH_COINS("shop.not-enough-coins", "price"),
    SHOP_PURCHASED("shop.purchased", "item", "price"),
    BEACON_GUI_BLOCKED("altar.beacon-gui-blocked"),
    READY_BLOCK_PLACE_BLOCKED("altar.ready-block-place-blocked"),
    PLAYING_DIRECT_BLOCK_PLACE_BLOCKED("altar.playing-direct-block-place-blocked"),
    ALTAR_MUST_BE_ABOVE_BEACON("altar.must-be-above-beacon"),
    ALTAR_CAPTURE_FAILED("altar.capture-failed", "reason"),
    ALTAR_BLOCKED_ABOVE("altar.blocked-above"),
    ALTAR_BLOCK_BROKEN("altar.block-broken"),
    RESPAWN_FREEZE_START("respawn.freeze-start", "seconds"),
    RESPAWN_CAN_MOVE_NOW("respawn.can-move-now"),
    BOUNTY_SAME_TEAM_NO_REWARD("bounty.same-team-no-reward"),
    BOUNTY_ALREADY_REWARDED("bounty.already-rewarded", "victim"),
    BOUNTY_REWARD_INVENTORY_FULL("bounty.reward-inventory-full"),
    BOUNTY_REWARD_GRANTED("bounty.reward-granted", "amount", "victim"),
    BOUNTY_REWARD_PAID_TO_VICTIM("bounty.reward-paid-to-victim"),
    PARTICIPANT_QUIT_RECOVERABLE("session.participant-quit-recoverable", "player", "count"),
    PARTICIPANT_QUIT_REMOVED("session.participant-quit-removed", "player", "count"),
    COIN_INVENTORY_FULL("coin.inventory-full"),
    COIN_PICKED_UP("coin.picked-up", "amount"),
    GAME_READY_ENTERED("game.ready-entered", "mode", "color"),
    STRENGTH_GRANTED("buff.strength-granted");

    private final String path;
    private final String[] placeholders;

    MayuelaMessage(String path, String... placeholders) {
        this.path = path;
        this.placeholders = placeholders;
    }

    public String path() {
        return path;
    }

    public String[] placeholders() {
        return placeholders.clone();
    }
}
