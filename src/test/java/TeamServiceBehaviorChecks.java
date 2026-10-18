import java.util.List;
import java.util.UUID;
import org.minecraft.eden.Eden.mayuela.model.GamePlayer;
import org.minecraft.eden.Eden.mayuela.model.GameTeam;
import org.minecraft.eden.Eden.mayuela.model.PlayerColor;
import org.minecraft.eden.Eden.mayuela.repository.ConfigRepository;
import org.minecraft.eden.Eden.mayuela.service.MatchStatsService;
import org.minecraft.eden.Eden.mayuela.service.TeamService;

public final class TeamServiceBehaviorChecks {

    public static void main(String[] args) {
        teamModeFormsTwoPlayerTeamsOnly();
        teamModeRejectsOddPlayerCount();
        soloModeFormsOnePlayerTeams();
        soloModeRejectsSinglePlayer();
        coinRulesPreserveOddPlayerCounts();
        statsExcludesConfiguredOutlierName();
        flagItemsAdderIdsUseColorSpecificDefaults();
        shopItemItemsAdderIdsUseItemSpecificDefaults();
        systemItemItemsAdderIdsUseItemSpecificDefaults();
        knockbackStickLimitRejectsInvalidValues();
        teamRuntimeResetClearsBountyState();
        System.out.println("TeamService behavior checks passed.");
    }

    private static void teamModeFormsTwoPlayerTeamsOnly() {
        TeamService teamService = new TeamService();

        check(teamService.formTeams(players(4), TeamService.MatchMode.TEAM), "team mode should accept 4 players");
        check(teamService.all().size() == 2, "team mode should create 2 teams for 4 players");
        check(teamService.all().stream().allMatch(team -> team.members().size() == 2), "team mode teams should have 2 members");
    }

    private static void teamModeRejectsOddPlayerCount() {
        TeamService teamService = new TeamService();

        check(!teamService.formTeams(players(5), TeamService.MatchMode.TEAM), "team mode should reject odd player counts");
    }

    private static void soloModeFormsOnePlayerTeams() {
        TeamService teamService = new TeamService();

        check(teamService.formTeams(players(5), TeamService.MatchMode.SOLO), "solo mode should accept 5 players");
        check(teamService.all().size() == 5, "solo mode should create one team per player");
        check(teamService.all().stream().allMatch(team -> team.members().size() == 1), "solo mode teams should have 1 member");
    }

    private static void soloModeRejectsSinglePlayer() {
        TeamService teamService = new TeamService();

        check(!teamService.formTeams(players(1), TeamService.MatchMode.SOLO), "solo mode should reject 1 player");
        check(teamService.all().isEmpty(), "solo mode should not create teams for 1 player");
    }

    private static void coinRulesPreserveOddPlayerCounts() {
        check(ConfigRepository.normalizedCoinRulePlayerCount(5) == 5, "5-player matches should use coin.rules.5");
        check(ConfigRepository.normalizedCoinRulePlayerCount(7) == 7, "7-player matches should use coin.rules.7");
        check(ConfigRepository.normalizedCoinRulePlayerCount(11) == 10, "coin rules should cap at 10 players");
    }

    private static void statsExcludesConfiguredOutlierName() {
        check(MatchStatsService.isStatsExcludedPlayerName("Gaensta", List.of("Gaensta")), "Gaensta should be excluded from stats");
        check(MatchStatsService.isStatsExcludedPlayerName("gaensta", List.of("Gaensta")), "stat exclusion should be case-insensitive");
        check(!MatchStatsService.isStatsExcludedPlayerName("RedPlayer", List.of("Gaensta")), "normal players should remain in stats");
    }

    private static void flagItemsAdderIdsUseColorSpecificDefaults() {
        check(ConfigRepository.defaultFlagItemsAdderId("RED").equals("mayuela:flag_red"), "red flag should have an ItemsAdder id");
        check(ConfigRepository.defaultFlagItemsAdderId("LIGHT_BLUE").equals("mayuela:flag_light_blue"), "light blue flag should keep the full color id");
    }

    private static void shopItemItemsAdderIdsUseItemSpecificDefaults() {
        check(ConfigRepository.defaultShopItemItemsAdderId("knockback_stick").equals("mayuela:knockback_stick"), "knockback stick should have an ItemsAdder id");
        check(ConfigRepository.defaultShopItemItemsAdderId("jump_boost").equals("mayuela:jump_boost"), "jump boost should have an ItemsAdder id");
    }

    private static void systemItemItemsAdderIdsUseItemSpecificDefaults() {
        check(ConfigRepository.defaultSystemItemItemsAdderId("offhand_lock").equals("mayuela:offhand_lock"), "offhand lock should have an ItemsAdder id");
    }

    private static void knockbackStickLimitRejectsInvalidValues() {
        check(ConfigRepository.normalizedKnockbackStickMaxHeld(0) == 1, "stick limit should be at least 1");
        check(ConfigRepository.normalizedKnockbackStickMaxHeld(3) == 3, "stick limit should preserve configured values");
    }

    private static void teamRuntimeResetClearsBountyState() {
        GameTeam team = new GameTeam("team_1", PlayerColor.defaults().get(0));
        team.capturedAltars().add("altar_1");
        team.capturedAltars().add("altar_2");
        team.capturedAltars().add("altar_3");
        team.setBountyWanted(true);

        team.resetGameState();

        check(!team.bountyWanted(), "team reset should clear bounty state");
        check(team.capturedAltars().isEmpty(), "team reset should clear captured altars");
    }

    private static List<GamePlayer> players(int count) {
        return java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(index -> new GamePlayer(UUID.nameUUIDFromBytes(("player-" + index).getBytes()), "Player" + index))
                .toList();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
