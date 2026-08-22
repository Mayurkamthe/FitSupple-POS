package com.fitsupplepos.controller;

import com.fitsupplepos.model.Product;
import com.fitsupplepos.service.ProductService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ProductController {

    private static final Logger log = LoggerFactory.getLogger(ProductController.class);

    @FXML private TextField searchField;
    @FXML private TableView<ProductService.ProductStockRow> productTable;
    @FXML private TableColumn<ProductService.ProductStockRow, String> nameCol;
    @FXML private TableColumn<ProductService.ProductStockRow, String> categoryCol;
    @FXML private TableColumn<ProductService.ProductStockRow, String> brandCol;
    @FXML private TableColumn<ProductService.ProductStockRow, String> skuCol;
    @FXML private TableColumn<ProductService.ProductStockRow, String> barcodeCol;
    @FXML private TableColumn<ProductService.ProductStockRow, String> mrpCol;
    @FXML private TableColumn<ProductService.ProductStockRow, String> sellingPriceCol;
    @FXML private TableColumn<ProductService.ProductStockRow, Number> stockCol;
    @FXML private TableColumn<ProductService.ProductStockRow, String> statusCol;
    @FXML private TableColumn<ProductService.ProductStockRow, Void> actionsCol;

    private final ProductService productService = new ProductService();

    @FXML
    public void initialize() {
        nameCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().product.getProductName()));
        categoryCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().product.getCategory().getDisplayName()));
        brandCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().product.getBrand() != null ? c.getValue().product.getBrand().getName() : ""));
        skuCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(nullSafe(c.getValue().product.getSku())));
        barcodeCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(nullSafe(c.getValue().product.getBarcode())));
        mrpCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty("₹" + c.getValue().product.getMrp()));
        sellingPriceCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty("₹" + c.getValue().product.getSellingPrice()));
        stockCol.setCellValueFactory(c -> new javafx.beans.property.SimpleIntegerProperty(c.getValue().totalStock));
        statusCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().stockStatus));

        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(status);
                    switch (status) {
                        case "OUT OF STOCK" -> setStyle("-fx-text-fill: #D64545; -fx-font-weight: bold;");
                        case "LOW STOCK" -> setStyle("-fx-text-fill: #E0A32C; -fx-font-weight: bold;");
                        default -> setStyle("-fx-text-fill: #1E9E5A; -fx-font-weight: bold;");
                    }
                }
            }
        });

        actionsCol.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn = new Button("Edit");
            private final Button barcodeBtn = new Button("Barcode");
            private final HBox box = new HBox(6, editBtn, barcodeBtn);
            {
                editBtn.getStyleClass().add("btn-secondary");
                barcodeBtn.getStyleClass().add("btn-secondary");
                editBtn.setOnAction(e -> {
                    ProductService.ProductStockRow row = getTableView().getItems().get(getIndex());
                    openEditDialog(row.product);
                });
                barcodeBtn.setOnAction(e -> {
                    ProductService.ProductStockRow row = getTableView().getItems().get(getIndex());
                    openBarcodeDialog(row.product);
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
        List<ProductService.ProductStockRow> rows = productService.listActiveWithStock();
        productTable.setItems(FXCollections.observableArrayList(rows));
    }

    @FXML
    private void handleSearch(KeyEvent event) {
        String term = searchField.getText();
        if (term == null || term.isBlank()) {
            loadData();
            return;
        }
        ObservableList<ProductService.ProductStockRow> filtered = FXCollections.observableArrayList(
                productService.listActiveWithStock().stream()
                        .filter(r -> r.product.getProductName().toLowerCase().contains(term.toLowerCase())
                                || nullSafe(r.product.getSku()).toLowerCase().contains(term.toLowerCase())
                                || nullSafe(r.product.getBarcode()).toLowerCase().contains(term.toLowerCase()))
                        .toList());
        productTable.setItems(filtered);
    }

    @FXML
    private void handleAddProduct() {
        openEditDialog(null);
    }

    private void openEditDialog(Product product) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/product_edit.fxml"));
            Parent root = loader.load();
            ProductEditController controller = loader.getController();
            controller.configure(product);
            controller.setOnSaved(this::loadData);

            Stage dialog = new Stage();
            dialog.setTitle(product == null ? "Add Product" : "Edit Product");
            dialog.initModality(Modality.APPLICATION_MODAL);
            Scene scene = new Scene(root, 700, 700);
            var css = getClass().getResource("/css/theme.css");
            if (css != null) scene.getStylesheets().add(css.toExternalForm());
            dialog.setScene(scene);
            scene.setOnKeyPressed(ke -> { if (ke.getCode() == javafx.scene.input.KeyCode.ESCAPE) dialog.close(); });
            dialog.showAndWait();
        } catch (Exception e) {
            log.error("Failed to open product edit dialog", e);
            new Alert(Alert.AlertType.ERROR, "Could not open the product form.").showAndWait();
        }
    }

    private void openBarcodeDialog(Product product) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/barcode_preview.fxml"));
            Parent root = loader.load();
            BarcodePreviewController controller = loader.getController();
            controller.configure(product);

            Stage dialog = new Stage();
            dialog.setTitle("Barcode — " + product.getProductName());
            dialog.initModality(Modality.APPLICATION_MODAL);
            Scene scene = new Scene(root, 420, 420);
            var css = getClass().getResource("/css/theme.css");
            if (css != null) scene.getStylesheets().add(css.toExternalForm());
            dialog.setScene(scene);
            scene.setOnKeyPressed(ke -> { if (ke.getCode() == javafx.scene.input.KeyCode.ESCAPE) dialog.close(); });
            dialog.showAndWait();
        } catch (Exception e) {
            log.error("Failed to open barcode dialog", e);
            new Alert(Alert.AlertType.ERROR, "Could not open the barcode preview.").showAndWait();
        }
    }

    private String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
