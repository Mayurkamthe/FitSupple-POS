package com.fitsupplepos.controller;

import com.fitsupplepos.exception.BusinessException;
import com.fitsupplepos.model.GstSetting;
import com.fitsupplepos.model.enums.BillingMode;
import com.fitsupplepos.service.GstSettingService;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;

import java.math.BigDecimal;

public class GstSettingController {

    @FXML private RadioButton gstRadio;
    @FXML private RadioButton nonGstRadio;
    @FXML private TextField gstinField;
    @FXML private TextField stateCodeField;
    @FXML private TextField defaultGstRateField;
    @FXML private Label errorLabel;
    @FXML private Label successLabel;

    private final GstSettingService gstSettingService = new GstSettingService();

    @FXML
    public void initialize() {
        GstSetting setting = gstSettingService.get();
        if (setting.getBillingMode() == BillingMode.GST) {
            gstRadio.setSelected(true);
        } else {
            nonGstRadio.setSelected(true);
        }
        gstinField.setText(setting.getGstin());
        stateCodeField.setText(setting.getStateCode());
        defaultGstRateField.setText(setting.getDefaultGstRate() == null ? "0" : setting.getDefaultGstRate().toPlainString());
    }

    @FXML
    private void handleSave() {
        try {
            BillingMode mode = gstRadio.isSelected() ? BillingMode.GST : BillingMode.NON_GST;
            BigDecimal defaultRate = defaultGstRateField.getText() == null || defaultGstRateField.getText().isBlank()
                    ? BigDecimal.ZERO : new BigDecimal(defaultGstRateField.getText().trim());

            gstSettingService.save(mode, text(gstinField), text(stateCodeField), defaultRate);

            hideError();
            successLabel.setText("Settings saved. This applies to every new bill in POS Billing immediately.");
            successLabel.setVisible(true);
            successLabel.setManaged(true);
        } catch (BusinessException e) {
            showError(e.getMessage());
        } catch (NumberFormatException e) {
            showError("Enter a valid default GST rate.");
        }
    }

    private String text(TextField f) {
        String v = f.getText();
        return v == null || v.isBlank() ? null : v.trim();
    }

    private void showError(String message) {
        successLabel.setVisible(false);
        successLabel.setManaged(false);
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }
}
