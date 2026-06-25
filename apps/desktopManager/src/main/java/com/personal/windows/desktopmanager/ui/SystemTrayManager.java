package com.personal.windows.desktopmanager.ui;

import java.awt.AWTException;
import java.awt.Image;
import java.awt.MouseInfo;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.net.URL;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.stage.Stage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SystemTrayManager {

    private static final Logger log = LoggerFactory.getLogger(SystemTrayManager.class);

    private final Runnable onShowWindow;
    private final Runnable onExit;
    private final Stage ownerStage;

    private TrayIcon trayIcon;

    public SystemTrayManager(Runnable onShowWindow, Runnable onExit, Stage ownerStage) {
        this.onShowWindow = onShowWindow;
        this.onExit = onExit;
        this.ownerStage = ownerStage;
    }

    public void createTrayIcon() {
        if (!SystemTray.isSupported()) {
            log.warn("系统托盘不支持");
            return;
        }

        Image iconImage = createTrayImage();
        trayIcon = new TrayIcon(iconImage, "桌面管家", null);
        trayIcon.setImageAutoSize(true);

        trayIcon.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    onShowWindow.run();
                } else if (e.getButton() == MouseEvent.BUTTON3) {
                    showContextMenu(e);
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

    private void showContextMenu(MouseEvent e) {
        double screenX = e.getXOnScreen();
        double screenY = e.getYOnScreen();
        if (screenX <= 0 && screenY <= 0) {
            java.awt.PointerInfo pi = MouseInfo.getPointerInfo();
            if (pi != null) {
                final double fx = pi.getLocation().x;
                final double fy = pi.getLocation().y;
                Platform.runLater(() -> showJavaFXMenu(fx, fy));
                return;
            }
        }
        final double fx = screenX;
        final double fy = screenY;
        Platform.runLater(() -> showJavaFXMenu(fx, fy));
    }

    private void showJavaFXMenu(double screenX, double screenY) {
        Node anchor = ownerStage.getScene() != null ? ownerStage.getScene().getRoot() : null;
        if (anchor == null) {
            log.warn("无法获取 JavaFX Scene 根节点，右键菜单无法显示");
            return;
        }

        ContextMenu menu = new ContextMenu();
        menu.setStyle("-fx-font-family: 'Microsoft YaHei', 'SimSun', 'SimHei', sans-serif;");

        MenuItem showItem = new MenuItem("图标");
        showItem.setOnAction(ev -> onShowWindow.run());

        MenuItem exitItem = new MenuItem("退出");
        exitItem.setOnAction(ev -> onExit.run());

        menu.getItems().addAll(showItem, new SeparatorMenuItem(), exitItem);
        menu.show(anchor, screenX, screenY);
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
