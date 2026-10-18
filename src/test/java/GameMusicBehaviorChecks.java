import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.minecraft.eden.Eden.mayuela.model.GamePlayer;
import org.minecraft.eden.Eden.mayuela.repository.ConfigRepository;
import org.minecraft.eden.Eden.mayuela.service.GameMusicService;
import org.minecraft.eden.Eden.mayuela.service.GamePlayerService;

final class GameMusicBehaviorChecks {
    static void run() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            JavaPlugin plugin = mock(JavaPlugin.class);
            when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
            ConfigRepository config = mock(ConfigRepository.class);
            when(config.musicEnabled()).thenReturn(true);
            when(config.musicSound()).thenReturn("mayuela:music.game");
            when(config.musicDurationSeconds()).thenReturn(180);
            when(config.musicVolume()).thenReturn(.5f);
            GamePlayerService players = mock(GamePlayerService.class);
            UUID id = UUID.randomUUID();
            GamePlayer participant = new GamePlayer(id, "Listener");
            when(players.all()).thenReturn(List.of(participant));
            when(players.find(id)).thenReturn(Optional.of(participant));
            Player player = mock(Player.class);
            when(player.getUniqueId()).thenReturn(id);
            when(player.isOnline()).thenReturn(true);
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
            BukkitScheduler scheduler = mock(BukkitScheduler.class);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            BukkitTask task = mock(BukkitTask.class);
            Runnable[] tick = new Runnable[1];
            when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(20L), eq(20L))).thenAnswer(call -> {
                tick[0] = call.getArgument(1);
                return task;
            });
            Clock clock = mock(Clock.class);
            GameMusicService music = new GameMusicService(plugin, config, players, clock);
            music.playFor(player);
            verify(player, never()).playSound(any(Sound.class), any(Sound.Emitter.class));
            music.start();
            var sound = org.mockito.ArgumentCaptor.forClass(Sound.class);
            verify(player).playSound(sound.capture(), eq(Sound.Emitter.self()));
            assertEquals("mayuela:music.game", sound.getValue().name().asString());
            assertEquals(Sound.Source.MUSIC, sound.getValue().source());
            when(clock.millis()).thenReturn(179999L);
            tick[0].run();
            verify(player, times(1)).playSound(any(Sound.class), any(Sound.Emitter.class));
            when(clock.millis()).thenReturn(180000L);
            tick[0].run();
            verify(player, times(2)).playSound(any(Sound.class), any(Sound.Emitter.class));
            verify(player).stopSound(sound.getValue());
            music.playFor(player);
            verify(player, times(3)).playSound(any(Sound.class), any(Sound.Emitter.class));
            verify(player, times(2)).stopSound(sound.getValue());
            Player outsider = mock(Player.class);
            when(outsider.getUniqueId()).thenReturn(UUID.randomUUID());
            when(outsider.isOnline()).thenReturn(true);
            music.playFor(outsider);
            verify(outsider, never()).playSound(any(Sound.class), any(Sound.Emitter.class));
            music.stopFor(player);
            when(player.isOnline()).thenReturn(false);
            when(clock.millis()).thenReturn(999999L);
            tick[0].run();
            verify(player, times(3)).playSound(any(Sound.class), any(Sound.Emitter.class));
            when(player.isOnline()).thenReturn(true);
            music.playFor(player);
            verify(player, times(4)).playSound(any(Sound.class), any(Sound.Emitter.class));
            music.stop();
            verify(task).cancel();
            music.playFor(player);
            verify(player, times(4)).playSound(any(Sound.class), any(Sound.Emitter.class));
            when(config.musicEnabled()).thenReturn(false);
            music.start();
            verify(scheduler, times(1)).runTaskTimer(eq(plugin), any(Runnable.class), eq(20L), eq(20L));
            when(config.musicEnabled()).thenReturn(true);
            when(config.musicSound()).thenReturn("Invalid sound name!");
            music.start();
            verify(scheduler, times(1)).runTaskTimer(eq(plugin), any(Runnable.class), eq(20L), eq(20L));
        }
        System.out.println("Music: start, repeat, restart, membership, quit, stop, disabled, invalid key passed.");
    }
}
