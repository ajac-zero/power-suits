package dev.ajaczero.powerarmor.client;

import com.geckolib.cache.model.GeoBone;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.base.GeoRenderer;
import com.geckolib.renderer.base.PerBoneRender;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.GeoRenderLayer;
import dev.ajaczero.powerarmor.entity.PowerArmorEntity;
import java.util.function.BiConsumer;
import net.minecraft.client.model.object.skull.SkullModelBase;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.SkullBlock;
import org.jspecify.annotations.Nullable;

/**
 * Without its helmet the suit shows its pilot's own head in the collar (the pilot's body stays hidden inside the suit,
 * PilotView.hidePilot). Drawn with vanilla's player-head model (head + hat layer, the pilot's skin) at the "pilot_head"
 * bone, which rides the suit's head bone: it turns and leans with the torso like the helmet would. Not in the pilot's own
 * cockpit view (the camera is inside that head).
 */
public class PilotHeadLayer extends GeoRenderLayer<PowerArmorEntity, Void, LivingEntityRenderState> {
    /** The pilot's skin texture this frame, or absent: no head to draw. */
    private static final DataTicket<Identifier> PILOT_SKIN = DataTicket.create("power_suits_pilot_skin", Identifier.class);
    private final SkullModelBase headModel;

    public PilotHeadLayer(EntityRendererProvider.Context context, GeoRenderer<PowerArmorEntity, Void, LivingEntityRenderState> renderer) {
        super(renderer);
        this.headModel = SkullBlockRenderer.createModel(context.getModelSet(), SkullBlock.Types.PLAYER);
    }

    /** Extract: the skin of a player sitting in a helmetless suit (the entities aren't available when the model draws). */
    @Override
    public void addRenderData(PowerArmorEntity suit, @Nullable Void ignored, LivingEntityRenderState state, float partialTick) {
        if (!suit.hasHelmet() && suit.getControllingPassenger() instanceof AbstractClientPlayer pilot && !PowerArmorRenderer.isCockpitView(suit)) {
            state.addGeckolibData(PILOT_SKIN, pilot.getSkin().body().texturePath());
        }
    }

    @Override
    public void addPerBoneRender(RenderPassInfo<LivingEntityRenderState> info, BiConsumer<GeoBone, PerBoneRender<LivingEntityRenderState>> consumer) {
        Identifier skin = info.renderState().getGeckolibData(PILOT_SKIN);
        if (skin == null || !info.willRender()) {
            return;
        }
        info.model().getBone("pilot_head").ifPresent(bone -> consumer.accept(bone, (pass, b, tasks) -> {
            var poseStack = pass.poseStack(); // at the bone's pivot: the bottom centre of the head, in the suit's model space
            // Vanilla models are built y-down and drawn through scale(-1, -1, 1) (LivingEntityRenderer does the same): the
            // head box then reaches 8 px up from the neck, face to the suit's front.
            poseStack.scale(-1F, -1F, 1F);
            tasks.submitModel(this.headModel, new SkullModelBase.State(), poseStack, SkullBlockRenderer.getPlayerSkinRenderType(skin),
                    pass.packedLight(), OverlayTexture.NO_OVERLAY, pass.renderState().outlineColor);
        }));
    }
}
