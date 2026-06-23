package com.personal.windows.desktopmanager.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);
    private static final Properties props = new Properties();

    static {
        try (InputStream is = AppConfig.class.getResourceAsStream("/application.properties")) {
            if (is != null) {
                props.load(is);
            }
        } catch (IOException e) {
            log.error("加载配置文件失败", e);
        }
    }

    public static String getAppTitle() {
        return props.getProperty("app.name", "DesktopManager");
    }

    public static int getDefaultWidth() {
        return Integer.parseInt(props.getProperty("app.window.width", "800"));
    }

    public static int getDefaultHeight() {
        return Integer.parseInt(props.getProperty("app.window.height", "600"));
    }

    public static int getMinWidth() {
        return Integer.parseInt(props.getProperty("app.window.minwidth", "400"));
    }

    public static int getMinHeight() {
        return Integer.parseInt(props.getProperty("app.window.minheight", "300"));
    }
}
