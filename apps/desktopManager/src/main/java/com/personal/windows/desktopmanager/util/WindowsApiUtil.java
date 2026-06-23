package com.personal.windows.desktopmanager.util;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.LRESULT;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.ptr.IntByReference;

import com.personal.windows.desktopmanager.exception.DesktopIconException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WindowsApiUtil {

    private static final Logger log = LoggerFactory.getLogger(WindowsApiUtil.class);

    private static final int HWND_BROADCAST = 0xFFFF;
    private static final int WM_SETTINGCHANGE = 0x001A;
    private static final int SMTO_ABORTIFHUNG = 0x0002;
    private static final int FILE_ATTRIBUTE_HIDDEN = 0x2;

    private static final String EXPLORER_ADVANCED_KEY =
            "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Advanced";
    private static final String HIDE_ICONS_VALUE = "HideIcons";

    private static final String USER_SHELL_FOLDERS_KEY =
            "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\User Shell Folders";

    private static final String AUTO_RUN_KEY =
            "Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    private static final String AUTO_RUN_VALUE_NAME = "PersonalDesktopManager";

    private WindowsApiUtil() {
    }

    interface User32Lib extends Library {
        User32Lib INSTANCE = Native.load("user32", User32Lib.class);

        LRESULT SendMessageTimeoutW(HWND hWnd, int msg, WPARAM wParam, LPARAM lParam,
                                    int fuFlags, int uTimeout, IntByReference lpdwResult);
    }

    interface Kernel32Lib extends Library {
        Kernel32Lib INSTANCE = Native.load("kernel32", Kernel32Lib.class);

        boolean SetFileAttributesW(String lpFileName, int dwFileAttributes);
    }

    public static void hideDesktopIcons() {
        try {
            Advapi32Util.registrySetIntValue(
                    WinReg.HKEY_CURRENT_USER, EXPLORER_ADVANCED_KEY, HIDE_ICONS_VALUE, 1);
            broadcastSettingChange();
            log.info("桌面图标已隐藏");
        } catch (Exception e) {
            throw new DesktopIconException("隐藏桌面图标失败", e);
        }
    }

    public static void showDesktopIcons() {
        try {
            Advapi32Util.registrySetIntValue(
                    WinReg.HKEY_CURRENT_USER, EXPLORER_ADVANCED_KEY, HIDE_ICONS_VALUE, 0);
            broadcastSettingChange();
            log.info("桌面图标已恢复显示");
        } catch (Exception e) {
            throw new DesktopIconException("恢复桌面图标显示失败", e);
        }
    }

    public static void hideFile(String filePath) {
        try {
            boolean result = Kernel32Lib.INSTANCE.SetFileAttributesW(filePath, FILE_ATTRIBUTE_HIDDEN);
            if (!result) {
                throw new DesktopIconException("设置文件隐藏属性失败: " + filePath);
            }
        } catch (Exception e) {
            if (e instanceof DesktopIconException) {
                throw (DesktopIconException) e;
            }
            throw new DesktopIconException("设置文件隐藏属性失败: " + filePath, e);
        }
    }

    public static String getDesktopPathFromRegistry() {
        try {
            String path = Advapi32Util.registryGetStringValue(
                    WinReg.HKEY_CURRENT_USER, USER_SHELL_FOLDERS_KEY, "Desktop");
            if (path != null && !path.isBlank()) {
                path = resolveEnvVars(path);
                return path;
            }
        } catch (Exception e) {
            log.warn("从注册表读取桌面路径失败，使用默认路径", e);
        }
        return null;
    }

    public static void setAutoStart(boolean enable) {
        try {
            if (enable) {
                String exePath = System.getProperty("java.home") + "\\bin\\javaw.exe";
                String jarPath = System.getProperty("java.class.path");
                String command = "\"" + exePath + "\" -jar \"" + jarPath + "\"";
                Advapi32Util.registrySetStringValue(
                        WinReg.HKEY_CURRENT_USER, AUTO_RUN_KEY, AUTO_RUN_VALUE_NAME, command);
            } else {
                if (Advapi32Util.registryValueExists(
                        WinReg.HKEY_CURRENT_USER, AUTO_RUN_KEY, AUTO_RUN_VALUE_NAME)) {
                    Advapi32Util.registryDeleteValue(
                            WinReg.HKEY_CURRENT_USER, AUTO_RUN_KEY, AUTO_RUN_VALUE_NAME);
                }
            }
        } catch (Exception e) {
            log.warn("设置自启动失败", e);
        }
    }

    private static void broadcastSettingChange() {
        HWND hwndBroadcast = new HWND(Pointer.createConstant(HWND_BROADCAST));
        WPARAM wParam = new WPARAM(0);
        LPARAM lParam = new LPARAM(0);
        IntByReference result = new IntByReference();

        User32Lib.INSTANCE.SendMessageTimeoutW(
                hwndBroadcast, WM_SETTINGCHANGE, wParam, lParam,
                SMTO_ABORTIFHUNG, 5000, result);
    }

    private static String resolveEnvVars(String path) {
        if (path == null) {
            return null;
        }
        if (path.contains("%")) {
            path = path.replace("%USERPROFILE%", System.getProperty("user.home"));
        }
        return path;
    }
}
