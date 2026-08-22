package com.fitsupplepos.config;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

/**
 * Owns the primary {@link Stage} and provides the two things every controller needs:
 * loading an FXML view, and swapping the whole window over to a new root/stylesheet.
 */
public final class SceneManager {

    private static Stage primaryStage;

    private SceneManager() {}

    public static void setPrimaryStage(Stage stage) {
        primaryStage = stage;
    }

    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    /** Returns a ready-to-load FXMLLoader for the given classpath resource (e.g. "/fxml/login.fxml"). */
    public static FXMLLoader loader(String fxmlPath) {
        URL url = SceneManager.class.getResource(fxmlPath);
        if (url == null) {
            throw new IllegalArgumentException("FXML resource not found on classpath: " + fxmlPath);
        }
        return new FXMLLoader(url);
    }

    /** Loads and returns the root node for the given FXML resource. */
    public static Parent load(String fxmlPath) throws IOException {
        return loader(fxmlPath).load();
    }

    /**
     * Swaps the primary stage's scene to the given root, applying the given stylesheet
     * and title. {@code maximized} controls whether the window is maximized (main app
     * shell) or left at its natural size (login screen).
     */
    public static void showScene(Parent root, String stylesheetPath, String title, boolean maximized) {
        if (primaryStage == null) {
            throw new IllegalStateException("SceneManager.setPrimaryStage() must be called before showScene().");
        }
        Scene scene = new Scene(root);
        if (stylesheetPath != null) {
            URL css = SceneManager.class.getResource(stylesheetPath);
            if (css != null) {
                scene.getStylesheets().add(css.toExternalForm());
            }
        }
        primaryStage.setScene(scene);
        primaryStage.setTitle(title);
        primaryStage.setMaximized(maximized);
        if (!primaryStage.isShowing()) {
            primaryStage.show();
        }
    }
}
