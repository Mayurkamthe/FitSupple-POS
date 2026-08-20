package com.fitsupplepos.controller;

import com.fitsupplepos.config.SceneManager;
import com.fitsupplepos.exception.BusinessException;
import com.fitsupplepos.model.User;
import com.fitsupplepos.service.AuthService;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class LoginController {

    private static final Logger log = LoggerFactory.getLogger(LoginController.class);

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;

    private final AuthService authService = new AuthService();

    @FXML
    private void handleLogin(ActionEvent event) {
        String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
        String password = passwordField.getText() == null ? "" : passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Please enter both username and password.");
            return;
        }

        try {
            User user = authService.login(username, password);
            if (user.isMustChangePassword()) {
                boolean changed = promptMandatoryPasswordChange(user);
                if (!changed) {
                    // Owner declined to set a new password (can't, dialog is mandatory) — re-check.
                    authService.logout();
                    showError("You must set a new password to continue.");
                    return;
                }
            }
            navigateToMainShell();
        } catch (BusinessException e) {
            showError(e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during login", e);
            showError("An unexpected error occurred. Please check the logs.");
        }
    }

    private boolean promptMandatoryPasswordChange(User user) throws IOException {
        javafx.fxml.FXMLLoader loader = SceneManager.loader("/fxml/change_password.fxml");
        Parent root = loader.load();
        ChangePasswordController controller = loader.getController();
        controller.configure(user, true);

        Stage dialog = new Stage();
        dialog.setTitle("Set New Password");
        dialog.initModality(Modality.APPLICATION_MODAL);
        Scene scene = new Scene(root);
        var css = getClass().getResource("/css/theme.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
        dialog.setScene(scene);
        scene.setOnKeyPressed(ke -> { if (ke.getCode() == javafx.scene.input.KeyCode.ESCAPE) dialog.close(); });
        dialog.showAndWait();

        return controller.isSaved();
    }

    private void navigateToMainShell() {
        try {
            Parent root = SceneManager.load("/fxml/main_shell.fxml");
            SceneManager.showScene(root, "/css/theme.css", "FitSupple POS", true);
        } catch (IOException e) {
            log.error("Failed to load main shell", e);
            Alert alert = new Alert(Alert.AlertType.ERROR, "Could not open the main application window.");
            alert.showAndWait();
        }
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }
}
