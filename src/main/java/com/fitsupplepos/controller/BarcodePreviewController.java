package com.fitsupplepos.controller;

import com.fitsupplepos.exception.BusinessException;
import com.fitsupplepos.model.Product;
import com.fitsupplepos.service.BarcodeService;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

public class BarcodePreviewController {

    private static final Logger log = LoggerFactory.getLogger(BarcodePreviewController.class);

    @FXML private Label productNameLabel;
    @FXML private ImageView barcodeImageView;
    @FXML private Label codeLabel;
    @FXML private TextField copiesField;
    @FXML private Label errorLabel;

    private final BarcodeService barcodeService = new BarcodeService();
    private Product product;
    private String code;

    public void configure(Product product) {
        this.product = product;
        this.code = product.getBarcode() != null && !product.getBarcode().isBlank()
                ? product.getBarcode() : product.getSku();

        productNameLabel.setText(product.getProductName());
        codeLabel.setText(code == null || code.isBlank() ? "No SKU/barcode value set for this product." : code);

        if (code != null && !code.isBlank()) {
            try {
                BufferedImage image = barcodeService.generateBarcodeImage(code, 280, 90);
                barcodeImageView.setImage(SwingFXUtils.toFXImage(image, null));
            } catch (BusinessException e) {
                showError(e.getMessage());
            }
        }
    }

    @FXML
    private void handleSaveLabelSheet(ActionEvent event) {
        if (code == null || code.isBlank()) {
            showError("This product has no SKU or barcode value to print.");
            return;
        }
        int copies;
        try {
            copies = Integer.parseInt(copiesField.getText().trim());
            if (copies <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            showError("Enter a valid number of copies.");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setInitialFileName(product.getProductName().replaceAll("\\s+", "_") + "_labels.pdf");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
        File file = chooser.showSaveDialog(((Button) event.getSource()).getScene().getWindow());
        if (file == null) return;

        try {
            barcodeService.generateLabelSheet(file, List.of(product), copies);
            new Alert(Alert.AlertType.INFORMATION, "Label sheet saved to " + file.getName()).showAndWait();
        } catch (Exception e) {
            log.error("Failed to generate label sheet", e);
            showError("Could not generate label sheet: " + e.getMessage());
        }
    }

    @FXML
    private void handleClose(ActionEvent event) {
        Stage stage = (Stage) ((Button) event.getSource()).getScene().getWindow();
        stage.close();
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }
}
