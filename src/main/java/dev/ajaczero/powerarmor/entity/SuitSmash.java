package dev.ajaczero.powerarmor.entity;

import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.PowerArmorConfig;
import dev.ajaczero.powerarmor.item.ToolMining;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A ton of moving steel goes straight through flimsy blocks. Walking breaks the blocks in
 * {@code powerarmor:suit_smashes_walking} (leaves, panes, cobwebs, snow layers...), sprinting also breaks
 * {@code powerarmor:suit_smashes_sprinting} (glass and ice). Both are block tags in {@code data/powerarmor/tags/block/},
 * so a datapack can change the lists. Nothing drops. Server side.
 *
 * Where the suit goes is decided on the pilot's client (ridden movement is client-authoritative), and the client stops
 * at a pane before the server could break it. So the server breaks what's in the suit's way a little ahead of it: the
 * box it checks reaches further in the direction the pilot is steering.
 */
public final class SuitSmash {
    public static final TagKey<Block> WALKING = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(PowerArmor.MODID, "suit_smashes_walking"));
    public static final TagKey<Block> SPRINTING = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(PowerArmor.MODID, "suit_smashes_sprinting"));

    /** How far ahead of the hitbox to clear (blocks): about two ticks of travel at a jog (0.25 b/t) / sprint (0.5 b/t). */
    private static final double LOOK_AHEAD_WALK = 0.6, LOOK_AHEAD_SPRINT = 1.1;
    /** How deep under the feet counts as "walking on" a block (leaves of a canopy, a glass roof, a snow layer). */
    private static final double BELOW_FEET = 0.2;

    /** Server, every tick of a powered, piloted suit. Returns how many blocks were broken (tests). */
    public static int tick(PowerArmorEntity suit, Player pilot) {
        if (!PowerArmorConfig.SMASH_BLOCKS.get() || !(suit.level() instanceof ServerLevel level)) {
            return 0;
        }
        // The pilot's WASD, turned into a world direction the same way vanilla does for movement (yaw = where they look).
        Vec3 input = PowerArmorEntity.pilotInput(pilot);
        if (input.lengthSqr() < 1e-4) {
            return 0; // standing still: you can stand on a glass roof or in a bush
        }
        float yaw = suit.getYRot() * Mth.DEG_TO_RAD;
        float sin = Mth.sin(yaw), cos = Mth.cos(yaw);
        Vec3 dir = new Vec3(input.x * cos - input.z * sin, 0, input.z * cos + input.x * sin).normalize();
        boolean sprint = pilot.isSprinting();
        TagKey<Block> tag = sprint ? SPRINTING : WALKING;
        AABB body = suit.getBoundingBox();
        AABB box = body.expandTowards(dir.scale(sprint ? LOOK_AHEAD_SPRINT : LOOK_AHEAD_WALK))
                .expandTowards(0, -BELOW_FEET, 0).deflate(0.01); // deflate: touching a wall isn't being inside it
        int broken = 0;
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            if (level.getBlockState(pos).is(tag) && ToolMining.breakBlock(level, pos.immutable(), pilot, ItemStack.EMPTY, false)) {
                broken++;
            }
        }
        return broken;
    }

    private SuitSmash() {}
}
