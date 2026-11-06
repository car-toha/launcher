package ru.mobilecraft.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Хранит последнюю отправленную команду и повторяет её по кнопке. Переживает перезапуск. */
public final class LastCommand {

    private static final Path FILE =
            FabricLoader.getInstance().getConfigDir().resolve("mobilecraft").resolve("last_command.txt");

    private static String last = null;

    private LastCommand() {}

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                String s = Files.readString(FILE, StandardCharsets.UTF_8).strip();
                if (!s.isEmpty()) last = s;
            }
        } catch (IOException ignored) {}
    }

    public static void remember(String command) {
        if (command == null || command.isBlank()) return;
        last = command.strip();
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, last, StandardCharsets.UTF_8);
        } catch (IOException ignored) {}
    }

    public static void repeat(Minecraft mc) {
        if (mc.player == null || mc.getConnection() == null) return;
        if (last == null) {
            mc.player.sendOverlayMessage(Component.translatable("mobilecraft.cmd.none"));
            return;
        }
        mc.getConnection().sendCommand(last);
        mc.player.sendOverlayMessage(Component.translatable("mobilecraft.cmd.sent", last));
    }
}
