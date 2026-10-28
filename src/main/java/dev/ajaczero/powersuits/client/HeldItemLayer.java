package dev.ajaczero.powersuits.client;

import com.geckolib.cache.model.GeoBone;
import com.geckolib.renderer.base.GeoRenderer;
import com.geckolib.renderer.layer.builtin.BlockAndItemGeoLayer;
import com.geckolib.util.RenderUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.ajaczero.powersuits.entity.PowerSuitEntity;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Draws the pilot's selected item in the suit's right hand (the "right_hand" grip bone), the way a player's
 * held item is drawn, only bigger. Skipped in the pilot's own cockpit view: the suit's upper body
 * isn't drawn there; the cockpit viewmodel's gauntlet holds the item instead (CockpitHands).
 */
public class HeldItemLayer extends BlockAndItemGeoLayer<PowerSuitEntity, Void, LivingEntityRenderState> {
    /** The suit's hand is about 1.5x a player's. */
    private static final float SCALE = 1.5F;

    public HeldItemLayer(EntityRendererProvider.Context context, GeoRenderer<PowerSuitEntity, Void, LivingEntityRenderState> renderer) {
        super(context, renderer);
    }

    /** Extract: the item's model for this frame (the item itself isn't available any more when the model is drawn). */
    @Override
    protected List<RenderData> getRelevantBones(PowerSuitEntity suit, @Nullable Void ignored, LivingEntityRenderState state, float partialTick) {
        if (!(suit.getControllingPassenger() instanceof Player pilot) || PowerSuitRenderer.isCockpitView(suit)) {
            return List.of();
        }
        ItemStack stack = pilot.getMainHandItem();
        if (stack.isEmpty()) {
            return List.of();
        }
        ItemDisplayContext context = ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        return List.of(RenderData.item("right_hand", context, RenderUtil.createRenderStateForItem(stack, this.itemModelResolver, context, suit)));
    }

    @Override
    public void addRenderData(PowerSuitEntity suit, @Nullable Void ignored, LivingEntityRenderState state, float partialTick) {
        List<RenderData> contents = getRelevantBones(suit, ignored, state, partialTick);
        if (!contents.isEmpty()) {
            state.addGeckolibData(CONTENTS, contents);
        }
    }

    @Override
    protected void submitItemStackRender(PoseStack poseStack, GeoBone bone, ItemStackRenderState stack, ItemDisplayContext displayContext,
                                         LivingEntityRenderState state, SubmitNodeCollector renderTasks, int packedLight) {
        poseStack.pushPose();
        // Same grip as vanilla's ItemInHandLayer (the bone's -Y runs down the forearm to the fingers).
        poseStack.rotateDegrees(Axis.XP, -90.0F);
        poseStack.rotateDegrees(Axis.YP, 180.0F);
        poseStack.translate(1F / 16F, 0.125F, 0.0F);
        poseStack.scale(SCALE, SCALE, SCALE);
        super.submitItemStackRender(poseStack, bone, stack, displayContext, state, renderTasks, packedLight);
        poseStack.popPose();
    }
}
