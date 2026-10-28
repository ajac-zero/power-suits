package dev.ajaczero.powerarmor.client;

import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * A key in the suit menu (user's pick B, 2026-10-27): a square button the size of a slot with a symbol on it instead of
 * a caption, vanilla's button underneath (the same sprites, so it lights up under the mouse like any button). A latching
 * key (the Power key) stays pushed in while its state is on, like an old machine's push-on/push-off switch: darker, a
 * pixel lower, its symbol and the pip under it green; out, the symbol grey and the pip dark red. A plain key (Pack up)
 * just clicks. Inactive, it's vanilla's disabled button with a faded symbol.
 * <p>
 * A widget is a piece of a screen that draws itself and takes clicks: the screen adds it once (addRenderableWidget) and
 * Minecraft asks it to draw every frame and tells it when it's clicked. The state it shows is read every frame from
 * {@code latched}, so it follows the suit (the switch flips on the server, the H key, running dry).
 */
final class SuitKey extends AbstractButton {
    static final int SIZE = 18;
    private static final int SYMBOL_W = 9, SYMBOL_H = 8, SYMBOL_X = 4, SYMBOL_Y = 4;
    /** The pushed-in key: vanilla's button at 72% brightness, under a 1 px shadow where it sank. */
    private static final int PRESSED_TINT = 0xFFB8B8B8, SHADOW = 0xFF373737;
    private static final int INK = 0xFF3C3C3C, INK_INACTIVE = 0xFF464646, LIT = 0xFF7CFF6B, PIP_OFF = 0xFF501E1E;

    private final Identifier symbol;
    @Nullable
    private final BooleanSupplier latched;
    private final Runnable action;

    SuitKey(int x, int y, Identifier symbol, Component name, @Nullable BooleanSupplier latched, Runnable action) {
        super(x, y, SIZE, SIZE, name);
        this.symbol = symbol;
        this.latched = latched;
        this.action = action;
    }

    boolean isDown() {
        return this.latched != null && this.latched.getAsBoolean();
    }

    @Override
    public void onPress(InputWithModifiers input) {
        this.action.run();
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        int x = this.getX(), y = this.getY();
        boolean down = this.isDown();
        Identifier button = SPRITES.get(this.active, this.isHoveredOrFocused());
        if (down) {
            g.fill(x, y, x + SIZE, y + 1, SHADOW);
            g.blitSprite(RenderPipelines.GUI_TEXTURED, button, x, y + 1, SIZE, SIZE - 1, PRESSED_TINT);
        } else {
            this.extractDefaultSprite(g);
        }
        int dy = down ? 1 : 0;
        int ink = !this.active ? INK_INACTIVE : down ? LIT : INK;
        // the symbols are white masks (tools/make-gui.py): blitSprite's colour tints them
        g.blitSprite(RenderPipelines.GUI_TEXTURED, this.symbol, x + SYMBOL_X, y + SYMBOL_Y + dy, SYMBOL_W, SYMBOL_H, ink);
        if (this.latched != null) {
            g.fill(x + 7, y + 13 + dy, x + 11, y + 14 + dy, down ? LIT : PIP_OFF);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
