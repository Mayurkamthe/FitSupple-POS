package com.fitsupplepos.controller;

import com.fitsupplepos.model.Expense;
import com.fitsupplepos.service.ExpenseService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ExpenseController {

    private static final Logger log = LoggerFactory.getLogger(ExpenseController.class);

    @FXML private DatePicker fromDatePicker;
    @FXML private DatePicker toDatePicker;
    @FXML private Label totalLabel;

    @FXML private TableView<Expense> expenseTable;
    @FXML private TableColumn<Expense, String> dateCol;
    @FXML private TableColumn<Expense, String> categoryCol;
    @FXML private TableColumn<Expense, String> amountCol;
    @FXML private TableColumn<Expense, String> notesCol;
    @FXML private TableColumn<Expense, Void> actionsCol;

    private final ExpenseService expenseService = new ExpenseService();

    @FXML
    public void initialize() {
        dateCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getExpenseDate().format(DateTimeFormatter.ofPattern("dd-MMM-yyyy"))));
        categoryCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCategory().name()));
        amountCol.setCellValueFactory(c -> new SimpleStringProperty("₹" + c.getValue().getAmount().setScale(2, RoundingMode.HALF_UP)));
        notesCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNotes() == null ? "" : c.getValue().getNotes()));

        actionsCol.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn = new Button("Edit");
            private final Button deleteBtn = new Button("Delete");
            private final HBox box = new HBox(6, editBtn, deleteBtn);
            {
                editBtn.getStyleClass().add("btn-secondary");
                deleteBtn.getStyleClass().add("btn-secondary");
                editBtn.setOnAction(e -> openEditDialog(getTableView().getItems().get(getIndex())));
                deleteBtn.setOnAction(e -> {
                    Expense expense = getTableView().getItems().get(getIndex());
                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Delete this expense entry?");
                    confirm.showAndWait().ifPresent(bt -> {
                        if (bt == ButtonType.OK) {
                            expenseService.delete(expense.getId());
                            loadData();
                        }
                    });
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });

        loadData();
    }

    private void loadData() {
        List<Expense> expenses = expenseService.listAll();
        setRows(expenses);
    }

    private void setRows(List<Expense> expenses) {
        expenseTable.setItems(FXCollections.observableArrayList(expenses));
        totalLabel.setText("Total: ₹" + expenseService.totalFor(expenses).setScale(2, RoundingMode.HALF_UP));
    }

    @FXML
    private void handleFilter() {
        LocalDate from = fromDatePicker.getValue();
        LocalDate to = toDatePicker.getValue();
        if (from == null || to == null) {
            new Alert(Alert.AlertType.WARNING, "Select both a from and to date.").showAndWait();
            return;
        }
        setRows(expenseService.listBetween(from, to));
    }

    @FXML
    private void handleShowAll() {
        fromDatePicker.setValue(null);
        toDatePicker.setValue(null);
        loadData();
    }

    @FXML
    private void handleAdd() {
        openEditDialog(null);
    }

    private void openEditDialog(Expense expense) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/expense_edit.fxml"));
            Parent root = loader.load();
            ExpenseEditController controller = loader.getController();
            controller.configure(expense);
            controller.setOnSaved(this::loadData);

            Stage dialog = new Stage();
            dialog.setTitle(expense == null ? "Add Expense" : "Edit Expense");
            dialog.initModality(Modality.APPLICATION_MODAL);
            Scene scene = new Scene(root, 420, 480);
            var css = getClass().getResource("/css/theme.css");
            if (css != null) scene.getStylesheets().add(css.toExternalForm());
            scene.setOnKeyPressed(ke -> { if (ke.getCode() == javafx.scene.input.KeyCode.ESCAPE) dialog.close(); });
            dialog.setScene(scene);
            dialog.showAndWait();
        } catch (Exception e) {
            log.error("Failed to open expense edit dialog", e);
            new Alert(Alert.AlertType.ERROR, "Could not open the expense form.").showAndWait();
        }
    }
}
