package dev.ajaczero.powerarmor.item;

import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Block breaking for the mining tools (drill, chainsaw). Server side only.
 *
 * Breaking a block "properly" is more than {@code level.removeBlock}: other mods (land claims, spawn
 * protection) listen for {@link BlockEvent.BreakEvent} and may cancel it, the block gets a chance to react
 * (doors take their other half, chests spill their contents) and the drops come from the block's loot table
 * for the tool that broke it. {@link #breakBlock} does what vanilla's {@code ServerPlayerGameMode.destroyBlock}
 * does, except that the "tool" is a virtual diamond pickaxe / axe instead of the item in the pilot's hand.
 */
public final class ToolMining {
    /** Vanilla: a block with hardness h takes h * 30 / speed ticks with the correct tool (100 without). */
    public static final float TICKS_PER_HARDNESS = 30F;

    /** True while one of our tools posts its own BreakEvent: {@code PilotHands} lets those through
     *  (it cancels every other break by a pilot, the hand can't mine). ThreadLocal because the integrated
     *  server and the client run on different threads. */
    private static final ThreadLocal<Boolean> TOOL_BREAK = ThreadLocal.withInitial(() -> false);

    public static boolean isToolBreak() {
        return TOOL_BREAK.get();
    }

    /** The block under the pilot's crosshair within their (suit-extended) reach, or null. */
    @Nullable
    public static BlockHitResult target(PowerArmorEntity suit, Player pilot) {
        Vec3 eye = pilot.getEyePosition();
        Vec3 end = eye.add(pilot.getViewVector(1F).scale(pilot.blockInteractionRange()));
        BlockHitResult hit = suit.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, pilot));
        return hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }

    /**
     * Break one block for {@code pilot} as if mined with {@code tool}. Returns false if nothing was broken
     * (air, unbreakable, a protection mod said no). Drops follow vanilla: none in creative mode.
     */
    public static boolean breakBlock(ServerLevel level, BlockPos pos, Player pilot, ItemStack tool) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.getDestroySpeed(level, pos) < 0) {
            return false;
        }
        GameType mode = pilot instanceof ServerPlayer sp ? sp.gameMode.getGameModeForPlayer()
                : pilot.isCreative() ? GameType.CREATIVE : GameType.SURVIVAL;
        if (pilot.blockActionRestricted(level, pos, mode)                    // adventure / spectator
                || state.getBlock() instanceof GameMasterBlock && !pilot.canUseGameMasterBlocks()) {
            return false;
        }
        BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(level, pos, state, pilot);
        TOOL_BREAK.set(true);
        try {
            NeoForge.EVENT_BUS.post(event);
        } finally {
            TOOL_BREAK.set(false);
        }
        if (event.isCanceled()) {
            return false;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        // Lets the block react (other door half, piglins guarding gold...) and plays the break effect for
        // everyone *except* the pilot (vanilla's pilot already saw it client-side); we send it to them below.
        BlockState broken = state.getBlock().playerWillDestroy(level, pos, state, pilot);
        if (!broken.onDestroyedByPlayer(level, pos, pilot, true, level.getFluidState(pos))) {
            return false;
        }
        broken.getBlock().destroy(level, pos, broken);
        if (!pilot.isCreative()) {
            Block.dropResources(broken, level, pos, blockEntity, pilot, tool); // loot table + XP (ores)
        }
        if (pilot instanceof ServerPlayer sp) {
            sp.connection.send(new ClientboundLevelEventPacket(2001, pos, Block.getId(state), false));
        }
        return true;
    }

    /** Break progress on one target, kept per suit. Shows the crack overlay on every block it will break. */
    public static class Progress {
        final PowerArmorEntity suit;
        @Nullable
        BlockPos target;
        @Nullable
        Direction face;
        float progress;
        int ticks;
        private int stage = -1;
        private final List<BlockPos> shown = new ArrayList<>();

        Progress(PowerArmorEntity suit) {
            this.suit = suit;
        }

        /** Start over if the pilot looks at another block (or another face of it). */
        void retarget(ServerLevel level, BlockPos pos, Direction face) {
            if (!pos.equals(this.target) || face != this.face) {
                this.clear(level);
                this.target = pos;
                this.face = face;
            }
        }

        /** Crack overlay (stage 0-9) on these blocks. Ids are negative so they never clash with a player's own. */
        void showCracks(ServerLevel level, List<BlockPos> blocks) {
            int newStage = Math.min(9, (int) (this.progress * 10F));
            if (newStage == this.stage && blocks.equals(this.shown)) {
                return;
            }
            this.hideCracks(level);
            this.stage = newStage;
            this.shown.addAll(blocks);
            for (int i = 0; i < blocks.size(); i++) {
                level.destroyBlockProgress(crackId(this.suit, i), blocks.get(i), newStage);
            }
        }

        private void hideCracks(ServerLevel level) {
            for (int i = 0; i < this.shown.size(); i++) {
                level.destroyBlockProgress(crackId(this.suit, i), this.shown.get(i), -1); // outside 0..9 = remove
            }
            this.shown.clear();
            this.stage = -1;
        }

        /** Forget the target and its cracks (looked away, released, block broken). */
        void clear(ServerLevel level) {
            this.progress = 0;
            this.target = null;
            this.face = null;
            this.hideCracks(level);
        }
    }

    private static int crackId(PowerArmorEntity suit, int index) {
        return -1000 - suit.getId() * 16 - index;
    }

    /** Grinding effects at the contact point: the block's hit sound (like mining) and crumbs. */
    static void grindEffects(ServerLevel level, BlockPos pos, Vec3 at, int ticks) {
        BlockState state = level.getBlockState(pos);
        if (ticks % 4 == 0) {
            SoundType sound = state.getSoundType(level, pos, null);
            level.playSound(null, pos, sound.getHitSound(), SoundSource.BLOCKS, (sound.getVolume() + 1F) / 4F, sound.getPitch() * 0.6F);
        }
        if (ticks % 2 == 0) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y, at.z, 4, 0.15, 0.15, 0.15, 0.1);
        }
    }

    private ToolMining() {}
}
