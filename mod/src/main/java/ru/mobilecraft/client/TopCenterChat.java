package ru.mobilecraft.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;

/**
 * Перерисовывает ванильный чат со сдвигом и масштабом: компактно, сверху по центру.
 *
 * Ванильный чат рисуется в левом нижнем углу: низ на (высота − 40),
 * ширина chat.getWidth(), высота chat.getHeight(), всё умножено на chatScale.
 * Мы берём этот прямоугольник и аффинно переносим его наверх по центру.
 */
public final class TopCenterChat {

    /** Дополнительное уменьшение поверх ванильного chatScale. */
    public static float EXTRA_SCALE = 0.8f;
    /** Отступ сверху в GUI-пикселях (под боссбаром держим запас). */
    public static int TOP_MARGIN = 4;

    private TopCenterChat() {}

    public static HudElement wrap(HudElement vanilla) {
        return (graphics, deltaTracker) -> {
            Minecraft mc = Minecraft.getInstance();
            float guiW = mc.getWindow().getGuiScaledWidth();
            float guiH = mc.getWindow().getGuiScaledHeight();
            float chatScale = mc.options.chatScale().get().floatValue();
            boolean focused = mc.screen instanceof ChatScreen;
            int widthPx = ChatComponent.getWidth(mc.options.chatWidth().get());
            int heightPx = ChatComponent.getHeight(focused
                    ? mc.options.chatHeightFocused().get()
                    : mc.options.chatHeightUnfocused().get());

            // Прямоугольник ванильного чата в GUI-координатах
            float chatW = (widthPx + 12) * chatScale;
            float chatTop = guiH - 40 - heightPx * chatScale;

            float s = EXTRA_SCALE;
            float tx = (guiW - s * chatW) / 2f;
            float ty = TOP_MARGIN - s * chatTop;

            graphics.pose().pushMatrix();
            graphics.pose().translate(tx, ty);
            graphics.pose().scale(s, s);
            vanilla.extractRenderState(graphics, deltaTracker);
            graphics.pose().popMatrix();

            Layout.chatLeft = tx;
            Layout.chatRight = tx + s * chatW;
            Layout.chatTop = TOP_MARGIN;
        };
    }

    /** Последняя вычисленная геометрия — нужна индикатору микрофона. */
    public static final class Layout {
        public static float chatLeft, chatRight, chatTop;
        private Layout() {}
    }
}
