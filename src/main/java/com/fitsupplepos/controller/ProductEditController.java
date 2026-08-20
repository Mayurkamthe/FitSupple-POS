package com.fitsupplepos.controller;

import com.fitsupplepos.dao.BrandDao;
import com.fitsupplepos.exception.BusinessException;
import com.fitsupplepos.model.Brand;
import com.fitsupplepos.model.Product;
import com.fitsupplepos.model.enums.ProductCategory;
import com.fitsupplepos.service.ProductService;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.util.List;

public class ProductEditController {

    @FXML private Label headerLabel;
    @FXML private TextField nameField;
    @FXML private ComboBox<String> brandCombo;
    @FXML private ComboBox<ProductCategory> categoryCombo;
    @FXML private TextField subCategoryField;
    @FXML private TextField flavourField;
    @FXML private TextField sizeField;
    @FXML private TextField unitField;
    @FXML private TextField skuField;
    @FXML private TextField barcodeField;
    @FXML private TextField hsnField;
    @FXML private TextField gstRateField;
    @FXML private TextField mrpField;
    @FXML private TextField purchasePriceField;
    @FXML private TextField sellingPriceField;
    @FXML private TextField minimumStockField;
    @FXML private CheckBox activeCheckBox;
    @FXML private Label errorLabel;

    private final ProductService productService = new ProductService();
    private final BrandDao brandDao = new BrandDao();

    private Product editingProduct;
    private boolean saved = false;
    private Runnable onSaved;

    @FXML
    public void initialize() {
        categoryCombo.setItems(FXCollections.observableArrayList(ProductCategory.values()));
        List<Brand> brands = brandDao.findAll();
        brandCombo.setItems(FXCollections.observableArrayList(brands.stream().map(Brand::getName).toList()));
    }

    public void setOnSaved(Runnable onSaved) {
        this.onSaved = onSaved;
    }

    /** Pass null to create a new product; pass an existing product to edit it. */
    public void configure(Product product) {
        this.editingProduct = product;
        if (product == null) {
            headerLabel.setText("Add Product");
            minimumStockField.setText("5");
            gstRateField.setText("0");
            return;
        }
        headerLabel.setText("Edit Product");
        nameField.setText(product.getProductName());
        if (product.getBrand() != null) brandCombo.setValue(product.getBrand().getName());
        categoryCombo.setValue(product.getCategory());
        subCategoryField.setText(product.getSubCategory());
        flavourField.setText(product.getFlavour());
        sizeField.setText(product.getSize());
        unitField.setText(product.getUnit());
        skuField.setText(product.getSku());
        barcodeField.setText(product.getBarcode());
        hsnField.setText(product.getHsnCode());
        gstRateField.setText(str(product.getGstRate()));
        mrpField.setText(str(product.getMrp()));
        purchasePriceField.setText(str(product.getPurchasePrice()));
        sellingPriceField.setText(str(product.getSellingPrice()));
        minimumStockField.setText(String.valueOf(product.getMinimumStock()));
        activeCheckBox.setSelected(product.isActive());
    }

    public boolean isSaved() {
        return saved;
    }

    @FXML
    private void handleSave(ActionEvent event) {
        try {
            Product product = editingProduct != null ? editingProduct : new Product();
            product.setProductName(requireText(nameField, "Product name"));
            product.setCategory(requireCombo(categoryCombo, "Category"));
            product.setSubCategory(text(subCategoryField));
            product.setFlavour(text(flavourField));
            product.setSize(text(sizeField));
            product.setUnit(text(unitField));
            product.setSku(text(skuField));
            product.setBarcode(text(barcodeField));
            product.setHsnCode(text(hsnField));
            product.setGstRate(decimal(gstRateField, BigDecimal.ZERO));
            product.setMrp(decimal(mrpField, null));
            product.setPurchasePrice(decimal(purchasePriceField, null));
            product.setSellingPrice(decimal(sellingPriceField, null));
            product.setMinimumStock(intVal(minimumStockField, 5));
            product.setActive(activeCheckBox.isSelected());

            String brandName = brandCombo.getValue();
            if (brandName != null && !brandName.isBlank()) {
                Brand brand = brandDao.findByName(brandName).orElseGet(() -> brandDao.save(new Brand(brandName)));
                product.setBrand(brand);
            }

            if (editingProduct != null) {
                productService.updateProduct(product);
            } else {
                productService.createProduct(product);
            }

            saved = true;
            if (onSaved != null) onSaved.run();
            closeWindow(event);
        } catch (BusinessException e) {
            showError(e.getMessage());
        } catch (NumberFormatException e) {
            showError("Please enter valid numbers for price/stock fields.");
        }
    }

    @FXML
    private void handleCancel(ActionEvent event) {
        closeWindow(event);
    }

    private void closeWindow(ActionEvent event) {
        Stage stage = (Stage) ((Button) event.getSource()).getScene().getWindow();
        stage.close();
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private String requireText(TextField field, String label) {
        String v = field.getText();
        if (v == null || v.isBlank()) {
            throw new BusinessException(label + " is required.");
        }
        return v.trim();
    }

    private <T> T requireCombo(ComboBox<T> combo, String label) {
        T v = combo.getValue();
        if (v == null) {
            throw new BusinessException(label + " is required.");
        }
        return v;
    }

    private String text(TextField field) {
        String v = field.getText();
        return (v == null || v.isBlank()) ? null : v.trim();
    }

    private BigDecimal decimal(TextField field, BigDecimal defaultIfBlank) {
        String v = field.getText();
        if (v == null || v.isBlank()) {
            if (defaultIfBlank == null) {
                throw new BusinessException("This field is required.");
            }
            return defaultIfBlank;
        }
        return new BigDecimal(v.trim());
    }

    private int intVal(TextField field, int defaultVal) {
        String v = field.getText();
        if (v == null || v.isBlank()) return defaultVal;
        return Integer.parseInt(v.trim());
    }

    private String str(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }
}
