package com.fitsupplepos.controller;

import com.fitsupplepos.exception.BusinessException;
import com.fitsupplepos.model.User;
import com.fitsupplepos.service.AuthService;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.stage.Stage;

public class ChangePasswordController {

    @FXML private Label headerLabel;
    @FXML private Label subLabel;
    @FXML private PasswordField currentPasswordField;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label errorLabel;
    @FXML private Button cancelButton;

    private final AuthService authService = new AuthService();
    private User targetUser;
    private boolean mandatory = false;
    private boolean saved = false;

    /** Configures whether the user can dismiss this dialog (false when forced on first login). */
    public void configure(User user, boolean mandatory) {
        this.targetUser = user;
        this.mandatory = mandatory;
        cancelButton.setVisible(!mandatory);
        cancelButton.setManaged(!mandatory);
        if (mandatory) {
            subLabel.setText("This is your first login. Please set a new password before continuing.");
        } else {
            subLabel.setText("Enter your current password and choose a new one.");
        }
    }

    public boolean isSaved() {
        return saved;
    }

    @FXML
    private void handleSave(ActionEvent event) {
        String current = currentPasswordField.getText();
        String next = newPasswordField.getText();
        String confirm = confirmPasswordField.getText();

        if (next == null || !next.equals(confirm)) {
            showError("New password and confirmation do not match.");
            return;
        }

        try {
            authService.changePassword(targetUser.getId(), current, next);
            saved = true;
            closeWindow(event);
        } catch (BusinessException e) {
            showError(e.getMessage());
        }
    }

    @FXML
    private void handleCancel(ActionEvent event) {
        saved = false;
        closeWindow(event);
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void closeWindow(ActionEvent event) {
        Stage stage = (Stage) ((Button) event.getSource()).getScene().getWindow();
        stage.close();
    }
}
