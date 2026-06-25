package com.personal.windows.desktopmanager.util;

import java.awt.Font;
import java.awt.GraphicsEnvironment;

public final class FontUtil {

    private static final String[] CJK_CANDIDATES = {
            "Microsoft YaHei", "SimSun", "SimHei", "FangSong", "KaiTi",
            "Microsoft JhengHei", "NSimSun", "DengXian", "YouYuan"
    };

    private static volatile Boolean cjkAvailable = null;

    private FontUtil() {
    }

    /**
     * 检测系统是否存在中文字体，结果缓存避免重复遍历字体列表。
     */
    public static boolean isCjkFontAvailable() {
        if (cjkAvailable != null) {
            return cjkAvailable;
        }
        synchronized (FontUtil.class) {
            if (cjkAvailable != null) {
                return cjkAvailable;
            }
            Font[] allFonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts();
            for (String candidate : CJK_CANDIDATES) {
                for (Font f : allFonts) {
                    if (candidate.equalsIgnoreCase(f.getFontName())
                            || candidate.equalsIgnoreCase(f.getFamily())) {
                        cjkAvailable = true;
                        return true;
                    }
                }
            }
            cjkAvailable = false;
            return false;
        }
    }

    /**
     * 如果系统中存在中文字体则返回中文文本，否则回退为英文文本。
     */
    public static String text(String chinese, String english) {
        return isCjkFontAvailable() ? chinese : english;
    }
}
