package com.fitsupplepos.controller;

import com.fitsupplepos.dao.PurchaseDao;
import com.fitsupplepos.exception.BusinessException;
import com.fitsupplepos.model.Product;
import com.fitsupplepos.model.Purchase;
import com.fitsupplepos.model.Supplier;
import com.fitsupplepos.service.ProductService;
import com.fitsupplepos.service.PurchaseService;
import com.fitsupplepos.service.SupplierService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class PurchaseController {

    private static final Logger log = LoggerFactory.getLogger(PurchaseController.class);

    @FXML private ComboBox<Supplier> supplierCombo;
    @FXML private TextField overallDiscountField;

    @FXML private ComboBox<Product> productCombo;
    @FXML private TextField batchNumberField;
    @FXML private DatePicker mfgDatePicker;
    @FXML private DatePicker expiryDatePicker;
    @FXML private TextField qtyField;
    @FXML private TextField purchasePriceField;
    @FXML private TextField gstRateField;
    @FXML private Label errorLabel;

    @FXML private TableView<PurchaseService.PurchaseLineInput> lineTable;
    @FXML private TableColumn<PurchaseService.PurchaseLineInput, String> lineProductCol;
    @FXML private TableColumn<PurchaseService.PurchaseLineInput, String> lineBatchCol;
    @FXML private TableColumn<PurchaseService.PurchaseLineInput, String> lineExpiryCol;
    @FXML private TableColumn<PurchaseService.PurchaseLineInput, String> lineQtyCol;
    @FXML private TableColumn<PurchaseService.PurchaseLineInput, String> linePriceCol;
    @FXML private TableColumn<PurchaseService.PurchaseLineInput, String> lineGstCol;
    @FXML private TableColumn<PurchaseService.PurchaseLineInput, String> lineTotalCol;
    @FXML private TableColumn<PurchaseService.PurchaseLineInput, Void> lineRemoveCol;

    @FXML private Label grandTotalLabel;

    @FXML private TableView<Purchase> purchaseHistoryTable;
    @FXML private TableColumn<Purchase, String> histInvoiceCol;
    @FXML private TableColumn<Purchase, String> histDateCol;
    @FXML private TableColumn<Purchase, String> histSupplierCol;
    @FXML private TableColumn<Purchase, String> histTotalCol;
    @FXML private TableColumn<Purchase, String> histStatusCol;

    private final SupplierService supplierService = new SupplierService();
    private final ProductService productService = new ProductService();
    private final PurchaseService purchaseService = new PurchaseService();
    private final PurchaseDao purchaseDao = new PurchaseDao();

    private final ObservableList<PurchaseService.PurchaseLineInput> lines = FXCollections.observableArrayList();
    // Keeps display-friendly product names alongside each line input for the table.
    private final java.util.Map<PurchaseService.PurchaseLineInput, String> productNames = new java.util.HashMap<>();

    @FXML
    public void initialize() {
        supplierCombo.setItems(FXCollections.observableArrayList(supplierService.listActive()));
        supplierCombo.setConverter(nameConverter(Supplier::getName));

        productCombo.setItems(FXCollections.observableArrayList(productService.search(null)));
        productCombo.setConverter(nameConverter(Product::getProductName));

        setupLineTable();
        setupHistoryTable();
        loadHistory();
    }

    private <T> StringConverter<T> nameConverter(java.util.function.Function<T, String> nameFn) {
        return new StringConverter<>() {
            @Override public String toString(T obj) { return obj == null ? "" : nameFn.apply(obj); }
            @Override public T fromString(String string) { return null; }
        };
    }

    private void setupLineTable() {
        lineProductCol.setCellValueFactory(c -> new SimpleStringProperty(productNames.getOrDefault(c.getValue(), "")));
        lineBatchCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().batchNumber == null ? "" : c.getValue().batchNumber));
        lineExpiryCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().expiryDate == null ? "" : c.getValue().expiryDate.format(DateTimeFormatter.ofPattern("dd-MMM-yyyy"))));
        lineQtyCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().quantity)));
        linePriceCol.setCellValueFactory(c -> new SimpleStringProperty("₹" + c.getValue().purchasePrice));
        lineGstCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().gstRate + "%"));
        lineTotalCol.setCellValueFactory(c -> new SimpleStringProperty("₹" + lineTotal(c.getValue()).setScale(2, RoundingMode.HALF_UP)));

        lineRemoveCol.setCellFactory(col -> new TableCell<>() {
            private final Button removeBtn = new Button("Remove");
            private final HBox box = new HBox(removeBtn);
            {
                removeBtn.getStyleClass().add("btn-secondary");
                removeBtn.setOnAction(e -> {
                    PurchaseService.PurchaseLineInput row = getTableView().getItems().get(getIndex());
                    lines.remove(row);
                    productNames.remove(row);
                    updateGrandTotal();
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });

        lineTable.setItems(lines);
    }

    private BigDecimal lineTotal(PurchaseService.PurchaseLineInput line) {
        BigDecimal base = line.purchasePrice.multiply(BigDecimal.valueOf(line.quantity));
        BigDecimal discount = line.discountAmount == null ? BigDecimal.ZERO : line.discountAmount;
        BigDecimal taxable = base.subtract(discount);
        BigDecimal gst = taxable.multiply(line.gstRate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return taxable.add(gst);
    }

    private void setupHistoryTable() {
        histInvoiceCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getInvoiceNumber()));
        histDateCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getPurchaseDate().format(DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm"))));
        histSupplierCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getSupplier().getName()));
        histTotalCol.setCellValueFactory(c -> new SimpleStringProperty("₹" + c.getValue().getGrandTotal()));
        histStatusCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getPaymentStatus().name()));
    }

    private void loadHistory() {
        List<Purchase> purchases = purchaseDao.findAllOrderedByDateDesc();
        purchaseHistoryTable.setItems(FXCollections.observableArrayList(purchases));
    }

    @FXML
    private void handleAddLine() {
        try {
            Product product = productCombo.getValue();
            if (product == null) {
                throw new BusinessException("Please select a product.");
            }
            int qty = Integer.parseInt(requireText(qtyField, "Quantity"));
            if (qty <= 0) throw new BusinessException("Quantity must be greater than zero.");
            BigDecimal price = new BigDecimal(requireText(purchasePriceField, "Purchase price"));
            BigDecimal gst = gstRateField.getText() == null || gstRateField.getText().isBlank()
                    ? BigDecimal.ZERO : new BigDecimal(gstRateField.getText().trim());

            PurchaseService.PurchaseLineInput line = new PurchaseService.PurchaseLineInput();
            line.productId = product.getId();
            line.batchNumber = batchNumberField.getText();
            line.manufacturingDate = mfgDatePicker.getValue();
            line.expiryDate = expiryDatePicker.getValue();
            line.quantity = qty;
            line.purchasePrice = price;
            line.sellingPrice = product.getSellingPrice();
            line.mrp = product.getMrp();
            line.gstRate = gst;
            line.discountAmount = BigDecimal.ZERO;

            lines.add(line);
            productNames.put(line, product.getProductName());
            updateGrandTotal();
            clearLineForm();
            hideError();
        } catch (BusinessException e) {
            showError(e.getMessage());
        } catch (NumberFormatException e) {
            showError("Please enter valid numbers for quantity, price and GST.");
        }
    }

    private void clearLineForm() {
        batchNumberField.clear();
        mfgDatePicker.setValue(null);
        expiryDatePicker.setValue(null);
        qtyField.clear();
        purchasePriceField.clear();
        gstRateField.setText("0");
    }

    private void updateGrandTotal() {
        BigDecimal total = lines.stream().map(this::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discount = overallDiscountField.getText() == null || overallDiscountField.getText().isBlank()
                ? BigDecimal.ZERO : new BigDecimal(overallDiscountField.getText().trim());
        grandTotalLabel.setText("Grand Total: ₹" + total.subtract(discount).setScale(2, RoundingMode.HALF_UP));
    }

    @FXML
    private void handleSavePurchase() {
        try {
            Supplier supplier = supplierCombo.getValue();
            if (supplier == null) {
                throw new BusinessException("Please select a supplier.");
            }
            if (lines.isEmpty()) {
                throw new BusinessException("Add at least one line item before saving.");
            }
            BigDecimal overallDiscount = overallDiscountField.getText() == null || overallDiscountField.getText().isBlank()
                    ? BigDecimal.ZERO : new BigDecimal(overallDiscountField.getText().trim());

            Purchase saved = purchaseService.recordPurchase(supplier.getId(), new ArrayList<>(lines), overallDiscount);

            lines.clear();
            productNames.clear();
            overallDiscountField.setText("0");
            updateGrandTotal();
            loadHistory();
            hideError();

            Alert alert = new Alert(Alert.AlertType.INFORMATION,
                    "Purchase " + saved.getInvoiceNumber() + " saved. Stock has been added to inventory.");
            alert.showAndWait();
        } catch (BusinessException e) {
            showError(e.getMessage());
        } catch (Exception e) {
            log.error("Failed to save purchase", e);
            showError("An unexpected error occurred while saving the purchase.");
        }
    }

    private String requireText(TextField field, String label) {
        String v = field.getText();
        if (v == null || v.isBlank()) {
            throw new BusinessException(label + " is required.");
        }
        return v.trim();
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
