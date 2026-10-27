package dev.ajaczero.powerarmor.client.hud;

import dev.ajaczero.powerarmor.PowerArmorConfig;
import dev.ajaczero.powerarmor.entity.Trajectory;
import dev.ajaczero.powerarmor.item.EmProjectorItem;
import dev.ajaczero.powerarmor.item.SocketAim;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The EM projector on the visor (2026-10-27).
 * <ul>
 *   <li>It scans on its own (user, 2026-10-27: you see what you can pull): everything within its grab range that you
 *   can see and it could catch (mobs, dropped items) gets small faint corner brackets, and so does every other player,
 *   whether the server lets you grab them or not (the client can't know the server's PvP rule); the nearest
 *   {@link #MAX_MARKS} only, so a farm doesn't fill the glass. The one a click would grab now (under the crosshair: the
 *   server's own pick, SocketAim.entityInView) gets bright brackets and its name.</li>
 *   <li>While it holds something: brackets round it, its name over them (HEAVY before it for a thing too heavy to
 *   lift, that the field only drags and shoves; the user dropped FIELD: it read as jargon) and, for a living thing, its
 *   health; and where it will fly if you let go now: a dotted arc from it to a diamond where it lands
 *   ({@link EmProjectorItem#predictThrow}, the TNT arc's drawing), moving with your aim. The marked things in its way
 *   light up: a thrown mob bowls through them (EmProjectorItem.bowl).</li>
 * </ul>
 * Everything comes from the client's own world and the suit's synced state (the socket's beam target): the client never
 * asks. The range is this client's config (the balance config isn't synced: on a server with another range the marks
 * can be off at the edge).
 */
public final class EmProjectorHud implements ToolHud {
    /** The box's gap round the hit box, and its brackets' arms (GUI px): what it holds or would grab, and the rest. */
    private static final int PAD = 2, ARM = 5, MARK_ARM = 3;
    private static final int HEALTH_SEGMENTS = 8;
    /** The landing diamond's half-size (the TNT arc's). */
    private static final int MARK = 4;
    /** The label's top this far above the box (its text and the health bar under it, then a gap), and no higher than
     *  the visor's readouts reach. */
    private static final int LABEL_UP = 15, LABEL_TOP = 62;
    /** At most this many things marked (the nearest). */
    private static final int MAX_MARKS = 10;
    /** The grab's widening of small things' hit boxes (EmProjectorItem.holdTick). */
    private static final float GRAB_INFLATE = 0.6F;

    @Override
    public String render(GuiGraphicsExtractor g, HudView v) {
        Entity held = v.suit.socket().getBeamTarget();
        List<Entity> marks = scan(v, held);
        Set<Entity> inLine = Set.of();
        String thrown = "";
        if (held != null) {
            List<Vec3> path = new ArrayList<>();
            thrown = throwArc(g, v, held, path);
            inLine = inTheWay(held, path, marks);
        }
        // what a click would grab now (nothing while something is held: the field is busy)
        Entity aimed = held != null ? null : SocketAim.entityInView(v.suit, v.pilot, range(), marks::contains, GRAB_INFLATE);
        for (Entity e : marks) {
            if (e == aimed) {
                continue;
            }
            int[] box = screenBox(v, e);
            if (box != null) {
                HudView.brackets(g, box[0] - PAD, box[1] - PAD, box[2] + PAD, box[3] + PAD, MARK_ARM,
                        inLine.contains(e) ? v.main(false) : v.faint(false));
            }
        }
        String state = "scan=" + marks.size() + (inLine.isEmpty() ? "" : " in_line=" + inLine.size());
        if (aimed != null) {
            labelled(g, v, aimed, false);
            state += " aim=" + id(aimed);
        }
        if (held != null) {
            int[] box = labelled(g, v, held, true);
            state += " held=" + id(held) + (box == null ? "" : String.format(Locale.ROOT, " box=%d,%d,%d,%d", box[0], box[1], box[2], box[3]))
                    + " " + thrown;
        }
        return state;
    }

    private static double range() {
        return PowerArmorConfig.EM_RANGE.get();
    }

    /**
     * What the projector could reach: within its range of the pilot's eye (the grab's ray is that long), in plain sight
     * (no block between the eye and it), and either something the field catches (a mob, a dropped item) or another
     * player; not the suit itself, its pilot or what it holds. The nearest {@link #MAX_MARKS}.
     */
    private static List<Entity> scan(HudView v, @Nullable Entity held) {
        Vec3 eye = v.pilot.getEyePosition(v.partial);
        double range = range();
        AABB area = new AABB(eye, eye).inflate(range);
        return v.suit.level().getEntities(v.pilot, area, e -> e != v.suit && e != held && !v.suit.hasPassenger(e)
                        && (e instanceof Player p ? !p.isSpectator() : EmProjectorItem.canGrab(e))
                        && e.getBoundingBox().getCenter().distanceTo(eye) <= range && visible(v, eye, e))
                .stream()
                .sorted(Comparator.comparingDouble(e -> e.distanceToSqr(eye)))
                .limit(MAX_MARKS)
                .collect(Collectors.toList());
    }

    /** No block between the eye and the middle of its hit box. */
    private static boolean visible(HudView v, Vec3 eye, Entity e) {
        return v.suit.level().clip(new ClipContext(eye, e.getBoundingBox().getCenter(), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, v.pilot)).getType() == HitResult.Type.MISS;
    }

    /** The marked things a throw along {@code path} would pass through: the held thing's hit box swept along it. */
    private static Set<Entity> inTheWay(Entity held, List<Vec3> path, List<Entity> marks) {
        if (path.size() < 2 || marks.isEmpty()) {
            return Set.of();
        }
        AABB box = held.getBoundingBox().move(held.position().scale(-1));
        Set<Entity> hit = new java.util.HashSet<>();
        for (int i = 1; i < path.size(); i++) {
            AABB swept = box.move(path.get(i - 1)).expandTowards(path.get(i).subtract(path.get(i - 1)));
            for (Entity e : marks) {
                if (e instanceof LivingEntity && swept.intersects(e.getBoundingBox())) {
                    hit.add(e);
                }
            }
        }
        return hit;
    }

    /** Bright brackets round {@code e}, its name over them and its health (the held thing's own, bigger brackets);
     *  returns the box on the screen, or null when it's off the screen. */
    @Nullable
    private static int[] labelled(GuiGraphicsExtractor g, HudView v, Entity e, boolean held) {
        int main = v.main(false);
        int[] box = screenBox(v, e);
        // The label above the box, over its top-left bracket: "PIG", the health bar under it. (Beside the box it
        // sat on the thing itself: a mob's model reaches past its hit box, a pig's head by a third of a block.)
        Component label = EmProjectorItem.isHeavy(e)
                ? Component.translatable("hud.powerarmor.heavy").append(" ").append(name(e)) : name(e);
        int lx, ly;
        if (box != null) {
            lx = box[0] - PAD;
            ly = box[1] - PAD - LABEL_UP;
            if (ly < LABEL_TOP) {
                ly = box[3] + PAD + 4; // no room above (it's high up in the view): under it
            }
            lx = Math.min(lx, v.width() - 4 - v.font.width(label));
            HudView.brackets(g, box[0] - PAD, box[1] - PAD, box[2] + PAD, box[3] + PAD, held ? ARM : MARK_ARM + 1, main);
        } else if (held) {
            lx = v.crosshairX() + 12;
            ly = v.crosshairY() + 8;
        } else {
            return null;
        }
        g.text(v.font, label, lx, ly, main, false);
        if (e instanceof LivingEntity living) {
            HudView.bar(g, lx, ly + 9, HEALTH_SEGMENTS, 2, living.getHealth() / living.getMaxHealth(), main, v.dim(false));
        }
        return box;
    }

    /** Where it flies if let go now: a dotted line from its middle along the flight, a diamond where it lands (at its
     *  feet). Fills {@code path} with the flight (where it is this frame on); returns it for the log. */
    private static String throwArc(GuiGraphicsExtractor g, HudView v, Entity target, List<Vec3> path) {
        Vec3 velocity = EmProjectorItem.throwVelocity(v.suit, target, v.pilot, v.partial);
        Trajectory flight = EmProjectorItem.predictThrow(v.suit.level(), target, velocity);
        // drawn where the target is this frame (the prediction starts at its last tick's position)
        Vec3 shift = target.getPosition(v.partial).subtract(target.position());
        Vec3 middle = shift.add(0, target.getBbHeight() / 2, 0);
        List<Vec3> line = new ArrayList<>(flight.path().size() + 1);
        for (Vec3 p : flight.path()) {
            line.add(p.add(middle));
            path.add(p.add(shift));
        }
        line.add(flight.end().add(shift));
        path.add(flight.end().add(shift));
        int dots = v.dottedLine(g, line, v.faint(false), 0);
        float[] p = v.project(flight.end().add(shift));
        if (p != null && flight.hit()) {
            HudView.diamond(g, Math.round(p[0]), Math.round(p[1]), MARK, v.main(false));
        }
        return String.format(Locale.ROOT, "arc=%d %s=%.1f ticks=%d", dots, flight.hit() ? "lands" : "flying",
                flight.end().distanceTo(target.position()), flight.ticks());
    }

    private static String id(Entity e) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
    }

    /** The name in capitals, like the rest of the visor; a stack of items with its count. */
    private static Component name(Entity target) {
        String name = target.getDisplayName().getString().toUpperCase(Locale.ROOT);
        if (target instanceof ItemEntity item && item.getItem().getCount() > 1) {
            name += " x" + item.getItem().getCount();
        }
        return Component.literal(name);
    }

    /** The target's hit box on the screen (where it is this frame), as {x0, y0, x1, y1} in GUI px, or null if any
     *  corner is behind the camera. */
    @Nullable
    private static int[] screenBox(HudView v, Entity target) {
        Vec3 shift = target.getPosition(v.partial).subtract(target.position());
        AABB b = target.getBoundingBox().move(shift);
        float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            float[] p = v.project(new Vec3((i & 1) == 0 ? b.minX : b.maxX, (i & 2) == 0 ? b.minY : b.maxY, (i & 4) == 0 ? b.minZ : b.maxZ));
            if (p == null) {
                return null;
            }
            x0 = Math.min(x0, p[0]);
            y0 = Math.min(y0, p[1]);
            x1 = Math.max(x1, p[0]);
            y1 = Math.max(y1, p[1]);
        }
        return new int[] {Math.round(x0), Math.round(y0), Math.round(x1), Math.round(y1)};
    }
}
