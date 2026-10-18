import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.minecraft.eden.Eden.mayuela.listener.ShopItemListener;
import org.minecraft.eden.Eden.mayuela.message.MessageService;
import org.minecraft.eden.Eden.mayuela.model.GameTeam;
import org.minecraft.eden.Eden.mayuela.model.PlayerColor;
import org.minecraft.eden.Eden.mayuela.repository.ConfigRepository;
import org.minecraft.eden.Eden.mayuela.service.*;

final class ShopKnockbackChecks {
    static void run() {
        for (String scenario : new String[]{"hit", "cancelled", "teammate", "frozen", "protected"}) {
            Player attacker = mock(Player.class);
            Player victim = mock(Player.class);
            when(attacker.getUniqueId()).thenReturn(UUID.randomUUID());
            when(victim.getUniqueId()).thenReturn(UUID.randomUUID());
            when(victim.getName()).thenReturn("Victim");
            when(attacker.getName()).thenReturn("Attacker");
            PlayerInventory inventory = mock(PlayerInventory.class);
            ItemStack stick = mock(ItemStack.class);
            when(stick.getAmount()).thenReturn(2);
            when(attacker.getInventory()).thenReturn(inventory);
            when(inventory.getItemInMainHand()).thenReturn(stick);
            when(inventory.getItem(0)).thenReturn(stick);
            ShopService shop = mock(ShopService.class);
            when(shop.readShopItem(stick)).thenReturn(ShopService.ShopItem.KNOCKBACK_STICK);
            ConfigRepository config = mock(ConfigRepository.class);
            when(config.knockbackStickBlocks()).thenReturn(15);
            KnockbackService knockback = mock(KnockbackService.class);
            doThrow(new PushStarted()).when(knockback).push(attacker, victim, 15);
            TeamService teams = mock(TeamService.class);
            if (scenario.equals("teammate")) {
                GameTeam team = new GameTeam("red", PlayerColor.defaults().getFirst());
                when(teams.findByPlayer(any())).thenReturn(Optional.of(team));
            }
            RespawnProtectionService protection = mock(RespawnProtectionService.class);
            when(protection.isFrozen(victim)).thenReturn(scenario.equals("frozen"));
            when(protection.isInvulnerable(victim)).thenReturn(scenario.equals("protected"));
            ShopItemListener listener = new ShopItemListener(config, shop, mock(MatchStatsService.class),
                    protection, teams, mock(MessageService.class), knockback);
            EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
            when(event.getDamager()).thenReturn(attacker);
            when(event.getEntity()).thenReturn(victim);
            when(event.isCancelled()).thenReturn(scenario.equals("cancelled") || scenario.equals("protected"));
            if (scenario.equals("hit")) {
                assertThrows(PushStarted.class, () -> listener.onDamage(event));
                verify(event).setCancelled(true);
                verify(stick).setAmount(1);
            } else {
                listener.onDamage(event);
                verifyNoInteractions(knockback);
                if (!scenario.equals("protected")) {
                    verify(stick, never()).setAmount(anyInt());
                }
            }
        }
        System.out.println("Shop knockback: 5 scenarios passed.");
    }
    private static final class PushStarted extends RuntimeException {}
}
