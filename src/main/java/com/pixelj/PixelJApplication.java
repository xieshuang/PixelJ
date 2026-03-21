package com.pixelj;

import com.pixelj.util.AppConfig;
import javafx.application.Application;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PixelJ 应用程序入口类。
 * 继承 JavaFX Application，负责初始化配置和启动主窗口。
 */
public class PixelJApplication extends Application {

    private static final Logger logger = LoggerFactory.getLogger(PixelJApplication.class);

    static {
        AppConfig.initialize();
    }

    /**
     * 应用程序初始化。
     * 在 start() 之前调用，用于初始化日志和配置信息。
     */
    @Override
    public void init() {
        logger.info("PixelJ starting...");
        logger.info("Java version: {}", System.getProperty("java.version"));
        logger.info("JavaFX version: {}", System.getProperty("javafx.version"));
        logger.info("AppConfig: {}", AppConfig.getInstance());
    }

    /**
     * 启动主窗口。
     *
     * @param primaryStage JavaFX 主舞台
     */
    @Override
    public void start(Stage primaryStage) {
        logger.info("Creating main window");
        try {
            var mainView = new com.pixelj.ui.MainView(primaryStage);
            primaryStage.setTitle("PixelJ");
            primaryStage.setScene(mainView.createScene());
            primaryStage.setWidth(1280);
            primaryStage.setHeight(800);
            primaryStage.show();
            logger.info("Main window displayed");
        } catch (Exception e) {
            logger.error("Failed to start application", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 应用程序关闭时调用。
     * 执行清理工作。
     */
    @Override
    public void stop() {
        logger.info("PixelJ shutting down");
    }

    /**
     * 应用程序入口点。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        launch(args);
    }
}
