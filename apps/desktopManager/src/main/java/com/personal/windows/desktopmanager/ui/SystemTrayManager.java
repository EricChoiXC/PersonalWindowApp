package com.personal.windows.desktopmanager.ui;

import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.AWTException;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.net.URL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.personal.windows.desktopmanager.util.FontUtil;

public class SystemTrayManager {

    private static final Logger log = LoggerFactory.getLogger(SystemTrayManager.class);

    private final Runnable onShowWindow;
    private final Runnable onExit;

    private TrayIcon trayIcon;

    public SystemTrayManager(Runnable onShowWindow, Runnable onExit) {
        this.onShowWindow = onShowWindow;
        this.onExit = onExit;
    }

    public void createTrayIcon() {
        if (!SystemTray.isSupported()) {
            log.warn("系统托盘不支持");
            return;
        }

        Image iconImage = createTrayImage();
        trayIcon = new TrayIcon(iconImage, FontUtil.text("桌面管家", "Desktop Manager"));
        trayIcon.setImageAutoSize(true);

        trayIcon.setPopupMenu(createPopupMenu());

        trayIcon.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    onShowWindow.run();
                }
            }
        });

        try {
            SystemTray.getSystemTray().add(trayIcon);
            log.info("系统托盘图标已创建");
        } catch (AWTException e) {
            log.error("创建系统托盘失败", e);
        }
    }

    public void removeTrayIcon() {
        if (trayIcon != null && SystemTray.isSupported()) {
            SystemTray.getSystemTray().remove(trayIcon);
            log.info("系统托盘图标已移除");
        }
    }

    private PopupMenu createPopupMenu() {
        PopupMenu popupMenu = new PopupMenu();

        MenuItem showItem = new MenuItem(FontUtil.text("主窗口", "Show Window"));
        showItem.addActionListener(e -> onShowWindow.run());

        MenuItem exitItem = new MenuItem(FontUtil.text("退出", "Exit"));
        exitItem.addActionListener(e -> onExit.run());

        popupMenu.add(showItem);
        popupMenu.addSeparator();
        popupMenu.add(exitItem);

        return popupMenu;
    }

    private Image createTrayImage() {
        URL iconUrl = getClass().getResource("/images/app-icon.png");
        if (iconUrl != null) {
            return Toolkit.getDefaultToolkit().getImage(iconUrl);
        }
        return createDefaultIcon();
    }

    private Image createDefaultIcon() {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = image.createGraphics();
        g.setColor(new java.awt.Color(70, 130, 180));
        g.fillRect(0, 0, 16, 16);
        g.setColor(java.awt.Color.WHITE);
        g.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 11));
        g.drawString("D", 2, 13);
        g.dispose();
        return image;
    }
}
