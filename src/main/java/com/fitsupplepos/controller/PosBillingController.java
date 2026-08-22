package com.fitsupplepos.controller;

import com.fitsupplepos.exception.BusinessException;
import com.fitsupplepos.model.Customer;
import com.fitsupplepos.model.GstSetting;
import com.fitsupplepos.model.Offer;
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

import java.io.File;
import java.io.IOException;

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
    @FXML private TextField couponField;
    @FXML private Label offersAppliedLabel;

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
    @FXML private Label couponDiscountLabel;
    @FXML private Label taxableLabel;
    @FXML private Label gstLabel;
    @FXML private Label grandTotalLabel;

    private final ProductService productService = new ProductService();
    private final CustomerService customerService = new CustomerService();
    private final SaleService saleService = new SaleService();
    private final com.fitsupplepos.service.InvoiceService invoiceService = new com.fitsupplepos.service.InvoiceService();

    private final ObservableList<CartRow> cart = FXCollections.observableArrayList();
    private final com.fitsupplepos.service.OfferService offerService = new com.fitsupplepos.service.OfferService();
    private BillingMode billingMode = BillingMode.NON_GST;
    private GstSetting gstSetting;
    private Sale lastSavedSale;
    private List<Offer> validOffers = new ArrayList<>();
    private Offer appliedCoupon;
    private BigDecimal couponDiscountTotal = BigDecimal.ZERO;

    /** Simple client-side estimate row; authoritative GST/FEFO math happens server-side in SaleService. */
    public static class CartRow {
        Product product;
        int quantity;
        BigDecimal discount;
        /** Discount contributed by an auto-applied Offer (percentage/fixed/customer-specific), on top of {@link #discount}. */
        BigDecimal offerDiscount = BigDecimal.ZERO;
        /** True for a "Get Y" line auto-added by a Buy-X-Get-Y offer — recomputed on every cart change, never manually edited. */
        boolean freebie = false;

        CartRow(Product product, int quantity, BigDecimal discount) {
            this.product = product;
            this.quantity = quantity;
            this.discount = discount;
        }

        BigDecimal totalDiscount() {
            return discount.add(offerDiscount);
        }

        BigDecimal lineTotal(BigDecimal gstRateOverride) {
            BigDecimal gross = product.getSellingPrice().multiply(BigDecimal.valueOf(quantity));
            BigDecimal taxable = gross.subtract(totalDiscount()).max(BigDecimal.ZERO);
            BigDecimal gst = taxable.multiply(gstRateOverride).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            return taxable.add(gst);
        }
    }

    @FXML
    public void initialize() {
        gstSetting = SessionManager.withSession(session -> session.get(GstSetting.class, 1L));
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
        customerCombo.valueProperty().addListener((obs, oldC, newC) -> { applyAutoOffers(); recalcTotals(); });

        validOffers = offerService.listCurrentlyValid();

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
        cartDiscountCol.setCellValueFactory(c -> new SimpleStringProperty(
                "₹" + c.getValue().totalDiscount().setScale(2, RoundingMode.HALF_UP)
                        + (c.getValue().freebie ? " (offer)" : c.getValue().offerDiscount.signum() > 0 ? " (incl. offer)" : "")));
        cartTotalCol.setCellValueFactory(c -> new SimpleStringProperty("₹" + c.getValue().lineTotal(effectiveGstRate(c.getValue())).setScale(2, RoundingMode.HALF_UP)));

        cartRemoveCol.setCellFactory(col -> new TableCell<>() {
            private final Button removeBtn = new Button("Remove");
            private final HBox box = new HBox(removeBtn);
            {
                removeBtn.getStyleClass().add("btn-secondary");
                removeBtn.setOnAction(e -> {
                    cart.remove(getTableView().getItems().get(getIndex()));
                    applyAutoOffers();
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

    /** Mirrors SaleService's tax-type decision so the on-screen estimate matches what gets saved. */
    private boolean isInterStateForSelectedCustomer() {
        if (gstSetting == null) return false;
        String shopState = gstSetting.getStateCode();
        Customer customer = customerCombo.getValue();
        String customerState = customer != null ? customer.getStateCode() : null;
        return shopState != null && !shopState.isBlank()
                && customerState != null && !customerState.isBlank()
                && !shopState.trim().equalsIgnoreCase(customerState.trim());
    }

    private String gstBreakdownLabel() {
        return isInterStateForSelectedCustomer() ? "IGST" : "CGST+SGST";
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
            if (!row.freebie && row.product.getId().equals(product.getId())) {
                row.quantity += qty;
                applyAutoOffers();
                recalcTotals();
                return;
            }
        }
        cart.add(new CartRow(product, qty, discount));
        applyAutoOffers();
        recalcTotals();
    }

    /**
     * Re-evaluates every currently-valid Offer against the cart: applies the best
     * percentage/fixed/customer-specific discount to each real line, and (re)generates
     * "free" lines for any Buy-X-Get-Y offer the cart now qualifies for. Called after
     * every cart or customer change so the bill always reflects what's actually in the
     * cart right now — never a stale offer from a previous state.
     */
    private void applyAutoOffers() {
        cart.removeIf(row -> row.freebie);

        Customer customer = customerCombo.getValue();
        List<String> appliedNames = new ArrayList<>();

        for (CartRow row : cart) {
            BigDecimal gross = row.product.getSellingPrice().multiply(BigDecimal.valueOf(row.quantity));
            var best = offerService.findBestAutoDiscount(validOffers, row.product, customer, gross);
            if (best.isPresent()) {
                row.offerDiscount = offerService.discountAmountFor(best.get(), gross);
                if (row.offerDiscount.signum() > 0) appliedNames.add(best.get().getName());
            } else {
                row.offerDiscount = BigDecimal.ZERO;
            }
        }

        List<CartRow> baseRows = new ArrayList<>(cart);
        for (CartRow row : baseRows) {
            for (Offer offer : offerService.findBuyXGetYOffersFor(validOffers, row.product)) {
                int buyQty = offer.getBuyQuantity();
                if (buyQty <= 0 || row.quantity < buyQty) continue;
                int multiples = row.quantity / buyQty;
                int freeQty = multiples * offer.getGetQuantity();
                if (freeQty <= 0 || offer.getGetProduct() == null) continue;

                CartRow freebie = new CartRow(offer.getGetProduct(), freeQty, BigDecimal.ZERO);
                freebie.freebie = true;
                freebie.offerDiscount = offer.getGetProduct().getSellingPrice().multiply(BigDecimal.valueOf(freeQty));
                cart.add(freebie);
                appliedNames.add(offer.getName());
            }
        }

        if (appliedNames.isEmpty()) {
            offersAppliedLabel.setVisible(false);
            offersAppliedLabel.setManaged(false);
        } else {
            offersAppliedLabel.setText("Offers applied: " + String.join(", ", appliedNames.stream().distinct().toList()));
            offersAppliedLabel.setVisible(true);
            offersAppliedLabel.setManaged(true);
        }
        cartTable.refresh();
    }

    @FXML
    private void handleApplyCoupon() {
        String code = couponField.getText();
        var offer = offerService.findCouponOffer(validOffers, code);
        if (offer.isEmpty()) {
            appliedCoupon = null;
            couponDiscountTotal = BigDecimal.ZERO;
            showError("No active offer found for coupon \"" + (code == null ? "" : code.trim()) + "\".");
            recalcTotals();
            return;
        }
        appliedCoupon = offer.get();
        hideError();
        recalcTotals();
    }

    private void recalcTotals() {
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal discountTotal = BigDecimal.ZERO;
        BigDecimal gstTotal = BigDecimal.ZERO;

        for (CartRow row : cart) {
            BigDecimal gross = row.product.getSellingPrice().multiply(BigDecimal.valueOf(row.quantity));
            subtotal = subtotal.add(gross);
            discountTotal = discountTotal.add(row.totalDiscount());
            BigDecimal taxable = gross.subtract(row.totalDiscount()).max(BigDecimal.ZERO);
            BigDecimal gst = taxable.multiply(effectiveGstRate(row)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            gstTotal = gstTotal.add(gst);
        }

        BigDecimal taxableBeforeCoupon = subtotal.subtract(discountTotal).max(BigDecimal.ZERO);

        couponDiscountTotal = BigDecimal.ZERO;
        if (appliedCoupon != null) {
            couponDiscountTotal = offerService.discountAmountFor(appliedCoupon, taxableBeforeCoupon);
        }

        BigDecimal taxable = taxableBeforeCoupon.subtract(couponDiscountTotal).max(BigDecimal.ZERO);
        BigDecimal rawTotal = taxable.add(gstTotal);
        BigDecimal grandTotal = rawTotal.setScale(0, RoundingMode.HALF_UP);

        subtotalLabel.setText("Subtotal: ₹" + subtotal.setScale(2, RoundingMode.HALF_UP));
        discountTotalLabel.setText("Discount: ₹" + discountTotal.setScale(2, RoundingMode.HALF_UP));
        if (couponDiscountTotal.signum() > 0) {
            couponDiscountLabel.setText("Coupon Discount (" + appliedCoupon.getCouponCode() + "): ₹" + couponDiscountTotal.setScale(2, RoundingMode.HALF_UP));
            couponDiscountLabel.setVisible(true);
            couponDiscountLabel.setManaged(true);
        } else {
            couponDiscountLabel.setVisible(false);
            couponDiscountLabel.setManaged(false);
        }
        taxableLabel.setText("Taxable Amount: ₹" + taxable.setScale(2, RoundingMode.HALF_UP));
        gstLabel.setText("GST (" + gstBreakdownLabel() + "): ₹" + gstTotal.setScale(2, RoundingMode.HALF_UP));
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
        couponField.clear();
        appliedCoupon = null;
        couponDiscountTotal = BigDecimal.ZERO;
        validOffers = offerService.listCurrentlyValid();
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

            // Distribute the coupon discount (a whole-cart amount) proportionally across lines
            // by their pre-coupon taxable value, since SaleService/Sale only model per-line
            // discounts — there's no separate "cart-level discount" column on Sale.
            BigDecimal taxableBeforeCoupon = BigDecimal.ZERO;
            for (CartRow row : cart) {
                BigDecimal gross = row.product.getSellingPrice().multiply(BigDecimal.valueOf(row.quantity));
                taxableBeforeCoupon = taxableBeforeCoupon.add(gross.subtract(row.totalDiscount()).max(BigDecimal.ZERO));
            }

            List<SaleService.CartLineInput> lines = new ArrayList<>();
            BigDecimal couponAllocated = BigDecimal.ZERO;
            int rowIndex = 0;
            for (CartRow row : cart) {
                rowIndex++;
                BigDecimal gross = row.product.getSellingPrice().multiply(BigDecimal.valueOf(row.quantity));
                BigDecimal lineTaxable = gross.subtract(row.totalDiscount()).max(BigDecimal.ZERO);

                BigDecimal couponShare = BigDecimal.ZERO;
                if (couponDiscountTotal.signum() > 0 && taxableBeforeCoupon.signum() > 0) {
                    if (rowIndex == cart.size()) {
                        // last row absorbs any rounding remainder so the allocated total matches exactly
                        couponShare = couponDiscountTotal.subtract(couponAllocated);
                    } else {
                        couponShare = couponDiscountTotal.multiply(lineTaxable)
                                .divide(taxableBeforeCoupon, 2, RoundingMode.HALF_UP);
                        couponAllocated = couponAllocated.add(couponShare);
                    }
                }

                SaleService.CartLineInput line = new SaleService.CartLineInput();
                line.productId = row.product.getId();
                line.quantity = row.quantity;
                line.discountAmount = row.totalDiscount().add(couponShare);
                lines.add(line);
            }

            Customer customer = customerCombo.getValue();
            Sale sale = saleService.recordSale(customer == null ? null : customer.getId(), lines, method, amountPaid);
            lastSavedSale = sale;

            Alert alert = new Alert(Alert.AlertType.INFORMATION,
                    "Bill saved as invoice " + sale.getInvoiceNumber() + ".\nGrand Total: ₹" + sale.getGrandTotal()
                            + "\n\nUse \"Save PDF\" or \"F6 Print\" to generate the invoice document.");
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
        if (lastSavedSale == null) {
            info("Save a bill first, then Print generates and opens the invoice for printing.");
            return;
        }
        try {
            File file = invoiceService.generateA4Invoice(lastSavedSale);
            openWithSystemViewer(file);
        } catch (IOException e) {
            log.error("Failed to generate invoice PDF for printing", e);
            info("Could not generate the invoice PDF: " + e.getMessage());
        }
    }

    @FXML
    private void handleSavePdf() {
        if (lastSavedSale == null) {
            info("Save a bill first, then Save PDF will generate the invoice document.");
            return;
        }
        try {
            File a4 = invoiceService.generateA4Invoice(lastSavedSale);
            File thermal = invoiceService.generateThermalReceipt(lastSavedSale);
            info("Invoice saved:\n" + a4.getAbsolutePath() + "\n" + thermal.getAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to save invoice PDF", e);
            info("Could not save the invoice PDF: " + e.getMessage());
        }
    }

    private void openWithSystemViewer(File file) {
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
                if (desktop.isSupported(java.awt.Desktop.Action.PRINT)) {
                    desktop.print(file);
                    return;
                }
                if (desktop.isSupported(java.awt.Desktop.Action.OPEN)) {
                    desktop.open(file);
                    return;
                }
            }
            info("Invoice generated at: " + file.getAbsolutePath()
                    + "\n(Automatic printing is not supported on this system — please open and print it manually.)");
        } catch (Exception e) {
            log.error("Failed to open/print invoice file", e);
            info("Invoice generated at: " + file.getAbsolutePath() + "\nCould not auto-open it: " + e.getMessage());
        }
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
