package dev.ajaczero.powerarmor.client;

import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import dev.ajaczero.powerarmor.item.PowerTool;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;
import software.bernie.geckolib.util.RenderUtil;

/**
 * The pilot's first-person "viewmodel": the socket tool on the left and the suit's gauntlet on the right, drawn in
 * front of the camera like vanilla's hands (CockpitHands) instead of looking at the suit's own arms from inside.
 * There is one per client. Its bones are named like the suit's (tools/extract-view-geo.py), so it plays the suit's
 * tool animations: the drill bit spins, the chain runs (the chaingun's barrels turn in code, ChaingunParts). Keys for bones it doesn't have
 * (the suit's arms) are skipped by GeckoLib.
 */
final class CockpitView implements GeoAnimatable {
    static final CockpitView INSTANCE = new CockpitView();

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** The suit being piloted, set before each render. */
    @Nullable
    PowerArmorEntity suit;

    private CockpitView() {}

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "tool", 3, this::toolAnimation));
    }

    private PlayState toolAnimation(AnimationState<CockpitView> state) {
        if (this.suit != null && this.suit.isToolRunning() && this.suit.getSocketStack().getItem() instanceof PowerTool tool
                && tool.runAnimation() != null) {
            return state.setAndContinue(tool.runAnimation());
        }
        return PlayState.STOP;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public double getTick(Object object) {
        return RenderUtil.getCurrentTick();
    }
}
