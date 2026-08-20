package com.fitsupplepos.controller;

import com.fitsupplepos.exception.BusinessException;
import com.fitsupplepos.model.Customer;
import com.fitsupplepos.service.CustomerService;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

public class CustomerEditController {

    @FXML private Label headerLabel;
    @FXML private TextField nameField;
    @FXML private TextField mobileField;
    @FXML private TextField whatsappField;
    @FXML private TextField emailField;
    @FXML private DatePicker birthdayPicker;
    @FXML private TextArea addressField;
    @FXML private TextArea notesField;
    @FXML private CheckBox whatsappOptInCheckBox;
    @FXML private Label errorLabel;

    private final CustomerService customerService = new CustomerService();
    private Customer editingCustomer;
    private Runnable onSaved;

    public void setOnSaved(Runnable onSaved) {
        this.onSaved = onSaved;
    }

    public void configure(Customer customer) {
        this.editingCustomer = customer;
        if (customer == null) {
            headerLabel.setText("Add Customer");
            return;
        }
        headerLabel.setText("Edit Customer");
        nameField.setText(customer.getName());
        mobileField.setText(customer.getMobile());
        whatsappField.setText(customer.getWhatsappNumber());
        emailField.setText(customer.getEmail());
        birthdayPicker.setValue(customer.getBirthday());
        addressField.setText(customer.getAddress());
        notesField.setText(customer.getNotes());
        whatsappOptInCheckBox.setSelected(customer.isWhatsappOptIn());
    }

    @FXML
    private void handleSave(ActionEvent event) {
        try {
            Customer customer = editingCustomer != null ? editingCustomer : new Customer();
            customer.setName(text(nameField));
            customer.setMobile(text(mobileField));
            customer.setWhatsappNumber(whatsappField.getText() == null || whatsappField.getText().isBlank()
                    ? text(mobileField) : text(whatsappField));
            customer.setEmail(text(emailField));
            customer.setBirthday(birthdayPicker.getValue());
            customer.setAddress(addressField.getText());
            customer.setNotes(notesField.getText());
            customer.setWhatsappOptIn(whatsappOptInCheckBox.isSelected());

            if (editingCustomer != null) {
                customerService.update(customer);
            } else {
                customerService.create(customer);
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
