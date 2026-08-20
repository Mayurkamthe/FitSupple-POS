package com.fitsupplepos.controller;

import com.fitsupplepos.model.Customer;
import com.fitsupplepos.model.enums.CustomerSegment;
import com.fitsupplepos.service.CustomerService;
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

import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class CustomerController {

    private static final Logger log = LoggerFactory.getLogger(CustomerController.class);

    @FXML private TextField searchField;
    @FXML private TableView<Customer> customerTable;
    @FXML private TableColumn<Customer, String> nameCol;
    @FXML private TableColumn<Customer, String> mobileCol;
    @FXML private TableColumn<Customer, String> segmentCol;
    @FXML private TableColumn<Customer, String> ordersCol;
    @FXML private TableColumn<Customer, String> spendingCol;
    @FXML private TableColumn<Customer, String> lastPurchaseCol;
    @FXML private TableColumn<Customer, String> favouriteCol;
    @FXML private TableColumn<Customer, String> whatsappCol;
    @FXML private TableColumn<Customer, Void> actionsCol;

    private final CustomerService customerService = new CustomerService();

    @FXML
    public void initialize() {
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        mobileCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getMobile()));
        segmentCol.setCellValueFactory(c -> new SimpleStringProperty(segmentLabel(customerService.computeSegment(c.getValue()))));

        ordersCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(customerService.getStats(c.getValue().getId()).orderCount)));
        spendingCol.setCellValueFactory(c -> new SimpleStringProperty("₹" + customerService.getStats(c.getValue().getId()).totalSpending.setScale(2, RoundingMode.HALF_UP)));
        lastPurchaseCol.setCellValueFactory(c -> {
            var stats = customerService.getStats(c.getValue().getId());
            return new SimpleStringProperty(stats.lastPurchase == null ? "-" :
                    stats.lastPurchase.format(DateTimeFormatter.ofPattern("dd-MMM-yyyy")));
        });
        favouriteCol.setCellValueFactory(c -> new SimpleStringProperty(customerService.getStats(c.getValue().getId()).favouriteProduct));
        whatsappCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().isWhatsappOptIn() ? "Yes" : "No"));

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

    private String segmentLabel(CustomerSegment segment) {
        return switch (segment) {
            case NEW -> "New";
            case REGULAR -> "Regular";
            case VIP -> "VIP";
            case INACTIVE_30 -> "Inactive 30d";
            case INACTIVE_60 -> "Inactive 60d";
            case WHEY_CUSTOMERS -> "Whey Customer";
            case CREATINE_CUSTOMERS -> "Creatine Customer";
            case HIGH_VALUE -> "High Value";
        };
    }

    private void loadData() {
        List<Customer> customers = customerService.listAll();
        customerTable.setItems(FXCollections.observableArrayList(customers));
    }

    @FXML
    private void handleSearch(KeyEvent event) {
        String term = searchField.getText();
        if (term == null || term.isBlank()) {
            loadData();
            return;
        }
        customerTable.setItems(FXCollections.observableArrayList(customerService.search(term)));
    }

    @FXML
    private void handleAdd() {
        openEditDialog(null);
    }

    private void openEditDialog(Customer customer) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/customer_edit.fxml"));
            Parent root = loader.load();
            CustomerEditController controller = loader.getController();
            controller.configure(customer);
            controller.setOnSaved(this::loadData);

            Stage dialog = new Stage();
            dialog.setTitle(customer == null ? "Add Customer" : "Edit Customer");
            dialog.initModality(Modality.APPLICATION_MODAL);
            Scene scene = new Scene(root, 480, 640);
            var css = getClass().getResource("/css/theme.css");
            if (css != null) scene.getStylesheets().add(css.toExternalForm());
            dialog.setScene(scene);
            scene.setOnKeyPressed(ke -> { if (ke.getCode() == javafx.scene.input.KeyCode.ESCAPE) dialog.close(); });
            dialog.showAndWait();
        } catch (Exception e) {
            log.error("Failed to open customer edit dialog", e);
            new Alert(Alert.AlertType.ERROR, "Could not open the customer form.").showAndWait();
        }
    }
}
