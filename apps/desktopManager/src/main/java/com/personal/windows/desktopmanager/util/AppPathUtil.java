package com.personal.windows.desktopmanager.util;

import java.nio.file.Files;
import java.nio.file.Path;

public final class AppPathUtil {

    private static final String APP_NAME = "PersonalDesktopManager";

    private AppPathUtil() {
    }

    public static Path getAppDataDir() {
        String appData = System.getenv("APPDATA");
        if (appData == null || appData.isBlank()) {
            appData = System.getProperty("user.home") + "\\AppData\\Roaming";
        }
        return Path.of(appData, APP_NAME);
    }

    public static Path getConfigFilePath() {
        return getAppDataDir().resolve("config.json");
    }

    public static void ensureAppDataDir() {
        try {
            Path dir = getAppDataDir();
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
        } catch (Exception e) {
            throw new com.personal.windows.desktopmanager.exception.ConfigException("无法创建应用数据目录", e);
        }
    }
}
