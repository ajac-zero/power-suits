package dev.ajaczero.powerarmor.client;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.GeoObjectRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.GeoRenderLayer;
import com.geckolib.util.ClientUtil;
import dev.ajaczero.powerarmor.PowerArmor;
import dev.ajaczero.powerarmor.item.PowerTool;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/** Draws one half of the cockpit viewmodel (CockpitView): the socket tool or the empty gauntlet. */
final class CockpitViewRenderer extends GeoObjectRenderer<CockpitView, Void, GeoRenderState> {
    enum Part { TOOL, GAUNTLET }

    static final String[] TOOL_BONES = {"chaingun", "em_projector", "drill", "chainsaw"};

    // Copied from CockpitView / its suit when the frame is extracted (the model is posed and drawn later).
    private static final DataTicket<Part> PART = DataTicket.create("powerarmor_view_part", Part.class);
    private static final DataTicket<String> TOOL_BONE = DataTicket.create("powerarmor_view_tool_bone", String.class);
    private static final DataTicket<Float> BARREL_ANGLE = DataTicket.create("powerarmor_view_barrel_angle", Float.class);
    private static final DataTicket<int[]> GLOW = DataTicket.create("powerarmor_view_glow", int[].class);
    /** The tool's lights, like on the suit (SuitGlowLayer): the chaingun's hot barrels, the EM projector's redstone. */
    private static final Identifier[] MASKS = {SuitGlowLayer.HEAT, SuitGlowLayer.HEAT_BARREL, SuitGlowLayer.EM_CORE, SuitGlowLayer.EM_ARC};

    CockpitViewRenderer() {
        super(new Model());
        this.withRenderLayer(new ToolGlow(this));
    }

    @Override
    public void addRenderData(CockpitView view, @Nullable Void ignored, GeoRenderState state, float partialTick) {
        state.addGeckolibData(PART, view.part);
        var suit = view.suit;
        state.addGeckolibData(TOOL_BONE, suit != null && suit.getSocketStack().getItem() instanceof PowerTool tool ? tool.suitBone() : "");
        state.addGeckolibData(BARREL_ANGLE, suit == null ? 0F : suit.getBarrelAngle(partialTick));
        if (suit != null && view.part == Part.TOOL) {
            float heat = suit.getToolHeat();
            float time = (float) ClientUtil.getCurrentTick(partialTick);
            state.addGeckolibData(GLOW, new int[] {
                    SuitGlowLayer.tint(SuitGlowLayer.heatGlow(heat), 1F, 1F, 1F),
                    SuitGlowLayer.tint(SuitGlowLayer.barrelGlow(heat), 1F, 1F, 1F),
                    SuitGlowLayer.tint(suit.getEmCoreGlow(time), 1F, 1F, 1F),
                    SuitGlowLayer.tint(suit.getEmArcGlow(time), 1F, 1F, 1F)});
        }
    }

    /** No centring in a block (GeoObjectRenderer's default translates by 0.5): CockpitHands places the model itself. */
    @Override
    public void adjustRenderPose(RenderPassInfo<GeoRenderState> info) {
    }

    @Override
    public void adjustModelBonesForRender(RenderPassInfo<GeoRenderState> info, BoneSnapshots bones) {
        boolean tool = info.getOrDefaultGeckolibData(PART, Part.TOOL) == Part.TOOL;
        bones.ifPresent("view_tool", b -> b.skipRender(!tool).skipChildrenRender(!tool));
        bones.ifPresent("view_gauntlet", b -> b.skipRender(tool).skipChildrenRender(tool));
        String installed = info.getOrDefaultGeckolibData(TOOL_BONE, "");
        for (String name : TOOL_BONES) {
            boolean show = name.equals(installed);
            bones.ifPresent(name, b -> b.skipRender(!show).skipChildrenRender(!show));
        }
        boolean bare = installed.isEmpty();
        bones.ifPresent("socket", b -> b.skipRender(!bare).skipChildrenRender(!bare));
        ChaingunParts.pose(bones, info.getOrDefaultGeckolibData(BARREL_ANGLE, 0F)); // turns with the suit's spin
    }

    /** Both faces, like GeckoLib's default: in this render path the cubes wind the other way, so culling hid them all. */
    @Override
    public @Nullable RenderType getRenderType(GeoRenderState state, Identifier texture) {
        return RenderTypes.entityCutout(texture);
    }

    private static final class Model extends GeoModel<CockpitView> {
        // GeckoLib 5 ids: assets/powerarmor/geckolib/models/<id>.geo.json and geckolib/animations/<id>.animation.json.
        private static final Identifier GEO = Identifier.fromNamespaceAndPath(PowerArmor.MODID, "view/power_armor_view");
        private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(PowerArmor.MODID, "textures/entity/power_armor.png");
        private static final Identifier ANIMATIONS = Identifier.fromNamespaceAndPath(PowerArmor.MODID, "entity/power_armor");

        @Override
        public Identifier getModelResource(GeoRenderState state) {
            return GEO;
        }

        @Override
        public Identifier getTextureResource(GeoRenderState state) {
            return TEXTURE;
        }

        @Override
        public Identifier getAnimationResource(CockpitView view) {
            return ANIMATIONS;
        }
    }

    private static final class ToolGlow extends GeoRenderLayer<CockpitView, Void, GeoRenderState> {
        ToolGlow(CockpitViewRenderer renderer) {
            super(renderer);
        }

        @Override
        public void submitRenderTask(RenderPassInfo<GeoRenderState> info, SubmitNodeCollector renderTasks) {
            int[] tints = info.getGeckolibData(GLOW);
            if (info.willRender() && tints != null && info.getOrDefaultGeckolibData(PART, Part.TOOL) == Part.TOOL) {
                SuitGlowLayer.drawMasks(this.renderer, info, renderTasks, MASKS, tints);
            }
        }
    }
}
