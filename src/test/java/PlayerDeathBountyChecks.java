import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.damage.DamageSource;
import org.minecraft.eden.Eden.mayuela.listener.PlayerLifecycleListener;
import org.minecraft.eden.Eden.mayuela.message.MessageService;
import org.minecraft.eden.Eden.mayuela.model.*;
import org.minecraft.eden.Eden.mayuela.repository.ConfigRepository;
import org.minecraft.eden.Eden.mayuela.service.*;

class PlayerDeathBountyChecks {
    static void run() {
        for (String cause : List.of("suicide", "stale-killer", "self", "teammate", "outsider", "mob", "melee", "arrow", "full-inventory")) {
            checkDeath(cause);
        }
        wantedStatusSurvivesRankingChange();
        System.out.println("Bounty: 10 scenarios passed.");
    }

    private static void checkDeath(String cause) {
        Player victim = mock(Player.class);
        Player attacker = mock(Player.class);
        when(attacker.getUniqueId()).thenReturn(UUID.randomUUID());
        when(attacker.getName()).thenReturn("Attacker");
        UUID victimId = UUID.randomUUID();
        when(victim.getUniqueId()).thenReturn(victimId);
        when(victim.getName()).thenReturn("Victim");
        GamePlayer participant = new GamePlayer(victimId, "Victim");
        GameTeam team = new GameTeam("red", PlayerColor.defaults().getFirst());
        team.setBountyWanted(true);
        GamePlayerService players = mock(GamePlayerService.class);
        when(players.find(victimId)).thenReturn(Optional.of(participant));
        when(players.all()).thenReturn(List.of(participant));
        TeamService teams = mock(TeamService.class);
        when(teams.findByPlayer(victimId)).thenReturn(Optional.of(team));
        GameTeam opponent = new GameTeam("blue", PlayerColor.defaults().get(1));
        when(teams.findByPlayer(attacker.getUniqueId())).thenReturn(cause.equals("outsider")
                ? Optional.empty() : Optional.of(cause.equals("teammate") ? team : opponent));
        GameSessionService session = mock(GameSessionService.class);
        when(session.phase()).thenReturn(GamePhase.PLAYING);
        AltarService altars = mock(AltarService.class);
        MessageService messages = mock(MessageService.class);
        // Stop after the death rule runs, before the live-server respawn scheduling.
        doThrow(new DeathNoticeReached()).when(messages).sendComponent(eq(victim), any());
        ConfigRepository config = mock(ConfigRepository.class);
        when(config.bountyRewardCoins()).thenReturn(8);
        CoinService coins = mock(CoinService.class);
        when(coins.grantCoins(attacker, 8)).thenReturn(!cause.equals("full-inventory"));
        PlayerLifecycleListener listener = new PlayerLifecycleListener(config,
                players, session, teams, altars, mock(InventoryService.class), mock(DisplayService.class),
                mock(ScoreboardService.class), coins, mock(MatchStatsService.class),
                mock(RespawnProtectionService.class), messages, mock(KnockbackService.class), mock(GameMusicService.class));
        PlayerDeathEvent event = mock(PlayerDeathEvent.class);
        when(event.getPlayer()).thenReturn(victim);
        DamageSource source = mock(DamageSource.class);
        when(event.getDamageSource()).thenReturn(source);
        switch (cause) {
            case "suicide" -> { }
            case "stale-killer" -> when(victim.getKiller()).thenReturn(attacker);
            case "self" -> when(source.getCausingEntity()).thenReturn(victim);
            case "mob" -> when(source.getCausingEntity()).thenReturn(mock(org.bukkit.entity.Zombie.class));
            default -> when(source.getCausingEntity()).thenReturn(attacker);
        }
        if (cause.equals("arrow")) {
            when(source.getDirectEntity()).thenReturn(mock(org.bukkit.entity.Arrow.class));
        }
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(victimId)).thenReturn(victim);
            assertThrows(DeathNoticeReached.class, () -> listener.onDeath(event));
        }
        boolean eligible = List.of("melee", "arrow", "full-inventory").contains(cause);
        verify(altars, times(eligible ? 1 : 0)).releaseBountyAfterDeath(eq(team), eq(victim), anyLong());
        verify(coins, times(eligible ? 1 : 0)).grantCoins(attacker, 8);
        assertEquals(eligible && !cause.equals("full-inventory"), team.bountyRewardClaimed(victimId), cause);
    }

    private static void wantedStatusSurvivesRankingChange() {
        ConfigRepository config = mock(ConfigRepository.class);
        when(config.strengthDelaySeconds()).thenReturn(Integer.MAX_VALUE);
        GameTeam wanted = new GameTeam("red", PlayerColor.defaults().getFirst());
        wanted.setBountyWanted(true);
        wanted.increaseActivation(20);
        GameTeam leader = new GameTeam("blue", PlayerColor.defaults().get(1));
        leader.increaseActivation(30);
        TeamService teams = mock(TeamService.class);
        when(teams.all()).thenReturn(List.of(wanted, leader));
        AltarService altars = new AltarService(config, new GamePlayerService(), teams,
                mock(InventoryService.class), mock(ScoreboardService.class), mock(DisplayService.class),
                mock(MatchStatsService.class), mock(MessageService.class));
        altars.tickActivation(0.04, 1);
        assertTrue(wanted.bountyWanted(), "losing the lead must not release a wanted team");
        altars.resetRuntimeState();
        assertFalse(wanted.bountyWanted(), "match reset still clears bounty");
    }

    private static final class DeathNoticeReached extends RuntimeException {}
}
