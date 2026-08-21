package com.fitsupplepos.controller;

import com.fitsupplepos.config.SceneManager;
import com.fitsupplepos.model.ProductBatch;
import com.fitsupplepos.service.InventoryReportService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class InventoryController {

    private static final Logger log = LoggerFactory.getLogger(InventoryController.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MMM-yyyy");

    @FXML private Label valuationCostLabel;
    @FXML private Label valuationMrpLabel;
    @FXML private TableView<ProductBatch> allStockTable;
    @FXML private TableView<ProductBatch> lowStockTable;
    @FXML private TableView<ProductBatch> outOfStockTable;
    @FXML private TableView<ProductBatch> expiring30Table;
    @FXML private TableView<ProductBatch> expiring60Table;
    @FXML private TableView<ProductBatch> expiredTable;

    private final InventoryReportService reportService = new InventoryReportService();

    @FXML
    public void initialize() {
        buildColumns(allStockTable, true);
        buildColumns(lowStockTable, true);
        buildColumns(outOfStockTable, false);
        buildColumns(expiring30Table, true);
        buildColumns(expiring60Table, true);
        buildColumns(expiredTable, false);
        refresh();
    }

    @FXML
    private void handleRefresh() {
        refresh();
    }

    private void refresh() {
        allStockTable.setItems(FXCollections.observableArrayList(reportService.findAllInStock()));
        lowStockTable.setItems(FXCollections.observableArrayList(reportService.findLowStock()));
        outOfStockTable.setItems(FXCollections.observableArrayList(reportService.findOutOfStock()));
        expiring30Table.setItems(FXCollections.observableArrayList(reportService.findExpiringWithin(30)));
        expiring60Table.setItems(FXCollections.observableArrayList(reportService.findExpiringWithin(60)));
        expiredTable.setItems(FXCollections.observableArrayList(reportService.findExpired()));

        BigDecimal cost = reportService.totalValuationAtCost().setScale(2, RoundingMode.HALF_UP);
        BigDecimal mrp = reportService.totalValuationAtMrp().setScale(2, RoundingMode.HALF_UP);
        valuationCostLabel.setText("Stock value (cost): \u20B9" + cost.toPlainString());
        valuationMrpLabel.setText("Stock value (MRP): \u20B9" + mrp.toPlainString());
    }

    @SuppressWarnings("unchecked")
    private void buildColumns(TableView<ProductBatch> table, boolean allowAdjust) {
        table.getColumns().clear();

        TableColumn<ProductBatch, String> productCol = new TableColumn<>("Product");
        productCol.setPrefWidth(220);
        productCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
                cd.getValue().getProduct().getProductName()));

        TableColumn<ProductBatch, String> batchCol = new TableColumn<>("Batch #");
        batchCol.setPrefWidth(110);
        batchCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
                cd.getValue().getBatchNumber()));

        TableColumn<ProductBatch, String> expiryCol = new TableColumn<>("Expiry");
        expiryCol.setPrefWidth(100);
        expiryCol.setCellValueFactory(cd -> {
            LocalDate exp = cd.getValue().getExpiryDate();
            return new javafx.beans.property.SimpleStringProperty(exp == null ? "—" : exp.format(DATE_FMT));
        });

        TableColumn<ProductBatch, String> availableCol = new TableColumn<>("Available");
        availableCol.setPrefWidth(80);
        availableCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
                String.valueOf(cd.getValue().getQuantityAvailable())));

        TableColumn<ProductBatch, String> purchasePriceCol = new TableColumn<>("Purchase Price");
        purchasePriceCol.setPrefWidth(100);
        purchasePriceCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
                "\u20B9" + cd.getValue().getPurchasePrice().setScale(2, RoundingMode.HALF_UP)));

        TableColumn<ProductBatch, String> mrpCol = new TableColumn<>("MRP");
        mrpCol.setPrefWidth(80);
        mrpCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
                "\u20B9" + cd.getValue().getMrp().setScale(2, RoundingMode.HALF_UP)));

        TableColumn<ProductBatch, String> statusCol = new TableColumn<>("Status");
        statusCol.setPrefWidth(110);
        statusCol.setCellValueFactory(cd -> {
            ProductBatch b = cd.getValue();
            String status;
            if (b.isExpired()) status = "EXPIRED";
            else if (b.getQuantityAvailable() <= 0) status = "OUT OF STOCK";
            else if (b.isExpiringWithin(30)) status = "EXPIRING SOON";
            else status = "OK";
            return new javafx.beans.property.SimpleStringProperty(status);
        });

        table.getColumns().addAll(productCol, batchCol, expiryCol, availableCol, purchasePriceCol, mrpCol, statusCol);

        if (allowAdjust) {
            TableColumn<ProductBatch, Void> actionsCol = new TableColumn<>("Actions");
            actionsCol.setPrefWidth(110);
            actionsCol.setCellFactory(col -> new TableCell<>() {
                private final Button adjustBtn = new Button("Adjust");
                {
                    adjustBtn.getStyleClass().add("btn-secondary");
                    adjustBtn.setOnAction(e -> openAdjustDialog(getTableView().getItems().get(getIndex())));
                }
                @Override
                protected void updateItem(Void item, boolean empty) {
                    super.updateItem(item, empty);
                    setGraphic(empty ? null : adjustBtn);
                }
            });
            table.getColumns().add(actionsCol);
        }
    }

    private void openAdjustDialog(ProductBatch batch) {
        try {
            FXMLLoader loader = SceneManager.loader("/fxml/stock_adjust.fxml");
            Parent root = loader.load();
            StockAdjustController controller = loader.getController();
            controller.configure(batch, this::refresh);

            Stage dialog = new Stage();
            dialog.setTitle("Adjust Stock");
            dialog.initModality(Modality.APPLICATION_MODAL);
            Scene scene = new Scene(root);
            var css = getClass().getResource("/css/theme.css");
            if (css != null) scene.getStylesheets().add(css.toExternalForm());
            dialog.setScene(scene);
            dialog.showAndWait();
        } catch (Exception e) {
            log.error("Failed to open stock adjustment dialog", e);
            new Alert(Alert.AlertType.ERROR, "Could not open the adjustment dialog.").showAndWait();
        }
    }
}
