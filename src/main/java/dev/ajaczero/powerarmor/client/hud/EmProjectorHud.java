package dev.ajaczero.powerarmor.client.hud;

import dev.ajaczero.powerarmor.entity.Trajectory;
import dev.ajaczero.powerarmor.item.EmProjectorItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The EM projector on the visor (2026-10-27): nothing while it idles; SCAN... under the crosshair while the button is
 * held and the field is still looking for something to grab; and while it holds something, corner brackets round it on
 * the world (its hit box, found on the screen every frame so they stay on it as it swings), its name over them (HEAVY
 * before it for a thing too heavy to lift, that the field only drags and shoves; the user dropped FIELD: it read as
 * jargon) and, for a living thing, its health; and where it will fly if you let go now: a dotted arc from it to a
 * diamond where it lands ({@link EmProjectorItem#predictThrow}, the TNT arc's drawing), moving with your aim.
 * Everything comes from the suit's synced state (the socket's beam target, the tool in use): the client never asks.
 */
public final class EmProjectorHud implements ToolHud {
    /** The box's gap round the hit box, and its brackets' arms (GUI px). */
    private static final int PAD = 2, ARM = 5;
    private static final int HEALTH_SEGMENTS = 8;
    /** The landing diamond's half-size (the TNT arc's). */
    private static final int MARK = 4;
    /** The label's top this far above the box (its text and the health bar under it, then a gap), and no higher than
     *  the visor's readouts reach. */
    private static final int LABEL_UP = 15, LABEL_TOP = 62;

    @Override
    public String render(GuiGraphicsExtractor g, HudView v) {
        Entity target = v.suit.socket().getBeamTarget();
        if (target == null) {
            if (!v.suit.socket().isActive()) {
                return "";
            }
            // searching: the dots count up, one every 5 ticks
            String dots = ".".repeat(1 + (v.suit.tickCount / 5) % 3);
            v.centred(g, Component.translatable("hud.powerarmor.scan").append(dots), v.crosshairX() + 1, v.crosshairY() + 16, v.faint(false));
            return "scan";
        }
        int main = v.main(false);
        int[] box = screenBox(v, target);
        String thrown = throwArc(g, v, target);
        // The label above the box, over its top-left bracket: "PIG", the health bar under it. (Beside the box it
        // sat on the thing itself: a mob's model reaches past its hit box, a pig's head by a third of a block.)
        Component label = EmProjectorItem.isHeavy(target)
                ? Component.translatable("hud.powerarmor.heavy").append(" ").append(name(target)) : name(target);
        int lx, ly;
        if (box != null) {
            lx = box[0] - PAD;
            ly = box[1] - PAD - LABEL_UP;
            if (ly < LABEL_TOP) {
                ly = box[3] + PAD + 4; // no room above (it's high up in the view): under it
            }
            lx = Math.min(lx, v.width() - 4 - v.font.width(label));
            HudView.brackets(g, box[0] - PAD, box[1] - PAD, box[2] + PAD, box[3] + PAD, ARM, main);
        } else {
            lx = v.crosshairX() + 12;
            ly = v.crosshairY() + 8;
        }
        g.text(v.font, label, lx, ly, main, false);
        if (target instanceof LivingEntity living) {
            HudView.bar(g, lx, ly + 9, HEALTH_SEGMENTS, 2, living.getHealth() / living.getMaxHealth(), main, v.dim(false));
        }
        return "held=" + BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).getPath()
                + (box == null ? "" : String.format(Locale.ROOT, " box=%d,%d,%d,%d", box[0], box[1], box[2], box[3])) + " " + thrown;
    }

    /** Where it flies if let go now: a dotted line from its middle along the flight, a diamond where it lands (at its
     *  feet). Returns that for the log. */
    private static String throwArc(GuiGraphicsExtractor g, HudView v, Entity target) {
        Vec3 velocity = EmProjectorItem.throwVelocity(v.suit, target, v.pilot, v.partial);
        Trajectory flight = EmProjectorItem.predictThrow(v.suit.level(), target, velocity);
        // drawn where the target is this frame (the prediction starts at its last tick's position)
        Vec3 shift = target.getPosition(v.partial).subtract(target.position());
        Vec3 middle = shift.add(0, target.getBbHeight() / 2, 0);
        List<Vec3> line = new ArrayList<>(flight.path().size() + 1);
        for (Vec3 p : flight.path()) {
            line.add(p.add(middle));
        }
        line.add(flight.end().add(shift));
        int dots = v.dottedLine(g, line, v.faint(false), 0);
        float[] p = v.project(flight.end().add(shift));
        if (p != null && flight.hit()) {
            HudView.diamond(g, Math.round(p[0]), Math.round(p[1]), MARK, v.main(false));
        }
        return String.format(Locale.ROOT, "arc=%d %s=%.1f ticks=%d", dots, flight.hit() ? "lands" : "flying",
                flight.end().distanceTo(target.position()), flight.ticks());
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
