package com.fitsupplepos.controller;

import com.fitsupplepos.dao.SaleDao;
import com.fitsupplepos.dao.SaleItemDao;
import com.fitsupplepos.dao.SalesReturnDao;
import com.fitsupplepos.exception.BusinessException;
import com.fitsupplepos.model.Sale;
import com.fitsupplepos.model.SaleItem;
import com.fitsupplepos.model.SalesReturn;
import com.fitsupplepos.model.enums.ReturnReason;
import com.fitsupplepos.service.ReturnService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class SalesReturnController {

    @FXML private TextField invoiceField;
    @FXML private Label saleInfoLabel;
    @FXML private Label errorLabel;

    @FXML private TableView<SaleItem> itemsTable;
    @FXML private TableColumn<SaleItem, String> itemProductCol;
    @FXML private TableColumn<SaleItem, String> itemBatchCol;
    @FXML private TableColumn<SaleItem, String> itemQtyCol;
    @FXML private TableColumn<SaleItem, String> itemReturnedCol;
    @FXML private TableColumn<SaleItem, String> itemReturnableCol;
    @FXML private TableColumn<SaleItem, String> itemTotalCol;

    @FXML private TextField returnQtyField;
    @FXML private ComboBox<ReturnReason> reasonCombo;
    @FXML private TextArea notesField;

    @FXML private TableView<SalesReturn> recentReturnsTable;
    @FXML private TableColumn<SalesReturn, String> recentDateCol;
    @FXML private TableColumn<SalesReturn, String> recentInvoiceCol;
    @FXML private TableColumn<SalesReturn, String> recentProductCol;
    @FXML private TableColumn<SalesReturn, String> recentQtyCol;
    @FXML private TableColumn<SalesReturn, String> recentReasonCol;
    @FXML private TableColumn<SalesReturn, String> recentRefundCol;

    private final SaleDao saleDao = new SaleDao();
    private final SaleItemDao saleItemDao = new SaleItemDao();
    private final SalesReturnDao salesReturnDao = new SalesReturnDao();
    private final ReturnService returnService = new ReturnService();

    private Sale currentSale;

    @FXML
    public void initialize() {
        reasonCombo.setItems(FXCollections.observableArrayList(ReturnReason.values()));

        itemProductCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getProduct().getProductName()));
        itemBatchCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getBatch().getBatchNumber()));
        itemQtyCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getQuantity())));
        itemReturnedCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(alreadyReturned(c.getValue()))));
        itemReturnableCol.setCellValueFactory(c -> new SimpleStringProperty(
                String.valueOf(c.getValue().getQuantity() - alreadyReturned(c.getValue()))));
        itemTotalCol.setCellValueFactory(c -> new SimpleStringProperty("₹" + c.getValue().getLineTotal().setScale(2, RoundingMode.HALF_UP)));

        recentDateCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getReturnedAt().format(DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm"))));
        recentInvoiceCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getSale().getInvoiceNumber()));
        recentProductCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getSaleItem().getProduct().getProductName()));
        recentQtyCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getReturnQuantity())));
        recentReasonCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getReturnReason().name()));
        recentRefundCol.setCellValueFactory(c -> new SimpleStringProperty("₹" + c.getValue().getRefundAmount().setScale(2, RoundingMode.HALF_UP)));

        loadRecentReturns();
    }

    private int alreadyReturned(SaleItem item) {
        return com.fitsupplepos.config.SessionManager.withSession(session -> salesReturnDao.sumReturnedForSaleItem(session, item.getId()));
    }

    @FXML
    private void handleSearchInvoice() {
        String invoiceNo = invoiceField.getText();
        if (invoiceNo == null || invoiceNo.isBlank()) {
            showError("Enter an invoice number to search.");
            return;
        }
        saleDao.findByInvoiceNumber(invoiceNo.trim()).ifPresentOrElse(sale -> {
            currentSale = sale;
            List<SaleItem> items = saleItemDao.findForSale(sale.getId());
            itemsTable.setItems(FXCollections.observableArrayList(items));
            saleInfoLabel.setText("Invoice " + sale.getInvoiceNumber() + " — "
                    + (sale.getCustomer() != null ? sale.getCustomer().getName() : "Walk-in Customer")
                    + " — Grand Total ₹" + sale.getGrandTotal());
            hideError();
        }, () -> {
            currentSale = null;
            itemsTable.setItems(FXCollections.observableArrayList());
            saleInfoLabel.setText("");
            showError("No sale found for invoice \"" + invoiceNo + "\".");
        });
    }

    @FXML
    private void handleProcessReturn() {
        try {
            SaleItem selected = itemsTable.getSelectionModel().getSelectedItem();
            if (selected == null) {
                throw new BusinessException("Select a line item from the invoice to return.");
            }
            ReturnReason reason = reasonCombo.getValue();
            if (reason == null) {
                throw new BusinessException("Select a return reason.");
            }
            int qty = Integer.parseInt(requireText(returnQtyField, "Return quantity"));

            returnService.processSalesReturn(selected.getId(), qty, reason, notesField.getText());

            Alert alert = new Alert(Alert.AlertType.INFORMATION, "Return processed successfully.");
            alert.showAndWait();

            returnQtyField.clear();
            notesField.clear();
            handleSearchInvoice();
            loadRecentReturns();
            hideError();
        } catch (BusinessException e) {
            showError(e.getMessage());
        } catch (NumberFormatException e) {
            showError("Enter a valid return quantity.");
        }
    }

    private void loadRecentReturns() {
        recentReturnsTable.setItems(FXCollections.observableArrayList(salesReturnDao.findRecent(50)));
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
