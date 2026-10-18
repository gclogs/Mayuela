package org.minecraft.eden.Eden.mayuela.service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public final class KnockbackService {
    private static final double STEP = 0.75;
    private static final double EPSILON = 0.000001;
    private final JavaPlugin plugin;
    private final Map<UUID, Push> pushes = new HashMap<>();
    private BukkitTask task;

    public KnockbackService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void push(Player attacker, Player victim, int blocks) {
        Location origin = victim.getLocation();
        Vector direction = origin.toVector().subtract(attacker.getLocation().toVector()).setY(0);
        if (direction.lengthSquared() < EPSILON) {
            direction = attacker.getLocation().getDirection().setY(0);
        }
        if (direction.lengthSquared() < EPSILON) {
            direction = new Vector(0, 0, 1);
        }
        pushes.put(victim.getUniqueId(), new Push(victim, origin, direction.normalize(), Math.max(1, blocks)));
        if (task == null) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        }
    }

    private void tick() {
        pushes.values().removeIf(push -> !advance(push));
        if (pushes.isEmpty() && task != null) {
            task.cancel();
            task = null;
        }
    }

    private boolean advance(Push push) {
        Player player = push.player;
        Location current = player.getLocation();
        if (!player.isOnline() || player.isDead() || current.getWorld() != push.position.getWorld()
                || current.distanceSquared(push.position) > 4.0) {
            return false;
        }
        // Measure displacement ourselves: vanilla velocity depends on friction and player input.
        BoundingBox body = player.getBoundingBox().shift(push.position.toVector().subtract(current.toVector()));
        double requested = Math.min(STEP, push.remaining);
        double distance = clearDistance(push.position.getWorld(), body, push.direction, requested);
        Location destination = push.position.clone().add(push.direction.clone().multiply(distance));
        destination.setYaw(current.getYaw());
        destination.setPitch(current.getPitch());
        if (distance <= EPSILON || !player.teleport(destination)) {
            return false;
        }
        player.setVelocity(new Vector());
        push.position = destination;
        push.remaining -= distance;
        return distance + EPSILON >= requested && push.remaining > EPSILON;
    }

    private double clearDistance(World world, BoundingBox body, Vector direction, double requested) {
        BoundingBox swept = body.clone().union(body.clone().shift(direction.clone().multiply(requested)));
        double distance = requested;
        for (int x = (int) Math.floor(swept.getMinX()); x <= (int) Math.floor(swept.getMaxX()); x++) {
            for (int z = (int) Math.floor(swept.getMinZ()); z <= (int) Math.floor(swept.getMaxZ()); z++) {
                if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                    return 0.0;
                }
                // Fences can extend above the block containing their collision shape.
                for (int y = (int) Math.floor(swept.getMinY()) - 1; y <= (int) Math.floor(swept.getMaxY()); y++) {
                    if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
                        continue;
                    }
                    for (BoundingBox local : world.getBlockAt(x, y, z).getCollisionShape().getBoundingBoxes()) {
                        BoundingBox obstacle = local.clone().shift(x, y, z);
                        if (!swept.overlaps(obstacle)) {
                            continue;
                        }
                        BoundingBox expanded = obstacle.clone().expand(body.getWidthX() / 2,
                                body.getHeight() / 2, body.getWidthZ() / 2);
                        if (expanded.contains(body.getCenter())) {
                            return 0.0;
                        }
                        RayTraceResult hit = expanded.rayTrace(body.getCenter(), direction, distance);
                        if (hit != null) {
                            distance = Math.max(0, body.getCenter().distance(hit.getHitPosition()) - EPSILON);
                        }
                    }
                }
            }
        }
        BoundingBox destination = body.clone().shift(direction.clone().multiply(distance));
        if (!world.getWorldBorder().isInside(new Location(world, destination.getMinX(), destination.getMinY(), destination.getMinZ()))
                || !world.getWorldBorder().isInside(new Location(world, destination.getMaxX(), destination.getMaxY(), destination.getMaxZ()))) {
            return 0.0;
        }
        return distance;
    }

    public void clear(Player player) {
        pushes.remove(player.getUniqueId());
    }

    public void stop() {
        pushes.clear();
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private static final class Push {
        private final Player player;
        private final Vector direction;
        private Location position;
        private double remaining;

        private Push(Player player, Location position, Vector direction, double remaining) {
            this.player = player;
            this.position = position;
            this.direction = direction;
            this.remaining = remaining;
        }
    }
}
