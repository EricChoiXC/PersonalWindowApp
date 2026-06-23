package com.personal.windows.desktopmanager;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.personal.windows.desktopmanager.config.AppConfig;

public class DesktopManagerApplication extends Application {

    private static final Logger log = LoggerFactory.getLogger(DesktopManagerApplication.class);

    @Override
    public void start(Stage primaryStage) {
        log.info("DesktopManager 启动中...");

        BorderPane root = new BorderPane();
        root.setCenter(new Label("Desktop Manager"));

        Scene scene = new Scene(root, AppConfig.getDefaultWidth(), AppConfig.getDefaultHeight());

        primaryStage.setTitle(AppConfig.getAppTitle());
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(AppConfig.getMinWidth());
        primaryStage.setMinHeight(AppConfig.getMinHeight());
        primaryStage.setOnCloseRequest(e -> {
            log.info("DesktopManager 退出");
            Platform.exit();
        });
        primaryStage.show();

        log.info("DesktopManager 启动完成");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
