package com.fitsupplepos;

import com.fitsupplepos.config.DatabaseInitializer;
import com.fitsupplepos.config.HibernateConfig;
import com.fitsupplepos.config.SceneManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main extends Application {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    @Override
    public void init() {
        // Runs off the JavaFX Application Thread — safe place to do first-run DB setup
        // (create data/ dir, create fitsupple.db, run Hibernate schema update, seed defaults).
        try {
            DatabaseInitializer.initialize();
        } catch (Exception e) {
            log.error("Fatal error during startup database initialization", e);
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR,
                        "FitSupple POS could not start because the local database could not be initialized:\n\n"
                                + e.getMessage());
                alert.setHeaderText("Startup Error");
                alert.showAndWait();
                Platform.exit();
            });
        }
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        SceneManager.setPrimaryStage(primaryStage);
        primaryStage.setMinWidth(1100);
        primaryStage.setMinHeight(700);

        Parent root = SceneManager.load("/fxml/login.fxml");
        SceneManager.showScene(root, "/css/theme.css", "FitSupple POS — Owner Login", false);

        primaryStage.setOnCloseRequest(e -> shutdown());
    }

    @Override
    public void stop() {
        shutdown();
    }

    private void shutdown() {
        HibernateConfig.shutdown();
        log.info("FitSupple POS shut down cleanly.");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
