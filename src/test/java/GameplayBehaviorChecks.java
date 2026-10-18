public final class GameplayBehaviorChecks {
    public static void main(String[] args) throws Exception {
        PlayerDeathBountyChecks.run();
        KnockbackBehaviorChecks.run();
        ShopKnockbackChecks.run();
        GameMusicBehaviorChecks.run();
        MusicConfigChecks.run();
        System.out.println("Gameplay behavior checks passed.");
    }
}
