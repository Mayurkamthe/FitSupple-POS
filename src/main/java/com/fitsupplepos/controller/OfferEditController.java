package com.fitsupplepos.controller;

import com.fitsupplepos.dao.CustomerDao;
import com.fitsupplepos.exception.BusinessException;
import com.fitsupplepos.model.Customer;
import com.fitsupplepos.model.Offer;
import com.fitsupplepos.model.Product;
import com.fitsupplepos.model.enums.OfferScope;
import com.fitsupplepos.model.enums.OfferType;
import com.fitsupplepos.model.enums.ProductCategory;
import com.fitsupplepos.service.OfferService;
import com.fitsupplepos.service.ProductService;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.math.BigDecimal;

public class OfferEditController {

    @FXML private Label headerLabel;
    @FXML private TextField nameField;
    @FXML private ComboBox<OfferType> offerTypeCombo;
    @FXML private ComboBox<OfferScope> scopeCombo;
    @FXML private ComboBox<Product> productCombo;
    @FXML private ComboBox<ProductCategory> categoryCombo;
    @FXML private ComboBox<Customer> customerCombo;
    @FXML private TextField couponCodeField;
    @FXML private TextField discountPercentField;
    @FXML private TextField discountFixedField;
    @FXML private ComboBox<Product> buyProductCombo;
    @FXML private TextField buyQtyField;
    @FXML private ComboBox<Product> getProductCombo;
    @FXML private TextField getQtyField;
    @FXML private DatePicker startDatePicker;
    @FXML private DatePicker endDatePicker;
    @FXML private CheckBox activeCheckBox;
    @FXML private Label errorLabel;

    private final OfferService offerService = new OfferService();
    private final ProductService productService = new ProductService();
    private final CustomerDao customerDao = new CustomerDao();

    private Offer editingOffer;
    private Runnable onSaved;

    @FXML
    public void initialize() {
        offerTypeCombo.setItems(FXCollections.observableArrayList(OfferType.values()));
        scopeCombo.setItems(FXCollections.observableArrayList(OfferScope.values()));
        categoryCombo.setItems(FXCollections.observableArrayList(ProductCategory.values()));

        var products = FXCollections.observableArrayList(productService.search(null));
        var productConverter = new StringConverter<Product>() {
            @Override public String toString(Product p) { return p == null ? "" : p.getProductName(); }
            @Override public Product fromString(String s) { return null; }
        };
        productCombo.setItems(products);
        productCombo.setConverter(productConverter);
        buyProductCombo.setItems(products);
        buyProductCombo.setConverter(productConverter);
        getProductCombo.setItems(products);
        getProductCombo.setConverter(productConverter);

        customerCombo.setItems(FXCollections.observableArrayList(customerDao.findAllOrderedByName()));
        customerCombo.setConverter(new StringConverter<>() {
            @Override public String toString(Customer c) { return c == null ? "" : c.getName() + " (" + c.getMobile() + ")"; }
            @Override public Customer fromString(String s) { return null; }
        });
    }

    public void setOnSaved(Runnable onSaved) {
        this.onSaved = onSaved;
    }

    public void configure(Offer offer) {
        this.editingOffer = offer;
        if (offer == null) {
            headerLabel.setText("Add Offer");
            return;
        }
        headerLabel.setText("Edit Offer");
        nameField.setText(offer.getName());
        offerTypeCombo.setValue(offer.getOfferType());
        scopeCombo.setValue(offer.getScope());
        productCombo.setValue(offer.getProduct());
        categoryCombo.setValue(offer.getCategory());
        customerCombo.setValue(offer.getCustomer());
        couponCodeField.setText(offer.getCouponCode());
        discountPercentField.setText(offer.getDiscountPercent() == null ? "" : offer.getDiscountPercent().toPlainString());
        discountFixedField.setText(offer.getDiscountFixed() == null ? "" : offer.getDiscountFixed().toPlainString());
        buyProductCombo.setValue(offer.getBuyProduct());
        buyQtyField.setText(offer.getBuyQuantity() == null ? "" : String.valueOf(offer.getBuyQuantity()));
        getProductCombo.setValue(offer.getGetProduct());
        getQtyField.setText(offer.getGetQuantity() == null ? "" : String.valueOf(offer.getGetQuantity()));
        startDatePicker.setValue(offer.getStartDate());
        endDatePicker.setValue(offer.getEndDate());
        activeCheckBox.setSelected(offer.isActive());
    }

    @FXML
    private void handleSave(ActionEvent event) {
        try {
            Offer offer = editingOffer != null ? editingOffer : new Offer();
            offer.setName(requireText(nameField, "Offer name"));
            offer.setOfferType(offerTypeCombo.getValue());
            offer.setScope(scopeCombo.getValue());
            offer.setProduct(productCombo.getValue());
            offer.setCategory(categoryCombo.getValue());
            offer.setCustomer(customerCombo.getValue());
            offer.setCouponCode(text(couponCodeField));
            offer.setDiscountPercent(decimal(discountPercentField));
            offer.setDiscountFixed(decimal(discountFixedField));
            offer.setBuyProduct(buyProductCombo.getValue());
            offer.setBuyQuantity(intVal(buyQtyField));
            offer.setGetProduct(getProductCombo.getValue());
            offer.setGetQuantity(intVal(getQtyField));
            offer.setStartDate(startDatePicker.getValue());
            offer.setEndDate(endDatePicker.getValue());
            offer.setActive(activeCheckBox.isSelected());

            if (editingOffer != null) {
                offerService.update(offer);
            } else {
                offerService.create(offer);
            }
            if (onSaved != null) onSaved.run();
            closeWindow(event);
        } catch (BusinessException e) {
            showError(e.getMessage());
        } catch (NumberFormatException e) {
            showError("Please enter valid numbers for discount/quantity fields.");
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

    private String text(TextField field) {
        String v = field.getText();
        return v == null || v.isBlank() ? null : v.trim();
    }

    private BigDecimal decimal(TextField field) {
        String v = field.getText();
        return v == null || v.isBlank() ? null : new BigDecimal(v.trim());
    }

    private Integer intVal(TextField field) {
        String v = field.getText();
        return v == null || v.isBlank() ? null : Integer.parseInt(v.trim());
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
