package com.fitsupplepos.controller;

import com.fitsupplepos.config.AppPaths;
import com.fitsupplepos.exception.BusinessException;
import com.fitsupplepos.model.InvoiceSetting;
import com.fitsupplepos.service.InvoiceSettingService;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

public class SettingsController {

    @FXML private TextField shopNameField;
    @FXML private TextField phoneField;
    @FXML private TextField emailField;
    @FXML private TextField logoPathField;
    @FXML private TextField invoicePrefixField;
    @FXML private TextField purchasePrefixField;
    @FXML private TextArea addressField;
    @FXML private TextArea footerNoteField;
    @FXML private Label errorLabel;

    @FXML private Label configPathLabel;
    @FXML private Label dataDirLabel;
    @FXML private Label backupDirLabel;
    @FXML private Label logDirLabel;

    private final InvoiceSettingService invoiceSettingService = new InvoiceSettingService();

    @FXML
    public void initialize() {
        InvoiceSetting setting = invoiceSettingService.get();
        shopNameField.setText(setting.getShopName());
        phoneField.setText(setting.getPhone());
        emailField.setText(setting.getEmail());
        logoPathField.setText(setting.getLogoPath());
        invoicePrefixField.setText(setting.getInvoicePrefix());
        purchasePrefixField.setText(setting.getPurchasePrefix());
        addressField.setText(setting.getAddress());
        footerNoteField.setText(setting.getInvoiceFooterNote());

        configPathLabel.setText("Config file: " + AppPaths.configFile().toAbsolutePath());
        dataDirLabel.setText("Database & invoices: " + AppPaths.dataDir().toAbsolutePath());
        backupDirLabel.setText("Backups: " + AppPaths.backupDir().toAbsolutePath());
        logDirLabel.setText("Logs: " + AppPaths.logDir().toAbsolutePath());
    }

    @FXML
    private void handleSave() {
        try {
            invoiceSettingService.save(
                    shopNameField.getText(),
                    addressField.getText(),
                    phoneField.getText(),
                    emailField.getText(),
                    logoPathField.getText(),
                    invoicePrefixField.getText(),
                    purchasePrefixField.getText(),
                    footerNoteField.getText());
            hideError();
        } catch (BusinessException e) {
            showError(e.getMessage());
        }
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }
}
