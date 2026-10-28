package dev.ajaczero.powersuits.entity.ai;

import dev.ajaczero.powersuits.entity.PowerSuitEntity;
import java.util.Set;
import net.minecraft.core.BlockPos;

/** Drill job (the drill adds it, see PowerTool.mindGoals): clear an open hall at home level, ring by ring outward from
 *  home (SuitWork.mayDig decides). */
public class DrillWorker extends Worker {
    public DrillWorker(PowerSuitEntity suit) {
        super(suit);
    }

    @Override
    protected boolean hasJob() {
        return this.suit.brain().digsHall();
    }

    @Override
    protected BlockPos findTarget(Set<BlockPos> skip) {
        return SuitWork.nextDigTarget(this.suit, skip);
    }

    @Override
    protected boolean stillWork(BlockPos target) {
        return SuitWork.mayDig(this.suit, target);
    }

    @Override
    protected boolean mayWorkOn(BlockPos pos) {
        return SuitWork.mayDig(this.suit, pos);
    }
}
