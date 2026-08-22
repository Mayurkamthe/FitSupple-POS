package com.fitsupplepos.controller;

import com.fitsupplepos.config.SceneManager;
import com.fitsupplepos.model.User;
import com.fitsupplepos.service.AuthService;
import com.fitsupplepos.util.ConnectivityChecker;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class MainShellController {

    private static final Logger log = LoggerFactory.getLogger(MainShellController.class);

    @FXML private VBox sidebarBox;
    @FXML private VBox contentArea;
    @FXML private Label statusLabel;
    @FXML private Label ownerLabel;

    private final AuthService authService = new AuthService();

    /** Module display-name -> FXML path. Built modules point at real views; the rest use the placeholder. */
    private final Map<String, String> modules = new LinkedHashMap<>();

    private Button activeButton;

    @FXML
    public void initialize() {
        modules.put("Dashboard", "/fxml/dashboard.fxml");
        modules.put("POS Billing", "/fxml/pos_billing.fxml");
        modules.put("Products", "/fxml/products.fxml");
        modules.put("Inventory", "/fxml/inventory.fxml");
        modules.put("Purchases", "/fxml/purchases.fxml");
        modules.put("Customers", "/fxml/customers.fxml");
        modules.put("Suppliers", "/fxml/suppliers.fxml");
        modules.put("GST / Non-GST", "/fxml/gst_settings.fxml");
        modules.put("Sales Returns", "/fxml/sales_returns.fxml");
        modules.put("Purchase Returns", "/fxml/purchase_returns.fxml");
        modules.put("Expenses", "/fxml/expenses.fxml");
        modules.put("Offers", "/fxml/offers.fxml");
        modules.put("WhatsApp", "/fxml/whatsapp.fxml");
        modules.put("Reports", "/fxml/reports.fxml");
        modules.put("Backup & Restore", "/fxml/backup.fxml");
        modules.put("Settings", "/fxml/settings.fxml");

        buildSidebar();

        AuthService.getCurrentUser().ifPresent(u -> ownerLabel.setText(
                (u.getFullName() != null && !u.getFullName().isBlank() ? u.getFullName() : u.getUsername()).toUpperCase()));

        navigateTo("Dashboard");
        startConnectivityMonitor();

        sidebarBox.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                startIdleTimeoutMonitor(newScene);
            }
        });
    }

    /**
     * Auto-logout: any mouse or key activity anywhere in the window resets a countdown
     * of {@code session.timeoutMinutes} (from AppConfig, default 15). If it ever
     * elapses with no activity, the owner is logged out and returned to the login
     * screen — same behaviour as a manual "Logout" click.
     */
    private void startIdleTimeoutMonitor(javafx.scene.Scene scene) {
        int timeoutMinutes = com.fitsupplepos.config.AppConfig.sessionTimeoutMinutes();
        if (timeoutMinutes <= 0) return;

        Timeline idleTimer = new Timeline(new KeyFrame(Duration.minutes(timeoutMinutes), e -> {
            log.info("Session idle for {} minute(s) — auto-logging out.", timeoutMinutes);
            authService.logout();
            try {
                Parent root = SceneManager.load("/fxml/login.fxml");
                SceneManager.showScene(root, "/css/theme.css", "FitSupple POS — Owner Login", false);
                Alert alert = new Alert(Alert.AlertType.INFORMATION,
                        "You were logged out after " + timeoutMinutes + " minute(s) of inactivity.");
                alert.showAndWait();
            } catch (IOException ex) {
                log.error("Failed to return to login screen after idle timeout", ex);
            }
        }));
        idleTimer.setCycleCount(1);
        idleTimer.play();

        javafx.event.EventHandler<javafx.scene.input.InputEvent> resetOnActivity = e -> {
            idleTimer.stop();
            idleTimer.playFromStart();
        };
        scene.addEventFilter(javafx.scene.input.MouseEvent.ANY, resetOnActivity);
        scene.addEventFilter(javafx.scene.input.KeyEvent.ANY, resetOnActivity);
    }

    private void buildSidebar() {
        sidebarBox.getChildren().clear();
        for (String moduleName : modules.keySet()) {
            Button btn = new Button(moduleName);
            btn.getStyleClass().add("sidebar-btn");
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.setOnAction(e -> {
                navigateTo(moduleName);
                setActiveButton(btn);
            });
            sidebarBox.getChildren().add(btn);
            if (moduleName.equals("Dashboard")) {
                activeButton = btn;
            }
        }
        if (activeButton != null) {
            activeButton.getStyleClass().add("sidebar-btn-active");
        }
    }

    private void setActiveButton(Button btn) {
        if (activeButton != null) {
            activeButton.getStyleClass().remove("sidebar-btn-active");
        }
        activeButton = btn;
        activeButton.getStyleClass().add("sidebar-btn-active");
    }

    private void navigateTo(String moduleName) {
        try {
            String fxmlPath = modules.get(moduleName);
            Parent view;
            if (fxmlPath != null) {
                view = SceneManager.load(fxmlPath);
            } else {
                FXMLLoader loader = SceneManager.loader("/fxml/placeholder.fxml");
                view = loader.load();
                PlaceholderController controller = loader.getController();
                controller.configure(moduleName);
            }
            contentArea.getChildren().setAll(view);
        } catch (IOException e) {
            log.error("Failed to load module '{}'", moduleName, e);
            Alert alert = new Alert(Alert.AlertType.ERROR, "Could not load the \"" + moduleName + "\" screen.");
            alert.showAndWait();
        }
    }

    private void startConnectivityMonitor() {
        Runnable check = () -> {
            boolean online = ConnectivityChecker.isOnline();
            Platform.runLater(() -> {
                if (online) {
                    statusLabel.setText("● ONLINE");
                    statusLabel.getStyleClass().setAll("status-online");
                } else {
                    statusLabel.setText("● OFFLINE");
                    statusLabel.getStyleClass().setAll("status-offline");
                }
            });
        };
        Thread initial = new Thread(check, "connectivity-initial-check");
        initial.setDaemon(true);
        initial.start();

        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(30), e -> {
            Thread t = new Thread(check, "connectivity-check");
            t.setDaemon(true);
            t.start();
        }));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }

    @FXML
    private void handleLogout(ActionEvent event) {
        authService.logout();
        try {
            Parent root = SceneManager.load("/fxml/login.fxml");
            SceneManager.showScene(root, "/css/theme.css", "FitSupple POS — Owner Login", false);
        } catch (IOException e) {
            log.error("Failed to return to login screen", e);
        }
    }

    @FXML
    private void handleChangePassword(ActionEvent event) {
        AuthService.getCurrentUser().ifPresent(user -> {
            try {
                FXMLLoader loader = SceneManager.loader("/fxml/change_password.fxml");
                Parent root = loader.load();
                ChangePasswordController controller = loader.getController();
                controller.configure(user, false);

                Stage dialog = new Stage();
                dialog.setTitle("Change Password");
                dialog.initModality(Modality.APPLICATION_MODAL);
                Scene scene = new Scene(root);
                var css = getClass().getResource("/css/theme.css");
                if (css != null) scene.getStylesheets().add(css.toExternalForm());
                dialog.setScene(scene);
                scene.setOnKeyPressed(ke -> { if (ke.getCode() == javafx.scene.input.KeyCode.ESCAPE) dialog.close(); });
                dialog.showAndWait();

                if (controller.isSaved()) {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION, "Password updated successfully.");
                    alert.showAndWait();
                }
            } catch (IOException e) {
                log.error("Failed to open change-password dialog", e);
            }
        });
    }
}
