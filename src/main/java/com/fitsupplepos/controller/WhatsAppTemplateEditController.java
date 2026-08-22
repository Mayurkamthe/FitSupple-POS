package com.fitsupplepos.controller;

import com.fitsupplepos.exception.BusinessException;
import com.fitsupplepos.model.WhatsAppTemplate;
import com.fitsupplepos.service.WhatsAppTemplateService;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

public class WhatsAppTemplateEditController {

    @FXML private Label headerLabel;
    @FXML private TextField nameField;
    @FXML private TextField languageField;
    @FXML private TextField categoryField;
    @FXML private TextArea bodyField;
    @FXML private TextField placeholderCountField;
    @FXML private CheckBox activeCheckBox;
    @FXML private Label errorLabel;

    private final WhatsAppTemplateService templateService = new WhatsAppTemplateService();
    private WhatsAppTemplate editingTemplate;
    private Runnable onSaved;

    public void setOnSaved(Runnable onSaved) {
        this.onSaved = onSaved;
    }

    public void configure(WhatsAppTemplate template) {
        this.editingTemplate = template;
        if (template == null) {
            headerLabel.setText("Add Template");
            return;
        }
        headerLabel.setText("Edit Template");
        nameField.setText(template.getTemplateName());
        languageField.setText(template.getLanguage());
        categoryField.setText(template.getCategory());
        bodyField.setText(template.getBodyText());
        placeholderCountField.setText(String.valueOf(template.getPlaceholderCount()));
        activeCheckBox.setSelected(template.isActive());
    }

    @FXML
    private void handleSave(ActionEvent event) {
        try {
            WhatsAppTemplate template = editingTemplate != null ? editingTemplate : new WhatsAppTemplate();
            template.setTemplateName(text(nameField));
            template.setLanguage(text(languageField));
            template.setCategory(text(categoryField));
            template.setBodyText(bodyField.getText());
            template.setPlaceholderCount(parseIntOrZero(placeholderCountField.getText()));
            template.setActive(activeCheckBox.isSelected());

            if (editingTemplate != null) {
                templateService.update(template);
            } else {
                templateService.create(template);
            }
            if (onSaved != null) onSaved.run();
            closeWindow(event);
        } catch (BusinessException e) {
            showError(e.getMessage());
        } catch (NumberFormatException e) {
            showError("Placeholder count must be a whole number.");
        }
    }

    @FXML
    private void handleCancel(ActionEvent event) {
        closeWindow(event);
    }

    private int parseIntOrZero(String s) {
        if (s == null || s.isBlank()) return 0;
        return Integer.parseInt(s.trim());
    }

    private String text(TextField f) {
        String v = f.getText();
        return v == null ? null : v.trim();
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void closeWindow(ActionEvent event) {
        Stage stage = (Stage) ((Button) event.getSource()).getScene().getWindow();
        stage.close();
    }
}
