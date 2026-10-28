package dev.ajaczero.powersuits.client;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.object.PlayState;
import com.geckolib.animation.state.AnimationTest;
import com.geckolib.util.GeckoLibUtil;
import dev.ajaczero.powersuits.entity.PowerSuitEntity;
import dev.ajaczero.powersuits.item.PowerTool;
import org.jspecify.annotations.Nullable;

/**
 * The pilot's first-person "viewmodel": the socket tool on the left and the suit's gauntlet on the right, drawn in
 * front of the camera like vanilla's hands (CockpitHands) instead of looking at the suit's own arms from inside.
 * There is one per client. Its bones are named like the suit's (tools/extract-view-geo.py), so it plays the suit's
 * tool animations: the drill bit spins, the chain runs (the chaingun's barrels turn in code, ToolRotors). Keys for bones it doesn't have
 * (the suit's arms) are skipped by GeckoLib.
 */
final class CockpitView implements GeoAnimatable {
    static final CockpitView INSTANCE = new CockpitView();

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** The suit being piloted, set before each render. */
    @Nullable
    PowerSuitEntity suit;
    /** Which half the next render pass draws (copied into its render state: the model is drawn later). */
    CockpitViewRenderer.Part part = CockpitViewRenderer.Part.TOOL;

    private CockpitView() {}

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>("tool", 3, this::toolAnimation));
    }

    private PlayState toolAnimation(AnimationTest<CockpitView> state) {
        if (this.suit != null && this.suit.socket().isRunning() && this.suit.getSocketStack().getItem() instanceof PowerTool tool
                && tool.runAnimation() != null) {
            return state.setAndContinue(tool.runAnimation());
        }
        return PlayState.STOP;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
