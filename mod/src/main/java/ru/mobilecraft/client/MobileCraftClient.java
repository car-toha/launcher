package ru.mobilecraft.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Точка входа клиента.
 *
 * Как устроено управление: сенсорные кнопки рисует и обрабатывает лаунчер
 * (у него есть мультитач, у игры — только одна «мышь»). Кнопки чата лаунчер
 * отправляет в игру как клавиши, которых нет на обычной клавиатуре:
 *   F13 — повторить последнюю команду
 *   F14 — микрофон вкл/выкл
 * Мод ловит эти клавиши, двигает и уменьшает чат и рисует индикатор микрофона.
 */
public final class MobileCraftClient implements ClientModInitializer {

    public static final String MOD_ID = "mobilecraft";

    public static final int KEY_REPEAT_COMMAND = GLFW.GLFW_KEY_F13;
    public static final int KEY_TOGGLE_MIC = GLFW.GLFW_KEY_F14;

    private final EdgeKey repeatKey = new EdgeKey(KEY_REPEAT_COMMAND);
    private final EdgeKey micKey = new EdgeKey(KEY_TOGGLE_MIC);

    @Override
    public void onInitializeClient() {
        LastCommand.load();

        // Запоминаем каждую отправленную команду (без начального '/')
        ClientSendMessageEvents.COMMAND.register(LastCommand::remember);

        // Мобильные настройки по умолчанию — один раз, после загрузки options.txt
        ClientLifecycleEvents.CLIENT_STARTED.register(MobileDefaults::applyOnce);

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);

        // Чат: меньше и сверху по центру
        HudElementRegistry.replaceElement(VanillaHudElements.CHAT, TopCenterChat::wrap);

        // Индикатор микрофона рядом с чатом
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(MOD_ID, "mic_indicator"),
                MicToggle::renderIndicator);
    }

    private void onTick(Minecraft mc) {
        if (mc.getWindow() == null) return;
        long handle = mc.getWindow().handle();

        if (repeatKey.pressed(handle)) LastCommand.repeat(mc);
        if (micKey.pressed(handle)) MicToggle.toggle(mc);

        MicToggle.tick(mc);
    }

    /** Срабатывает один раз на нажатие (опрос состояния клавиши каждый тик). */
    private static final class EdgeKey {
        private final int key;
        private boolean wasDown;

        EdgeKey(int key) { this.key = key; }

        boolean pressed(long window) {
            boolean down = GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;
            boolean edge = down && !wasDown;
            wasDown = down;
            return edge;
        }
    }
}
