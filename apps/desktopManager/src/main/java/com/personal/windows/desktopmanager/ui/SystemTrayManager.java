package com.personal.windows.desktopmanager.ui;

import java.awt.AWTException;
import java.awt.CheckboxMenuItem;
import java.awt.Image;
import java.awt.Menu;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.net.URL;

import com.personal.windows.desktopmanager.service.IDesktopIconService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SystemTrayManager {

    private static final Logger log = LoggerFactory.getLogger(SystemTrayManager.class);

    private final IDesktopIconService desktopIconService;
    private final Runnable onShowWindow;
    private final Runnable onExit;

    private TrayIcon trayIcon;

    public SystemTrayManager(IDesktopIconService desktopIconService,
                             Runnable onShowWindow, Runnable onExit) {
        this.desktopIconService = desktopIconService;
        this.onShowWindow = onShowWindow;
        this.onExit = onExit;
    }

    public void createTrayIcon() {
        if (!SystemTray.isSupported()) {
            log.warn("系统托盘不支持");
            return;
        }

        SystemTray tray = SystemTray.getSystemTray();

        PopupMenu popup = new PopupMenu();

        MenuItem showItem = new MenuItem("图标");
        showItem.addActionListener(e -> onShowWindow.run());
        popup.add(showItem);

        popup.addSeparator();

        MenuItem exitItem = new MenuItem("退出");
        exitItem.addActionListener(e -> onExit.run());
        popup.add(exitItem);

        Image iconImage = createTrayImage();
        trayIcon = new TrayIcon(iconImage, "桌面管家", popup);
        trayIcon.setImageAutoSize(true);

        trayIcon.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    onShowWindow.run();
                }
            }
        });

        try {
            tray.add(trayIcon);
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
