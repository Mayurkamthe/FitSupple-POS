package com.fitsupplepos.controller;

import com.fitsupplepos.dao.InvoiceSettingDao;
import com.fitsupplepos.exception.BusinessException;
import com.fitsupplepos.model.Customer;
import com.fitsupplepos.model.GstSetting;
import com.fitsupplepos.model.Product;
import com.fitsupplepos.model.Sale;
import com.fitsupplepos.model.enums.BillingMode;
import com.fitsupplepos.model.enums.PaymentMethod;
import com.fitsupplepos.service.CustomerService;
import com.fitsupplepos.service.ProductService;
import com.fitsupplepos.service.SaleService;
import com.fitsupplepos.config.SessionManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class PosBillingController {

    private static final Logger log = LoggerFactory.getLogger(PosBillingController.class);

    @FXML private Label billingModeLabel;
    @FXML private TextField barcodeField;
    @FXML private ComboBox<Product> productCombo;
    @FXML private TextField qtyField;
    @FXML private TextField discountField;
    @FXML private Label errorLabel;

    @FXML private ComboBox<Customer> customerCombo;

    @FXML private TableView<CartRow> cartTable;
    @FXML private TableColumn<CartRow, String> cartProductCol;
    @FXML private TableColumn<CartRow, String> cartRateCol;
    @FXML private TableColumn<CartRow, String> cartQtyCol;
    @FXML private TableColumn<CartRow, String> cartDiscountCol;
    @FXML private TableColumn<CartRow, String> cartTotalCol;
    @FXML private TableColumn<CartRow, Void> cartRemoveCol;

    @FXML private ComboBox<PaymentMethod> paymentMethodCombo;
    @FXML private TextField amountPaidField;

    @FXML private Label subtotalLabel;
    @FXML private Label discountTotalLabel;
    @FXML private Label taxableLabel;
    @FXML private Label gstLabel;
    @FXML private Label grandTotalLabel;

    private final ProductService productService = new ProductService();
    private final CustomerService customerService = new CustomerService();
    private final SaleService saleService = new SaleService();
    private final InvoiceSettingDao invoiceSettingDao = new InvoiceSettingDao();

    private final ObservableList<CartRow> cart = FXCollections.observableArrayList();
    private BillingMode billingMode = BillingMode.NON_GST;

    /** Simple client-side estimate row; authoritative GST/FEFO math happens server-side in SaleService. */
    public static class CartRow {
        Product product;
        int quantity;
        BigDecimal discount;

        CartRow(Product product, int quantity, BigDecimal discount) {
            this.product = product;
            this.quantity = quantity;
            this.discount = discount;
        }

        BigDecimal lineTotal(BigDecimal gstRateOverride) {
            BigDecimal gross = product.getSellingPrice().multiply(BigDecimal.valueOf(quantity));
            BigDecimal taxable = gross.subtract(discount);
            BigDecimal gst = taxable.multiply(gstRateOverride).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            return taxable.add(gst);
        }
    }

    @FXML
    public void initialize() {
        GstSetting gstSetting = SessionManager.withSession(session -> session.get(GstSetting.class, 1L));
        billingMode = gstSetting != null ? gstSetting.getBillingMode() : BillingMode.NON_GST;
        billingModeLabel.setText(billingMode == BillingMode.GST ? "GST BILLING" : "NON-GST BILLING");

        productCombo.setItems(FXCollections.observableArrayList(productService.search(null)));
        productCombo.setConverter(new StringConverter<>() {
            @Override public String toString(Product p) { return p == null ? "" : p.getProductName(); }
            @Override public Product fromString(String s) { return null; }
        });

        customerCombo.setItems(FXCollections.observableArrayList(customerService.listAll()));
        customerCombo.setConverter(new StringConverter<>() {
            @Override public String toString(Customer c) { return c == null ? "Walk-in Customer" : c.getName() + " (" + c.getMobile() + ")"; }
            @Override public Customer fromString(String s) { return null; }
        });

        paymentMethodCombo.setItems(FXCollections.observableArrayList(PaymentMethod.values()));
        paymentMethodCombo.setValue(PaymentMethod.CASH);

        setupCartTable();
        recalcTotals();

        cartTable.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                registerShortcuts(newScene);
            }
        });
    }

    private void registerShortcuts(javafx.scene.Scene scene) {
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F1), this::handleNewBill);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F2), () -> barcodeField.requestFocus());
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F3), () -> customerCombo.requestFocus());
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F4), () -> paymentMethodCombo.requestFocus());
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F5), this::handleSaveBill);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F6), this::handlePrint);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F7), this::handleWhatsApp);
    }

    private void setupCartTable() {
        cartProductCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().product.getProductName()));
        cartRateCol.setCellValueFactory(c -> new SimpleStringProperty("₹" + c.getValue().product.getSellingPrice()));
        cartQtyCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().quantity)));
        cartDiscountCol.setCellValueFactory(c -> new SimpleStringProperty("₹" + c.getValue().discount));
        cartTotalCol.setCellValueFactory(c -> new SimpleStringProperty("₹" + c.getValue().lineTotal(effectiveGstRate(c.getValue())).setScale(2, RoundingMode.HALF_UP)));

        cartRemoveCol.setCellFactory(col -> new TableCell<>() {
            private final Button removeBtn = new Button("Remove");
            private final HBox box = new HBox(removeBtn);
            {
                removeBtn.getStyleClass().add("btn-secondary");
                removeBtn.setOnAction(e -> {
                    cart.remove(getTableView().getItems().get(getIndex()));
                    recalcTotals();
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });

        cartTable.setItems(cart);
    }

    private BigDecimal effectiveGstRate(CartRow row) {
        if (billingMode != BillingMode.GST) return BigDecimal.ZERO;
        return row.product.getGstRate() != null ? row.product.getGstRate() : BigDecimal.ZERO;
    }

    @FXML
    private void handleBarcodeEnter(ActionEvent event) {
        String code = barcodeField.getText();
        if (code == null || code.isBlank()) return;
        productService.findByBarcode(code.trim()).ifPresentOrElse(
                p -> {
                    addProductToCart(p, 1, BigDecimal.ZERO);
                    barcodeField.clear();
                    hideError();
                },
                () -> showError("No product found for barcode \"" + code + "\".")
        );
    }

    @FXML
    private void handleAddToCart() {
        try {
            Product product = productCombo.getValue();
            if (product == null) {
                throw new BusinessException("Select a product to add.");
            }
            int qty = Integer.parseInt(qtyField.getText() == null || qtyField.getText().isBlank() ? "1" : qtyField.getText().trim());
            if (qty <= 0) throw new BusinessException("Quantity must be greater than zero.");
            BigDecimal discount = discountField.getText() == null || discountField.getText().isBlank()
                    ? BigDecimal.ZERO : new BigDecimal(discountField.getText().trim());

            addProductToCart(product, qty, discount);
            qtyField.setText("1");
            discountField.setText("0");
            hideError();
        } catch (BusinessException e) {
            showError(e.getMessage());
        } catch (NumberFormatException e) {
            showError("Please enter a valid quantity and discount.");
        }
    }

    private void addProductToCart(Product product, int qty, BigDecimal discount) {
        for (CartRow row : cart) {
            if (row.product.getId().equals(product.getId())) {
                row.quantity += qty;
                cartTable.refresh();
                recalcTotals();
                return;
            }
        }
        cart.add(new CartRow(product, qty, discount));
        recalcTotals();
    }

    private void recalcTotals() {
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal discountTotal = BigDecimal.ZERO;
        BigDecimal gstTotal = BigDecimal.ZERO;

        for (CartRow row : cart) {
            BigDecimal gross = row.product.getSellingPrice().multiply(BigDecimal.valueOf(row.quantity));
            subtotal = subtotal.add(gross);
            discountTotal = discountTotal.add(row.discount);
            BigDecimal taxable = gross.subtract(row.discount);
            BigDecimal gst = taxable.multiply(effectiveGstRate(row)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            gstTotal = gstTotal.add(gst);
        }

        BigDecimal taxable = subtotal.subtract(discountTotal);
        BigDecimal rawTotal = taxable.add(gstTotal);
        BigDecimal grandTotal = rawTotal.setScale(0, RoundingMode.HALF_UP);

        subtotalLabel.setText("Subtotal: ₹" + subtotal.setScale(2, RoundingMode.HALF_UP));
        discountTotalLabel.setText("Discount: ₹" + discountTotal.setScale(2, RoundingMode.HALF_UP));
        taxableLabel.setText("Taxable Amount: ₹" + taxable.setScale(2, RoundingMode.HALF_UP));
        gstLabel.setText("GST (CGST+SGST): ₹" + gstTotal.setScale(2, RoundingMode.HALF_UP));
        grandTotalLabel.setText("Grand Total: ₹" + grandTotal);
        amountPaidField.setText(grandTotal.toPlainString());
    }

    @FXML
    private void handleClearCustomer() {
        customerCombo.setValue(null);
    }

    @FXML
    private void handleNewBill() {
        cart.clear();
        customerCombo.setValue(null);
        amountPaidField.clear();
        recalcTotals();
        hideError();
        barcodeField.requestFocus();
    }

    @FXML
    private void handleSaveBill() {
        try {
            if (cart.isEmpty()) {
                throw new BusinessException("Add at least one item to the bill before saving.");
            }
            PaymentMethod method = paymentMethodCombo.getValue();
            if (method == null) {
                throw new BusinessException("Select a payment method.");
            }
            BigDecimal amountPaid = amountPaidField.getText() == null || amountPaidField.getText().isBlank()
                    ? BigDecimal.ZERO : new BigDecimal(amountPaidField.getText().trim());

            List<SaleService.CartLineInput> lines = new ArrayList<>();
            for (CartRow row : cart) {
                SaleService.CartLineInput line = new SaleService.CartLineInput();
                line.productId = row.product.getId();
                line.quantity = row.quantity;
                line.discountAmount = row.discount;
                lines.add(line);
            }

            Customer customer = customerCombo.getValue();
            Sale sale = saleService.recordSale(customer == null ? null : customer.getId(), lines, method, amountPaid);

            Alert alert = new Alert(Alert.AlertType.INFORMATION,
                    "Bill saved as invoice " + sale.getInvoiceNumber() + ".\nGrand Total: ₹" + sale.getGrandTotal()
                            + "\n\n(PDF invoice generation and printing are part of the Invoice module — coming next.)");
            alert.showAndWait();

            handleNewBill();
        } catch (BusinessException e) {
            showError(e.getMessage());
        } catch (NumberFormatException e) {
            showError("Please enter a valid amount paid.");
        } catch (Exception e) {
            log.error("Failed to save bill", e);
            showError("An unexpected error occurred while saving the bill.");
        }
    }

    @FXML
    private void handlePrint() {
        info("Thermal/A4 printing is part of the Invoice module — coming in the next build phase.");
    }

    @FXML
    private void handleWhatsApp() {
        info("WhatsApp invoice sending is part of the WhatsApp module — coming in a later build phase.");
    }

    private void info(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message);
        alert.showAndWait();
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
