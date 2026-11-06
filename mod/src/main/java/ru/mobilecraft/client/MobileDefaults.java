package ru.mobilecraft.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Мобильные настройки по умолчанию. Применяются один раз при первом запуске,
 * дальше игрок может менять их как хочет.
 */
public final class MobileDefaults {

    private static final Path FLAG =
            FabricLoader.getInstance().getConfigDir().resolve("mobilecraft").resolve("defaults_applied");

    private MobileDefaults() {}

    public static void applyOnce(Minecraft mc) {
        if (Files.exists(FLAG)) return;
        Options o = mc.options;

        o.autoJump().set(true);              // как в Bedrock на телефоне
        o.chatWidth().set(0.55);             // узкий чат
        o.chatHeightUnfocused().set(0.30);   // ~4–5 строк в игре
        o.chatHeightFocused().set(0.55);     // больше строк, когда чат открыт
        o.chatLineSpacing().set(0.0);
        o.textBackgroundOpacity().set(0.35);
        o.pauseOnLostFocus = false;          // свернул лаунчер — не пауза
        o.save();

        try {
            Files.createDirectories(FLAG.getParent());
            Files.writeString(FLAG, "1");
        } catch (IOException ignored) {}
    }
}
