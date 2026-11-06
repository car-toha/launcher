package ru.mobilecraft.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Микрофон-переключатель для Simple Voice Chat.
 *
 * SVC переводится в режим Push-To-Talk (см. VoiceChatDefaults), а мы «держим»
 * его клавишу PTT, пока микрофон включён. Нажал — говоришь, нажал ещё раз — тишина.
 * Состояние подтверждаем каждый тик: ваниль отпускает все клавиши при открытии
 * экранов (инвентарь, чат), и без этого микрофон бы самопроизвольно выключался.
 */
public final class MicToggle {

    private static final String SVC_PTT_KEY = "key.push_to_talk";

    private static boolean on = false;
    private static KeyMapping ptt = null;

    private MicToggle() {}

    public static boolean isOn() { return on; }

    public static void toggle(Minecraft mc) {
        KeyMapping key = findPtt(mc);
        if (key == null) {
            on = false;
            if (mc.player != null)
                mc.player.sendOverlayMessage(Component.translatable("mobilecraft.mic.missing"));
            return;
        }
        on = !on;
        key.setDown(on);
        if (mc.player != null)
            mc.player.sendOverlayMessage(Component.translatable(on ? "mobilecraft.mic.on" : "mobilecraft.mic.off"));
    }

    public static void tick(Minecraft mc) {
        if (!on) return;
        // Вышли с сервера — выключаем, чтобы при следующем входе не говорить случайно
        if (mc.player == null) {
            on = false;
            KeyMapping key = findPtt(mc);
            if (key != null) key.setDown(false);
            return;
        }
        KeyMapping key = findPtt(mc);
        if (key != null && !key.isDown()) key.setDown(true);
    }

    private static KeyMapping findPtt(Minecraft mc) {
        if (ptt != null) return ptt;
        if (mc.options == null) return null;
        for (KeyMapping k : mc.options.keyMappings) {
            String name = k.getName();
            if (SVC_PTT_KEY.equals(name) || name.endsWith("push_to_talk")) {
                ptt = k;
                break;
            }
        }
        return ptt;
    }

    /** Красная точка «REC» справа от чата, пока микрофон включён. */
    public static void renderIndicator(GuiGraphicsExtractor g, DeltaTracker dt) {
        if (!on) return;
        Minecraft mc = Minecraft.getInstance();
        int x = Math.round(TopCenterChat.Layout.chatRight) + 4;
        int y = Math.round(TopCenterChat.Layout.chatTop) + 2;
        boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
        g.fill(x, y, x + 6, y + 6, blink ? 0xFFFF3030 : 0xFFAA2020);
        g.text(mc.font, "MIC", x + 9, y - 1, 0xFFFFFFFF, true);
    }
}
