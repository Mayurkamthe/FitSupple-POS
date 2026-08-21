package com.fitsupplepos.controller;

import com.fitsupplepos.config.SessionManager;
import com.fitsupplepos.dao.PurchaseDao;
import com.fitsupplepos.dao.PurchaseItemDao;
import com.fitsupplepos.dao.PurchaseReturnDao;
import com.fitsupplepos.exception.BusinessException;
import com.fitsupplepos.model.Purchase;
import com.fitsupplepos.model.PurchaseItem;
import com.fitsupplepos.model.PurchaseReturn;
import com.fitsupplepos.service.ReturnService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class PurchaseReturnController {

    @FXML private TextField invoiceField;
    @FXML private Label purchaseInfoLabel;
    @FXML private Label errorLabel;

    @FXML private TableView<PurchaseItem> itemsTable;
    @FXML private TableColumn<PurchaseItem, String> itemProductCol;
    @FXML private TableColumn<PurchaseItem, String> itemBatchCol;
    @FXML private TableColumn<PurchaseItem, String> itemQtyCol;
    @FXML private TableColumn<PurchaseItem, String> itemReturnedCol;
    @FXML private TableColumn<PurchaseItem, String> itemReturnableCol;
    @FXML private TableColumn<PurchaseItem, String> itemTotalCol;

    @FXML private TextField returnQtyField;
    @FXML private TextField reasonField;

    @FXML private TableView<PurchaseReturn> recentReturnsTable;
    @FXML private TableColumn<PurchaseReturn, String> recentDateCol;
    @FXML private TableColumn<PurchaseReturn, String> recentInvoiceCol;
    @FXML private TableColumn<PurchaseReturn, String> recentProductCol;
    @FXML private TableColumn<PurchaseReturn, String> recentQtyCol;
    @FXML private TableColumn<PurchaseReturn, String> recentReasonCol;
    @FXML private TableColumn<PurchaseReturn, String> recentRefundCol;

    private final PurchaseDao purchaseDao = new PurchaseDao();
    private final PurchaseItemDao purchaseItemDao = new PurchaseItemDao();
    private final PurchaseReturnDao purchaseReturnDao = new PurchaseReturnDao();
    private final ReturnService returnService = new ReturnService();

    private Purchase currentPurchase;

    @FXML
    public void initialize() {
        itemProductCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getProduct().getProductName()));
        itemBatchCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getBatch() != null ? c.getValue().getBatch().getBatchNumber() : ""));
        itemQtyCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getQuantity())));
        itemReturnedCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(alreadyReturned(c.getValue()))));
        itemReturnableCol.setCellValueFactory(c -> new SimpleStringProperty(
                String.valueOf(c.getValue().getQuantity() - alreadyReturned(c.getValue()))));
        itemTotalCol.setCellValueFactory(c -> new SimpleStringProperty("₹" + c.getValue().getLineTotal().setScale(2, RoundingMode.HALF_UP)));

        recentDateCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getReturnedAt().format(DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm"))));
        recentInvoiceCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getPurchase().getInvoiceNumber()));
        recentProductCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getPurchaseItem().getProduct().getProductName()));
        recentQtyCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getReturnQuantity())));
        recentReasonCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getReason() == null ? "" : c.getValue().getReason()));
        recentRefundCol.setCellValueFactory(c -> new SimpleStringProperty("₹" + c.getValue().getRefundAmount().setScale(2, RoundingMode.HALF_UP)));

        loadRecentReturns();
    }

    private int alreadyReturned(PurchaseItem item) {
        return SessionManager.withSession(session -> purchaseReturnDao.sumReturnedForPurchaseItem(session, item.getId()));
    }

    @FXML
    private void handleSearchInvoice() {
        String invoiceNo = invoiceField.getText();
        if (invoiceNo == null || invoiceNo.isBlank()) {
            showError("Enter a purchase invoice number to search.");
            return;
        }
        List<Purchase> matches = purchaseDao.findAllOrderedByDateDesc().stream()
                .filter(p -> p.getInvoiceNumber().equalsIgnoreCase(invoiceNo.trim()))
                .toList();
        if (matches.isEmpty()) {
            currentPurchase = null;
            itemsTable.setItems(FXCollections.observableArrayList());
            purchaseInfoLabel.setText("");
            showError("No purchase found for invoice \"" + invoiceNo + "\".");
            return;
        }
        currentPurchase = matches.get(0);
        List<PurchaseItem> items = purchaseItemDao.findForPurchase(currentPurchase.getId());
        itemsTable.setItems(FXCollections.observableArrayList(items));
        purchaseInfoLabel.setText("Invoice " + currentPurchase.getInvoiceNumber() + " — "
                + currentPurchase.getSupplier().getName() + " — Grand Total ₹" + currentPurchase.getGrandTotal());
        hideError();
    }

    @FXML
    private void handleProcessReturn() {
        try {
            PurchaseItem selected = itemsTable.getSelectionModel().getSelectedItem();
            if (selected == null) {
                throw new BusinessException("Select a line item from the purchase to return.");
            }
            String reason = reasonField.getText();
            if (reason == null || reason.isBlank()) {
                throw new BusinessException("Enter a reason for the return.");
            }
            int qty = Integer.parseInt(requireText(returnQtyField, "Return quantity"));

            returnService.processPurchaseReturn(selected.getId(), qty, reason.trim());

            Alert alert = new Alert(Alert.AlertType.INFORMATION, "Purchase return processed successfully.");
            alert.showAndWait();

            returnQtyField.clear();
            reasonField.clear();
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
        recentReturnsTable.setItems(FXCollections.observableArrayList(purchaseReturnDao.findRecent(50)));
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
