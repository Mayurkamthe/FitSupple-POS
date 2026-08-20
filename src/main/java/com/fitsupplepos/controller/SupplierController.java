package com.fitsupplepos.controller;

import com.fitsupplepos.model.Supplier;
import com.fitsupplepos.service.SupplierService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class SupplierController {

    private static final Logger log = LoggerFactory.getLogger(SupplierController.class);

    @FXML private TextField searchField;
    @FXML private TableView<Supplier> supplierTable;
    @FXML private TableColumn<Supplier, String> nameCol;
    @FXML private TableColumn<Supplier, String> contactCol;
    @FXML private TableColumn<Supplier, String> phoneCol;
    @FXML private TableColumn<Supplier, String> emailCol;
    @FXML private TableColumn<Supplier, String> gstinCol;
    @FXML private TableColumn<Supplier, Void> actionsCol;

    private final SupplierService supplierService = new SupplierService();

    @FXML
    public void initialize() {
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        contactCol.setCellValueFactory(c -> new SimpleStringProperty(nullSafe(c.getValue().getContactPerson())));
        phoneCol.setCellValueFactory(c -> new SimpleStringProperty(nullSafe(c.getValue().getPhone())));
        emailCol.setCellValueFactory(c -> new SimpleStringProperty(nullSafe(c.getValue().getEmail())));
        gstinCol.setCellValueFactory(c -> new SimpleStringProperty(nullSafe(c.getValue().getGstin())));

        actionsCol.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn = new Button("Edit");
            private final HBox box = new HBox(6, editBtn);
            {
                editBtn.getStyleClass().add("btn-secondary");
                editBtn.setOnAction(e -> openEditDialog(getTableView().getItems().get(getIndex())));
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
        List<Supplier> suppliers = supplierService.listActive();
        supplierTable.setItems(FXCollections.observableArrayList(suppliers));
    }

    @FXML
    private void handleSearch(KeyEvent event) {
        String term = searchField.getText();
        if (term == null || term.isBlank()) {
            loadData();
            return;
        }
        supplierTable.setItems(FXCollections.observableArrayList(
                supplierService.listActive().stream()
                        .filter(s -> s.getName().toLowerCase().contains(term.toLowerCase()))
                        .toList()));
    }

    @FXML
    private void handleAdd() {
        openEditDialog(null);
    }

    private void openEditDialog(Supplier supplier) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/supplier_edit.fxml"));
            Parent root = loader.load();
            SupplierEditController controller = loader.getController();
            controller.configure(supplier);
            controller.setOnSaved(this::loadData);

            Stage dialog = new Stage();
            dialog.setTitle(supplier == null ? "Add Supplier" : "Edit Supplier");
            dialog.initModality(Modality.APPLICATION_MODAL);
            Scene scene = new Scene(root, 480, 560);
            var css = getClass().getResource("/css/theme.css");
            if (css != null) scene.getStylesheets().add(css.toExternalForm());
            dialog.setScene(scene);
            scene.setOnKeyPressed(ke -> { if (ke.getCode() == javafx.scene.input.KeyCode.ESCAPE) dialog.close(); });
            dialog.showAndWait();
        } catch (Exception e) {
            log.error("Failed to open supplier edit dialog", e);
            new Alert(Alert.AlertType.ERROR, "Could not open the supplier form.").showAndWait();
        }
    }

    private String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
