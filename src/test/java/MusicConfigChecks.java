import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.minecraft.eden.Eden.mayuela.repository.ConfigRepository;

final class MusicConfigChecks {
    static void run() throws IOException {
        Path root = Files.createTempDirectory("mayuela-music-config-");
        try {
            Path fresh = Files.createDirectory(root.resolve("fresh"));
            ConfigRepository config = repository(fresh);
            config.load();
            Path settingsFile = fresh.resolve("music.yml");
            assertTrue(Files.isRegularFile(settingsFile));
            assertFalse(Files.exists(fresh.resolve("game settings.yml")));
            assertTrue(config.musicEnabled());
            assertEquals("mayuela:music.game", config.musicSound());
            assertEquals(180, config.musicDurationSeconds());
            assertEquals(.5f, config.musicVolume());
            assertTrue(Files.readString(settingsFile).contains("/game reload"));
            assertFalse(read(fresh, "game.yml").contains("music"));

            YamlConfiguration settings = read(fresh, "music.yml");
            settings.set("music.enabled", false);
            settings.set("music.sound", "custom:music.new_song");
            settings.set("music.duration-seconds", 243);
            settings.set("music.volume", .25);
            settings.save(settingsFile.toFile());
            config.load();
            assertFalse(config.musicEnabled());
            assertEquals("custom:music.new_song", config.musicSound());
            assertEquals(243, config.musicDurationSeconds());
            assertEquals(.25f, config.musicVolume());

            Path legacy = Files.createDirectory(root.resolve("legacy"));
            writeLegacy(legacy);
            ConfigRepository migrated = repository(legacy);
            migrated.load();
            assertFalse(migrated.musicEnabled());
            assertEquals("legacy:music.old_song", migrated.musicSound());
            assertEquals(87, migrated.musicDurationSeconds());
            assertEquals(.75f, migrated.musicVolume());
            assertFalse(read(legacy, "game.yml").contains("music"));
            assertEquals(6, read(legacy, "game.yml").getInt("game.max-players"));
            migrated.load();
            assertEquals("legacy:music.old_song", migrated.musicSound());

            Path mixed = Files.createDirectory(root.resolve("mixed"));
            writeLegacy(mixed);
            YamlConfiguration oldSettings = new YamlConfiguration();
            oldSettings.set("music.enabled", false);
            oldSettings.set("music.sound", "legacy:music.settings_song");
            oldSettings.set("music.duration-seconds", 120);
            oldSettings.save(mixed.resolve("game settings.yml").toFile());
            ConfigRepository renamed = repository(mixed);
            renamed.load();
            assertFalse(renamed.musicEnabled());
            assertEquals("legacy:music.settings_song", renamed.musicSound());
            assertEquals(120, renamed.musicDurationSeconds());
            assertEquals(.75f, renamed.musicVolume());
            assertEquals("legacy:music.settings_song", read(mixed, "music.yml").getString("music.sound"));
            assertEquals("legacy:music.settings_song", read(mixed, "game settings.yml").getString("music.sound"));
            writeLegacy(mixed);
            settings = new YamlConfiguration();
            settings.set("music.enabled", true);
            settings.set("music.sound", "custom:music.preferred");
            settings.save(mixed.resolve("music.yml").toFile());
            ConfigRepository preferred = repository(mixed);
            preferred.load();
            assertTrue(preferred.musicEnabled());
            assertEquals("custom:music.preferred", preferred.musicSound());
            assertEquals(120, preferred.musicDurationSeconds());
            assertEquals(.75f, preferred.musicVolume());

            Path blocked = Files.createDirectory(root.resolve("blocked"));
            writeLegacy(blocked);
            oldSettings.save(blocked.resolve("game settings.yml").toFile());
            Files.createDirectory(blocked.resolve("music.yml"));
            repository(blocked).load();
            assertEquals("legacy:music.old_song", read(blocked, "game.yml").getString("music.sound"));
            assertEquals("legacy:music.settings_song", read(blocked, "game settings.yml").getString("music.sound"));
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
        System.out.println("Music config: creation, reload, legacy migrations, precedence, failed-save preservation passed.");
    }

    private static ConfigRepository repository(Path folder) {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(folder.toFile());
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        return new ConfigRepository(plugin);
    }

    private static YamlConfiguration read(Path folder, String name) {
        return YamlConfiguration.loadConfiguration(folder.resolve(name).toFile());
    }

    private static void writeLegacy(Path folder) throws IOException {
        YamlConfiguration legacy = new YamlConfiguration();
        legacy.set("game.max-players", 6);
        legacy.set("music.enabled", false);
        legacy.set("music.sound", "legacy:music.old_song");
        legacy.set("music.duration-seconds", 87);
        legacy.set("music.volume", .75);
        legacy.save(folder.resolve("game.yml").toFile());
    }
}
