package org.minecraft.eden.Eden.mayuela;

import org.bukkit.plugin.java.JavaPlugin;
import org.minecraft.eden.Eden.mayuela.command.GameCommand;
import org.minecraft.eden.Eden.mayuela.command.JoinCommand;
import org.minecraft.eden.Eden.mayuela.command.LeaveCommand;
import org.minecraft.eden.Eden.mayuela.command.ShopCommand;
import org.minecraft.eden.Eden.mayuela.listener.AltarBlockListener;
import org.minecraft.eden.Eden.mayuela.listener.CoinListener;
import org.minecraft.eden.Eden.mayuela.listener.ItemLockListener;
import org.minecraft.eden.Eden.mayuela.listener.PlayerLifecycleListener;
import org.minecraft.eden.Eden.mayuela.listener.ShopItemListener;
import org.minecraft.eden.Eden.mayuela.listener.ShopListener;
import org.minecraft.eden.Eden.mayuela.message.MessageService;
import org.minecraft.eden.Eden.mayuela.message.YamlMessageService;
import org.minecraft.eden.Eden.mayuela.repository.ConfigRepository;
import org.minecraft.eden.Eden.mayuela.service.AltarService;
import org.minecraft.eden.Eden.mayuela.service.CoinService;
import org.minecraft.eden.Eden.mayuela.service.DisplayService;
import org.minecraft.eden.Eden.mayuela.service.GamePlayerService;
import org.minecraft.eden.Eden.mayuela.service.GameSessionService;
import org.minecraft.eden.Eden.mayuela.service.InventoryService;
import org.minecraft.eden.Eden.mayuela.service.MatchStatsService;
import org.minecraft.eden.Eden.mayuela.service.MapService;
import org.minecraft.eden.Eden.mayuela.service.PdcMonitorService;
import org.minecraft.eden.Eden.mayuela.service.RespawnProtectionService;
import org.minecraft.eden.Eden.mayuela.service.ScoreboardService;
import org.minecraft.eden.Eden.mayuela.service.ShopService;
import org.minecraft.eden.Eden.mayuela.service.TeamService;
import org.minecraft.eden.Eden.mayuela.service.KnockbackService;
import org.minecraft.eden.Eden.mayuela.service.GameMusicService;

public final class Mayuela extends JavaPlugin {

    private GameSessionService gameSessionService;
    private DisplayService displayService;

    @Override
    public void onEnable() {
        ConfigRepository configRepository = new ConfigRepository(this);
        configRepository.load();
        MessageService messageService = new YamlMessageService(this);
        messageService.reload();

        GamePlayerService gamePlayerService = new GamePlayerService();
        TeamService teamService = new TeamService();
        InventoryService inventoryService = new InventoryService(configRepository);
        ScoreboardService scoreboardService = new ScoreboardService();
        displayService = new DisplayService(this);
        MapService mapService = new MapService(configRepository);
        MatchStatsService matchStatsService = new MatchStatsService(this, configRepository, gamePlayerService, teamService);
        RespawnProtectionService respawnProtectionService = new RespawnProtectionService();
        KnockbackService knockbackService = new KnockbackService(this);
        GameMusicService musicService = new GameMusicService(this, configRepository, gamePlayerService);
        AltarService altarService = new AltarService(configRepository, gamePlayerService, teamService, inventoryService, scoreboardService, displayService, matchStatsService, messageService);
        CoinService coinService = new CoinService(this, configRepository, gamePlayerService, teamService, scoreboardService, matchStatsService, messageService);
        ShopService shopService = new ShopService(configRepository, coinService, matchStatsService, messageService);
        PdcMonitorService pdcMonitorService = new PdcMonitorService();
        gameSessionService = new GameSessionService(this, configRepository, gamePlayerService, teamService, altarService, inventoryService, scoreboardService, displayService, coinService, matchStatsService, messageService, knockbackService, musicService);

        JoinCommand joinCommand = new JoinCommand(configRepository, gamePlayerService, teamService, scoreboardService, gameSessionService, messageService);
        LeaveCommand leaveCommand = new LeaveCommand(gamePlayerService, teamService, scoreboardService, gameSessionService, messageService);
        ShopCommand shopCommand = new ShopCommand(gamePlayerService, gameSessionService, shopService, messageService);
        GameCommand gameCommand = new GameCommand(configRepository, mapService, altarService, gameSessionService, coinService, gamePlayerService, teamService, scoreboardService, pdcMonitorService, messageService);

        getCommand("입장").setExecutor(joinCommand);
        getCommand("입장").setTabCompleter(joinCommand);
        getCommand("퇴장").setExecutor(leaveCommand);
        getCommand("상점열기").setExecutor(shopCommand);
        getCommand("상점열기").setExecutor(shopCommand);
        getCommand("game").setExecutor(gameCommand);
        getCommand("game").setTabCompleter(gameCommand);

        getServer().getPluginManager().registerEvents(new ItemLockListener(inventoryService, gamePlayerService, gameSessionService, coinService, shopService), this);
        getServer().getPluginManager().registerEvents(new AltarBlockListener(altarService, gameSessionService, gamePlayerService, inventoryService, teamService, messageService), this);
        getServer().getPluginManager().registerEvents(new CoinListener(coinService), this);
        getServer().getPluginManager().registerEvents(new ShopListener(shopService), this);
        getServer().getPluginManager().registerEvents(new ShopItemListener(configRepository, shopService, matchStatsService, respawnProtectionService, teamService, messageService, knockbackService), this);
        getServer().getPluginManager().registerEvents(new PlayerLifecycleListener(configRepository, gamePlayerService, gameSessionService, teamService, altarService, inventoryService, displayService, scoreboardService, coinService, matchStatsService, respawnProtectionService, messageService, knockbackService, musicService), this);

        getLogger().info("Mayuela enabled.");
    }

    @Override
    public void onDisable() {
        if (gameSessionService != null) {
            gameSessionService.forceStop();
        }
        if (displayService != null) {
            displayService.removeAll();
        }
    }
}
