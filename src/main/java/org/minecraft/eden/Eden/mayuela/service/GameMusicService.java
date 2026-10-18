package org.minecraft.eden.Eden.mayuela.service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.time.Clock;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.minecraft.eden.Eden.mayuela.repository.ConfigRepository;

public final class GameMusicService {
    private final JavaPlugin plugin;
    private final ConfigRepository configRepository;
    private final GamePlayerService players;
    private final Clock clock;
    private final Map<UUID, Long> nextPlayback = new HashMap<>();
    private BukkitTask task;
    private Sound sound;
    private long durationMillis;

    public GameMusicService(JavaPlugin plugin, ConfigRepository configRepository, GamePlayerService players) {
        this(plugin, configRepository, players, Clock.systemUTC());
    }

    public GameMusicService(JavaPlugin plugin, ConfigRepository configRepository, GamePlayerService players, Clock clock) {
        this.plugin = plugin;
        this.configRepository = configRepository;
        this.players = players;
        this.clock = clock;
    }

    public void start() {
        stop();
        if (!configRepository.musicEnabled()) {
            return;
        }
        String key = configRepository.musicSound();
        if (!Key.parseable(key)) {
            plugin.getLogger().warning("Invalid music.sound in music.yml: " + key);
            return;
        }
        sound = Sound.sound(Key.key(key), Sound.Source.MUSIC, configRepository.musicVolume(), 1.0f);
        durationMillis = configRepository.musicDurationSeconds() * 1000L;
        for (var participant : players.all()) {
            Player player = Bukkit.getPlayer(participant.uuid());
            if (player != null) {
                playFor(player);
            }
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void tick() {
        long now = clock.millis();
        for (var participant : players.all()) {
            Player player = Bukkit.getPlayer(participant.uuid());
            if (player != null && now >= nextPlayback.getOrDefault(participant.uuid(), 0L)) {
                playFor(player);
            }
        }
    }

    public void playFor(Player player) {
        if (sound == null || !player.isOnline() || players.find(player.getUniqueId()).isEmpty()) {
            return;
        }
        // A pack reload or respawn may restart playback; never overlap the previous copy.
        stopFor(player);
        player.playSound(sound, Sound.Emitter.self());
        nextPlayback.put(player.getUniqueId(), clock.millis() + durationMillis);
    }

    public void stopFor(Player player) {
        if (nextPlayback.remove(player.getUniqueId()) != null && sound != null) {
            player.stopSound(sound);
        }
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (UUID id : java.util.List.copyOf(nextPlayback.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                stopFor(player);
            }
        }
        nextPlayback.clear();
        sound = null;
    }
}
