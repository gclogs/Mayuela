import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.VoxelShape;
import org.minecraft.eden.Eden.mayuela.service.KnockbackService;

final class KnockbackBehaviorChecks {
    static void run() {
        for (int blocks : new int[]{1, 2, 15, 31}) {
            try (Fixture f = new Fixture()) {
                f.push(blocks);
                f.ticks(60);
                assertEquals(blocks, f.position.getX() - 0.5, 0.00001, "exact horizontal distance");
                verify(f.task).cancel();
            }
        }
        try (Fixture f = new Fixture()) {
            when(f.attacker.getLocation()).thenReturn(new Location(f.world, -1, 80, -1));
            f.push(15);
            f.ticks(40);
            assertEquals(15, Math.hypot(f.position.getX() - .5, f.position.getZ() - .5), .00001);
            assertEquals(64, f.position.getY(), .00001);
        }
        for (int y : new int[]{64, 65}) {
            try (Fixture f = new Fixture()) {
                f.solid(4, y, 0, new BoundingBox(0, 0, 0, 1, 1, 1));
                f.push(15);
                f.ticks(40);
                assertEquals(3.7, f.position.getX(), .00001, "feet/head wall collision");
            }
        }
        try (Fixture f = new Fixture()) {
            f.solid(4, 64, 0, new BoundingBox(.4375, 0, 0, .5625, 1, 1));
            f.push(15);
            f.ticks(40);
            assertEquals(4.1375, f.position.getX(), .00001, "thin pane cannot be skipped");
        }
        try (Fixture f = new Fixture()) {
            f.solid(4, 63, 0, new BoundingBox(.375, 0, .375, .625, 1.5, .625));
            f.push(15);
            f.ticks(40);
            assertEquals(4.075, f.position.getX(), .00001, "fence extends above its block");
        }
        try (Fixture f = new Fixture()) {
            f.push(15);
            f.ticks(2);
            double stoppedAt = f.position.getX();
            f.service.stop();
            f.ticks(30);
            assertEquals(stoppedAt, f.position.getX());
        }
        try (Fixture f = new Fixture()) {
            f.push(15);
            f.service.clear(f.victim);
            f.ticks(30);
            assertEquals(.5, f.position.getX());
        }
        try (Fixture f = new Fixture()) {
            f.push(15);
            f.ticks(2);
            f.push(2);
            f.ticks(40);
            assertEquals(4.0, f.position.getX(), .00001, "new hit replaces an unfinished push");
        }
        try (Fixture f = new Fixture()) {
            when(f.world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);
            f.push(15);
            f.ticks(40);
            assertEquals(.5, f.position.getX());
        }
        try (Fixture f = new Fixture()) {
            when(f.victim.teleport(any(Location.class))).thenReturn(false);
            f.push(15);
            f.ticks(40);
            verify(f.victim, times(1)).teleport(any(Location.class));
        }
        System.out.println("Knockback: 14 scenarios passed.");
    }

    private static final class Fixture implements AutoCloseable {
        final World world = mock(World.class);
        final Player attacker = mock(Player.class);
        final Player victim = mock(Player.class);
        final BukkitTask task = mock(BukkitTask.class);
        final Map<String, Block> blocks = new HashMap<>();
        final org.mockito.MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
        final KnockbackService service;
        Location position = new Location(world, .5, 64, .5);
        Runnable tick;

        Fixture() {
            BukkitScheduler scheduler = mock(BukkitScheduler.class);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            when(scheduler.runTaskTimer(any(), any(Runnable.class), anyLong(), anyLong())).thenAnswer(call -> {
                tick = call.getArgument(1);
                return task;
            });
            when(world.getMinHeight()).thenReturn(-64);
            when(world.getMaxHeight()).thenReturn(320);
            when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
            WorldBorder border = mock(WorldBorder.class);
            when(border.isInside(any())).thenReturn(true);
            when(world.getWorldBorder()).thenReturn(border);
            Block air = block(List.of());
            when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(call ->
                    blocks.getOrDefault(call.getArgument(0) + ":" + call.getArgument(1) + ":" + call.getArgument(2), air));
            when(victim.getUniqueId()).thenReturn(UUID.randomUUID());
            when(victim.isOnline()).thenReturn(true);
            when(victim.getLocation()).thenAnswer(call -> position.clone());
            when(victim.getBoundingBox()).thenAnswer(call -> new BoundingBox(position.getX() - .3, position.getY(), position.getZ() - .3,
                    position.getX() + .3, position.getY() + 1.8, position.getZ() + .3));
            when(victim.teleport(any(Location.class))).thenAnswer(call -> {
                position = ((Location) call.getArgument(0)).clone();
                return true;
            });
            when(attacker.getLocation()).thenReturn(new Location(world, -1, 64, .5));
            service = new KnockbackService(mock(JavaPlugin.class));
        }

        void solid(int x, int y, int z, BoundingBox shape) { blocks.put(x + ":" + y + ":" + z, block(List.of(shape))); }
        void push(int blocks) { service.push(attacker, victim, blocks); }
        void ticks(int count) { for (int i = 0; i < count; i++) tick.run(); }
        @Override public void close() { service.stop(); bukkit.close(); }

        private Block block(List<BoundingBox> boxes) {
            VoxelShape shape = mock(VoxelShape.class);
            when(shape.getBoundingBoxes()).thenReturn(boxes);
            Block block = mock(Block.class);
            when(block.getCollisionShape()).thenReturn(shape);
            return block;
        }
    }
}
