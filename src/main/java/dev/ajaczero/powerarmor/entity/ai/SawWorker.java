package dev.ajaczero.powerarmor.entity.ai;

import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;

/** Chainsaw job (the chainsaw adds it, see PowerTool.mindGoals): fell the natural trees round home, nearest first: cut
 *  the trunk's lowest log, wait for the tree to come down, next tree. */
public class SawWorker extends Worker {
    public SawWorker(PowerArmorEntity suit) {
        super(suit);
    }

    @Override
    protected boolean hasJob() {
        return this.suit.brain().fellsTrees();
    }

    @Override
    protected BlockPos findTarget(Set<BlockPos> skip) {
        return SuitWork.nextTrunk(this.suit, skip);
    }

    @Override
    protected boolean stillWork(BlockPos target) {
        return this.suit.level().getBlockState(target).is(BlockTags.LOGS);
    }

    @Override
    protected boolean mayWorkOn(BlockPos pos) {
        return SuitWork.mayCut(this.suit.level(), pos, this.suit.level().getBlockState(pos));
    }

    @Override
    protected boolean busy() {
        return this.suit.socket().isRunning() && !this.suit.socket().isAiTriggerHeld(); // the tree is still coming down
    }
}
