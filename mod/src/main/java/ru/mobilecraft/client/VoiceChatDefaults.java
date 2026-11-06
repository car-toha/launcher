package ru.mobilecraft.client;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Запускается до загрузки игры (preLaunch), чтобы Simple Voice Chat прочитал уже
 * исправленный конфиг: режим Push-To-Talk, которым управляет кнопка-переключатель.
 */
public final class VoiceChatDefaults implements PreLaunchEntrypoint {

    private static final String KEY = "microphone_activation_type";

    @Override
    public void onPreLaunch() {
        Path file = FabricLoader.getInstance().getConfigDir()
                .resolve("voicechat").resolve("voicechat-client.properties");
        try {
            Files.createDirectories(file.getParent());
            List<String> lines = Files.exists(file)
                    ? new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8))
                    : new ArrayList<>();
            boolean found = false;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).startsWith(KEY + "=")) {
                    lines.set(i, KEY + "=PTT");
                    found = true;
                }
            }
            if (!found) lines.add(KEY + "=PTT");
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException ignored) {}
    }
}
