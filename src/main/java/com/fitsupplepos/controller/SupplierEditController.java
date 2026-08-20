package com.fitsupplepos.controller;

import com.fitsupplepos.exception.BusinessException;
import com.fitsupplepos.model.Supplier;
import com.fitsupplepos.service.SupplierService;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class SupplierEditController {

    @FXML private Label headerLabel;
    @FXML private TextField nameField;
    @FXML private TextField contactPersonField;
    @FXML private TextField phoneField;
    @FXML private TextField emailField;
    @FXML private TextField gstinField;
    @FXML private TextArea addressField;
    @FXML private TextArea notesField;
    @FXML private Label errorLabel;

    private final SupplierService supplierService = new SupplierService();
    private Supplier editingSupplier;
    private Runnable onSaved;

    public void setOnSaved(Runnable onSaved) {
        this.onSaved = onSaved;
    }

    public void configure(Supplier supplier) {
        this.editingSupplier = supplier;
        if (supplier == null) {
            headerLabel.setText("Add Supplier");
            return;
        }
        headerLabel.setText("Edit Supplier");
        nameField.setText(supplier.getName());
        contactPersonField.setText(supplier.getContactPerson());
        phoneField.setText(supplier.getPhone());
        emailField.setText(supplier.getEmail());
        gstinField.setText(supplier.getGstin());
        addressField.setText(supplier.getAddress());
        notesField.setText(supplier.getNotes());
    }

    @FXML
    private void handleSave(ActionEvent event) {
        try {
            Supplier supplier = editingSupplier != null ? editingSupplier : new Supplier();
            supplier.setName(text(nameField));
            supplier.setContactPerson(text(contactPersonField));
            supplier.setPhone(text(phoneField));
            supplier.setEmail(text(emailField));
            supplier.setGstin(text(gstinField));
            supplier.setAddress(addressField.getText());
            supplier.setNotes(notesField.getText());

            if (editingSupplier != null) {
                supplierService.update(supplier);
            } else {
                supplierService.create(supplier);
            }
            if (onSaved != null) onSaved.run();
            closeWindow(event);
        } catch (BusinessException e) {
            showError(e.getMessage());
        }
    }

    @FXML
    private void handleCancel(ActionEvent event) {
        closeWindow(event);
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
