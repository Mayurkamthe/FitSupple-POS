package com.fitsupplepos.controller;

import com.fitsupplepos.exception.BusinessException;
import com.fitsupplepos.model.Expense;
import com.fitsupplepos.model.enums.ExpenseCategory;
import com.fitsupplepos.service.ExpenseService;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ExpenseEditController {

    @FXML private Label headerLabel;
    @FXML private ComboBox<ExpenseCategory> categoryCombo;
    @FXML private TextField amountField;
    @FXML private DatePicker datePicker;
    @FXML private TextArea notesField;
    @FXML private Label errorLabel;

    private final ExpenseService expenseService = new ExpenseService();
    private Expense editingExpense;
    private Runnable onSaved;

    @FXML
    public void initialize() {
        categoryCombo.setItems(FXCollections.observableArrayList(ExpenseCategory.values()));
        datePicker.setValue(LocalDate.now());
    }

    public void setOnSaved(Runnable onSaved) {
        this.onSaved = onSaved;
    }

    public void configure(Expense expense) {
        this.editingExpense = expense;
        if (expense == null) {
            headerLabel.setText("Add Expense");
            return;
        }
        headerLabel.setText("Edit Expense");
        categoryCombo.setValue(expense.getCategory());
        amountField.setText(expense.getAmount().toPlainString());
        datePicker.setValue(expense.getExpenseDate());
        notesField.setText(expense.getNotes());
    }

    @FXML
    private void handleSave(ActionEvent event) {
        try {
            Expense expense = editingExpense != null ? editingExpense : new Expense();
            expense.setCategory(categoryCombo.getValue());
            expense.setAmount(new BigDecimal(requireText(amountField, "Amount")));
            expense.setExpenseDate(datePicker.getValue());
            expense.setNotes(notesField.getText());

            if (editingExpense != null) {
                expenseService.update(expense);
            } else {
                expenseService.create(expense);
            }
            if (onSaved != null) onSaved.run();
            closeWindow(event);
        } catch (BusinessException e) {
            showError(e.getMessage());
        } catch (NumberFormatException e) {
            showError("Enter a valid amount.");
        }
    }

    @FXML
    private void handleCancel(ActionEvent event) {
        closeWindow(event);
    }

    private String requireText(TextField field, String label) {
        String v = field.getText();
        if (v == null || v.isBlank()) {
            throw new BusinessException(label + " is required.");
        }
        return v.trim();
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
