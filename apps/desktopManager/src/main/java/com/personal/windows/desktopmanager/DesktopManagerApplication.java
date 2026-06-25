package com.personal.windows.desktopmanager;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

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
import com.personal.windows.desktopmanager.util.AppPathUtil;

public class DesktopManagerApplication extends Application {

    private static final Logger log = LoggerFactory.getLogger(DesktopManagerApplication.class);

    private static FileLock instanceLock;
    private static FileChannel lockChannel;
    private static DesktopManagerApplication instance;
    private final AtomicBoolean cleanupDone = new AtomicBoolean(false);

    private IConfigService configService;
    private IDesktopIconService desktopIconService;
    private IGroupService groupService;

    private SystemTrayManager trayManager;
    private IconManageController iconManageController;
    private Stage mainStage;

    @Override
    public void start(Stage primaryStage) {
        log.info("DesktopManager 启动中...");
        instance = this;

        Platform.setImplicitExit(false);

        initServices();
        initTrayAndWindow(primaryStage);

        primaryStage.show();
        log.info("主界面已显示");

        CompletableFuture.runAsync(() -> {
            initializeApp();
            Platform.runLater(() -> iconManageController.loadData());
        });

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
                this::showMainWindow,
                this::exitApplication,
                primaryStage);
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

            groupService.listGroups();

            List<DesktopFileVo> files = desktopIconService.scanDesktopFiles();
            String defaultGroupId = groupService.getDefaultGroupId();
            int hiddenCount = 0;
            for (DesktopFileVo file : files) {
                try {
                    desktopIconService.hideFile(file.getFilePath());
                    configService.setFileGroup(file.getFilePath(), defaultGroupId);
                    hiddenCount++;
                } catch (Exception e) {
                    log.warn("隐藏文件失败: {}", file.getFilePath(), e);
                }
            }
            log.info("初始扫描完成，已隐藏 {}/{} 个文件", hiddenCount, files.size());

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
            try {
                if (iconManageController != null) {
                    iconManageController.loadData();
                }
            } catch (Exception e) {
                log.error("刷新数据失败", e);
            }
            mainStage.show();
            mainStage.toFront();
        });
    }

    private void exitApplication() {
        log.info("正在退出 DesktopManager...");

        doCleanup();

        CompletableFuture.runAsync(() -> {
            cleanupDone.set(true);
            desktopIconService.stopFileWatcher();
            releaseInstanceLock();
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
        doCleanup();
        if (desktopIconService != null) {
            desktopIconService.stopFileWatcher();
        }
        releaseInstanceLock();
    }

    private void doCleanup() {
        if (!cleanupDone.compareAndSet(false, true)) {
            return;
        }
        try {
            if (desktopIconService != null) {
                desktopIconService.showDesktopIcons();
                desktopIconService.unhideAllHiddenFiles();
            }
        } catch (Exception e) {
            log.error("恢复桌面图标失败", e);
        }
    }

    private static void staticCleanup() {
        if (instance != null) {
            instance.doCleanup();
        }
        releaseInstanceLock();
    }

    private static void releaseInstanceLock() {
        try {
            if (instanceLock != null && instanceLock.isValid()) {
                instanceLock.release();
            }
            if (lockChannel != null && lockChannel.isOpen()) {
                lockChannel.close();
            }
        } catch (Exception e) {
            log.warn("释放实例锁失败", e);
        }
    }

    public static void main(String[] args) {
        System.setProperty("file.encoding", "UTF-8");
        System.setProperty("sun.jnu.encoding", "UTF-8");
        System.setProperty("awt.useSystemAAFontSettings", "on");

        if (!acquireInstanceLock()) {
            showAlreadyRunningDialog();
            return;
        }

        Runtime.getRuntime().addShutdownHook(new Thread(DesktopManagerApplication::staticCleanup));
        launch(args);
    }

    private static boolean acquireInstanceLock() {
        try {
            Path appDataDir = AppPathUtil.getAppDataDir();
            Files.createDirectories(appDataDir);
            File lockFile = appDataDir.resolve(".instance.lock").toFile();
            RandomAccessFile raf = new RandomAccessFile(lockFile, "rw");
            lockChannel = raf.getChannel();
            instanceLock = lockChannel.tryLock();
            if (instanceLock == null) {
                lockChannel.close();
                return false;
            }
            return true;
        } catch (Exception e) {
            log.error("获取实例锁失败", e);
            return true;
        }
    }

    private static void showAlreadyRunningDialog() {
        try {
            java.awt.Toolkit.getDefaultToolkit().beep();
        } catch (Exception ignored) {
        }
        System.exit(0);
    }
}
