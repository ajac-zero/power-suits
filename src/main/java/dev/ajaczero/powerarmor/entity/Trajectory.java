package dev.ajaczero.powerarmor.entity;

import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Something's flight, worked out ahead (the pilot's visor draws it: the TNT launcher's round, TntShell.predict; a mob
 * the EM projector is about to throw, EmProjectorItem.predictThrow): {@code path} its position after each tick (0 =
 * where it starts), {@code end} where the flight ends, {@code ticks} when, {@code hit} whether it hit something there
 * (false: it was still in the air when the prediction stopped: a TNT round's fuse running out) and {@code entity} what
 * it hit, if that was an entity.
 */
public record Trajectory(List<Vec3> path, Vec3 end, int ticks, boolean hit, @Nullable Entity entity) {}
