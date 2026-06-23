package com.personal.windows.desktopmanager;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.personal.windows.desktopmanager.config.AppConfig;
import com.personal.windows.desktopmanager.model.DesktopFileVo;
import com.personal.windows.desktopmanager.service.IConfigService;
import com.personal.windows.desktopmanager.service.IDesktopIconService;
import com.personal.windows.desktopmanager.service.IGroupService;
import com.personal.windows.desktopmanager.service.impl.ConfigServiceImpl;
import com.personal.windows.desktopmanager.service.impl.DesktopIconServiceImpl;
import com.personal.windows.desktopmanager.service.impl.GroupServiceImpl;
import com.personal.windows.desktopmanager.ui.IconManageController;
import com.personal.windows.desktopmanager.ui.SystemTrayManager;

public class DesktopManagerApplication extends Application {

    private static final Logger log = LoggerFactory.getLogger(DesktopManagerApplication.class);

    private IConfigService configService;
    private IDesktopIconService desktopIconService;
    private IGroupService groupService;

    private SystemTrayManager trayManager;
    private IconManageController iconManageController;
    private Stage mainStage;

    @Override
    public void start(Stage primaryStage) {
        log.info("DesktopManager 启动中...");

        initServices();
        initTrayAndWindow(primaryStage);

        CompletableFuture.runAsync(this::initializeApp);

        log.info("DesktopManager 启动完成");
    }

    private void initServices() {
        configService = new ConfigServiceImpl();
        desktopIconService = new DesktopIconServiceImpl();
        groupService = new GroupServiceImpl(configService, desktopIconService);
    }

    private void initTrayAndWindow(Stage primaryStage) {
        createMainWindow(primaryStage);

        trayManager = new SystemTrayManager(
                desktopIconService,
                this::showMainWindow,
                this::exitApplication);
        trayManager.createTrayIcon();
    }

    private void createMainWindow(Stage primaryStage) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/IconManage.fxml"));
            Parent root = loader.load();

            iconManageController = loader.getController();
            iconManageController.setGroupService(groupService);
            iconManageController.setStage(primaryStage);

            Scene scene = new Scene(root, AppConfig.getDefaultWidth(), AppConfig.getDefaultHeight());

            primaryStage.setTitle("桌面管家 - 图标管理");
            primaryStage.getIcons().add(new Image(
                    getClass().getResourceAsStream("/images/app-icon.png")));
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(AppConfig.getMinWidth());
            primaryStage.setMinHeight(AppConfig.getMinHeight());

            primaryStage.setOnCloseRequest(e -> {
                e.consume();
                primaryStage.hide();
            });

            this.mainStage = primaryStage;

            log.info("主窗口已创建");
        } catch (Exception e) {
            log.error("创建主窗口失败", e);
        }
    }

    private void initializeApp() {
        try {
            desktopIconService.hideDesktopIcons();

            List<DesktopFileVo> files = desktopIconService.scanDesktopFiles();
            String defaultGroupId = groupService.getDefaultGroupId();
            for (DesktopFileVo file : files) {
                try {
                    desktopIconService.hideFile(file.getFilePath());
                    configService.setFileGroup(file.getFilePath(), defaultGroupId);
                } catch (Exception e) {
                    log.warn("隐藏文件失败: {}", file.getFilePath(), e);
                }
            }
            log.info("初始扫描完成，共 {} 个文件", files.size());

            desktopIconService.startFileWatcher(file -> {
                String gid = groupService.getDefaultGroupId();
                configService.setFileGroup(file.getFilePath(), gid);
                if (iconManageController != null) {
                    iconManageController.onNewFileDetected(file);
                }
                log.debug("新文件已归属默认分组: {}", file.getFileName());
            });

        } catch (Exception e) {
            log.error("应用初始化失败", e);
        }
    }

    private void showMainWindow() {
        if (mainStage == null) {
            return;
        }
        Platform.runLater(() -> {
            if (iconManageController != null) {
                iconManageController.loadData();
            }
            if (mainStage.isShowing()) {
                mainStage.toFront();
            } else {
                mainStage.show();
            }
        });
    }

    private void exitApplication() {
        log.info("正在退出 DesktopManager...");

        CompletableFuture.runAsync(() -> {
            try {
                desktopIconService.showDesktopIcons();
            } catch (Exception e) {
                log.error("恢复桌面图标失败", e);
            }
            desktopIconService.stopFileWatcher();
        }).thenRun(() -> Platform.runLater(() -> {
            trayManager.removeTrayIcon();
            if (mainStage != null) {
                mainStage.setOnCloseRequest(null);
                mainStage.close();
            }
            Platform.exit();
        })).whenComplete((v, ex) -> {
            if (ex != null) {
                log.error("退出过程异常", ex);
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {
            }
            System.exit(0);
        });
    }

    @Override
    public void stop() {
        log.info("Application.stop() 被调用");
        if (desktopIconService != null) {
            desktopIconService.stopFileWatcher();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
