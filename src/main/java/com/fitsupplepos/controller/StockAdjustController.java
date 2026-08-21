package com.fitsupplepos.controller;

import com.fitsupplepos.exception.BusinessException;
import com.fitsupplepos.model.ProductBatch;
import com.fitsupplepos.model.enums.TransactionType;
import com.fitsupplepos.service.InventoryService;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class StockAdjustController {

    @FXML private Label titleLabel;
    @FXML private Label batchInfoLabel;
    @FXML private ComboBox<TransactionType> typeCombo;
    @FXML private TextField quantityField;
    @FXML private TextArea reasonField;
    @FXML private Label errorLabel;

    private final InventoryService inventoryService = new InventoryService();
    private ProductBatch batch;
    private boolean saved = false;
    private Runnable onSaved;

    @FXML
    public void initialize() {
        typeCombo.getItems().setAll(TransactionType.ADJUSTMENT, TransactionType.DAMAGE, TransactionType.EXPIRED);
        typeCombo.getSelectionModel().selectFirst();
    }

    public void configure(ProductBatch batch, Runnable onSaved) {
        this.batch = batch;
        this.onSaved = onSaved;
        batchInfoLabel.setText(batch.getProduct().getProductName() + " — Batch " + batch.getBatchNumber()
                + " — currently available: " + batch.getQuantityAvailable());
    }

    public boolean isSaved() {
        return saved;
    }

    @FXML
    private void handleSave(ActionEvent event) {
        try {
            TransactionType type = typeCombo.getValue();
            int qty = Integer.parseInt(quantityField.getText().trim());
            if (qty <= 0) {
                showError("Enter a positive quantity.");
                return;
            }
            String reason = reasonField.getText();
            if (reason == null || reason.isBlank()) {
                showError("A reason/note is required for the audit trail.");
                return;
            }

            int delta = (type == TransactionType.ADJUSTMENT) ? qty : -qty;
            // For ADJUSTMENT we allow the owner to choose direction via a signed quantity convention:
            // typed value is always positive; "increase" vs "decrease" is a menu choice for clarity below.
            inventoryService.manualAdjust(batch.getId(), delta, type, reason.trim());

            saved = true;
            if (onSaved != null) onSaved.run();
            closeWindow(event);
        } catch (NumberFormatException e) {
            showError("Quantity must be a whole number.");
        } catch (BusinessException e) {
            showError(e.getMessage());
        }
    }

    @FXML
    private void handleCancel(ActionEvent event) {
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
