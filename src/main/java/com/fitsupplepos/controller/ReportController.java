package com.fitsupplepos.controller;

import com.fitsupplepos.model.InventoryTransaction;
import com.fitsupplepos.service.InventoryReportService;
import com.fitsupplepos.service.ReportService;
import com.fitsupplepos.util.CsvExportUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ReportController {

    private static final Logger log = LoggerFactory.getLogger(ReportController.class);
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm");

    @FXML private DatePicker fromDatePicker;
    @FXML private DatePicker toDatePicker;

    @FXML private ComboBox<String> salesGroupCombo;
    @FXML private Label totalSalesLabel;
    @FXML private TableView<ReportService.NameValueRow> salesTable;

    @FXML private Label stockCostLabel;
    @FXML private Label stockMrpLabel;
    @FXML private TableView<InventoryTransaction> stockMovementTable;

    @FXML private ComboBox<String> purchaseGroupCombo;
    @FXML private TableView<ReportService.NameValueRow> purchaseTable;

    @FXML private Label gstTaxableLabel;
    @FXML private Label gstCgstLabel;
    @FXML private Label gstSgstLabel;
    @FXML private Label gstIgstLabel;
    @FXML private TableView<Object[]> gstByRateTable;

    @FXML private Label revenueLabel;
    @FXML private Label cogsLabel;
    @FXML private Label discountsLabel;
    @FXML private Label expensesLabel;
    @FXML private Label grossProfitLabel;
    @FXML private Label netProfitLabel;

    private final ReportService reportService = new ReportService();
    private final InventoryReportService inventoryReportService = new InventoryReportService();

    @FXML
    public void initialize() {
        fromDatePicker.setValue(LocalDate.now().withDayOfMonth(1));
        toDatePicker.setValue(LocalDate.now());

        salesGroupCombo.setItems(FXCollections.observableArrayList("Product-wise", "Category-wise", "Customer-wise"));
        salesGroupCombo.setValue("Product-wise");
        salesGroupCombo.valueProperty().addListener((obs, o, n) -> refreshSales());

        purchaseGroupCombo.setItems(FXCollections.observableArrayList("Supplier-wise", "Product-wise"));
        purchaseGroupCombo.setValue("Supplier-wise");
        purchaseGroupCombo.valueProperty().addListener((obs, o, n) -> refreshPurchases());

        buildNameValueColumns(salesTable, "Name", "Qty Sold");
        buildNameValueColumns(purchaseTable, "Name", "Qty Purchased");
        buildStockMovementColumns();
        buildGstByRateColumns();

        refreshAll();
    }

    @FXML
    private void handleApply() {
        refreshAll();
    }

    private void refreshAll() {
        if (fromDatePicker.getValue() == null || toDatePicker.getValue() == null) {
            return;
        }
        refreshSales();
        refreshInventory();
        refreshPurchases();
        refreshGst();
        refreshProfit();
    }

    private LocalDate from() { return fromDatePicker.getValue(); }
    private LocalDate to() { return toDatePicker.getValue(); }

    // ---------------------------------------------------------------- Sales ----

    private void refreshSales() {
        List<ReportService.NameValueRow> rows = switch (salesGroupCombo.getValue()) {
            case "Category-wise" -> reportService.salesByCategory(from(), to());
            case "Customer-wise" -> reportService.salesByCustomer(from(), to());
            default -> reportService.salesByProduct(from(), to());
        };
        salesTable.setItems(FXCollections.observableArrayList(rows));
        BigDecimal total = reportService.totalSales(from(), to());
        totalSalesLabel.setText("Total Sales: ₹" + total.setScale(2, RoundingMode.HALF_UP));
    }

    @FXML
    private void handleExportSales() {
        exportCsv(salesTable, "sales_report");
    }

    // ------------------------------------------------------------ Inventory ----

    private void refreshInventory() {
        BigDecimal cost = inventoryReportService.totalValuationAtCost().setScale(2, RoundingMode.HALF_UP);
        BigDecimal mrp = inventoryReportService.totalValuationAtMrp().setScale(2, RoundingMode.HALF_UP);
        stockCostLabel.setText("Stock Value (Cost): ₹" + cost);
        stockMrpLabel.setText("Stock Value (MRP): ₹" + mrp);
        stockMovementTable.setItems(FXCollections.observableArrayList(reportService.stockMovement(from(), to())));
    }

    @FXML
    private void handleExportInventory() {
        exportCsv(stockMovementTable, "stock_movement_report");
    }

    // ---------------------------------------------------------- Purchases ----

    private void refreshPurchases() {
        List<ReportService.NameValueRow> rows = "Product-wise".equals(purchaseGroupCombo.getValue())
                ? reportService.purchasesByProduct(from(), to())
                : reportService.purchasesBySupplier(from(), to());
        purchaseTable.setItems(FXCollections.observableArrayList(rows));
    }

    @FXML
    private void handleExportPurchases() {
        exportCsv(purchaseTable, "purchases_report");
    }

    // ----------------------------------------------------------------- GST ----

    private void refreshGst() {
        ReportService.GstSummaryRow summary = reportService.gstSummary(from(), to());
        gstTaxableLabel.setText("Taxable: ₹" + summary.taxable().setScale(2, RoundingMode.HALF_UP));
        gstCgstLabel.setText("CGST: ₹" + summary.cgst().setScale(2, RoundingMode.HALF_UP));
        gstSgstLabel.setText("SGST: ₹" + summary.sgst().setScale(2, RoundingMode.HALF_UP));
        gstIgstLabel.setText("IGST: ₹" + summary.igst().setScale(2, RoundingMode.HALF_UP));
        gstByRateTable.setItems(FXCollections.observableArrayList(reportService.gstByRate(from(), to())));
    }

    @FXML
    private void handleExportGst() {
        exportCsv(gstByRateTable, "gst_report");
    }

    // -------------------------------------------------------------- Profit ----

    private void refreshProfit() {
        ReportService.ProfitSummary summary = reportService.profitSummary(from(), to());
        revenueLabel.setText("Revenue: ₹" + summary.revenue().setScale(2, RoundingMode.HALF_UP));
        cogsLabel.setText("Cost of Goods Sold: ₹" + summary.costOfGoodsSold().setScale(2, RoundingMode.HALF_UP));
        discountsLabel.setText("Discounts Given: ₹" + summary.discounts().setScale(2, RoundingMode.HALF_UP));
        expensesLabel.setText("Expenses: ₹" + summary.expenses().setScale(2, RoundingMode.HALF_UP));
        grossProfitLabel.setText("Gross Profit: ₹" + summary.grossProfit().setScale(2, RoundingMode.HALF_UP));
        netProfitLabel.setText("Net Profit: ₹" + summary.netProfit().setScale(2, RoundingMode.HALF_UP));
    }

    @FXML
    private void handleExportProfitPdf() {
        FileChooser chooser = new FileChooser();
        chooser.setInitialFileName("profit_report_" + from() + "_to_" + to() + ".pdf");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
        File file = chooser.showSaveDialog(netProfitLabel.getScene().getWindow());
        if (file == null) return;

        try {
            com.fitsupplepos.service.PdfReportService.writeProfitSummaryPdf(
                    file, from(), to(), reportService.profitSummary(from(), to()));
            new Alert(Alert.AlertType.INFORMATION, "Profit summary exported to " + file.getName()).showAndWait();
        } catch (IOException e) {
            log.error("Failed to export profit PDF", e);
            new Alert(Alert.AlertType.ERROR, "Could not export PDF: " + e.getMessage()).showAndWait();
        }
    }

    // -------------------------------------------------------------- helpers ----

    private void buildNameValueColumns(TableView<ReportService.NameValueRow> table, String nameHeader, String qtyHeader) {
        table.getColumns().clear();
        TableColumn<ReportService.NameValueRow, String> nameCol = new TableColumn<>(nameHeader);
        nameCol.setPrefWidth(260);
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name()));

        TableColumn<ReportService.NameValueRow, String> qtyCol = new TableColumn<>(qtyHeader);
        qtyCol.setPrefWidth(120);
        qtyCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().quantity())));

        TableColumn<ReportService.NameValueRow, String> revenueCol = new TableColumn<>("Amount");
        revenueCol.setPrefWidth(140);
        revenueCol.setCellValueFactory(c -> new SimpleStringProperty("₹" + c.getValue().revenue().setScale(2, RoundingMode.HALF_UP)));

        table.getColumns().addAll(nameCol, qtyCol, revenueCol);
    }

    private void buildStockMovementColumns() {
        stockMovementTable.getColumns().clear();

        TableColumn<InventoryTransaction, String> dateCol = new TableColumn<>("Date/Time");
        dateCol.setPrefWidth(150);
        dateCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCreatedAt().format(DATE_TIME_FMT)));

        TableColumn<InventoryTransaction, String> typeCol = new TableColumn<>("Type");
        typeCol.setPrefWidth(110);
        typeCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTransactionType().name()));

        TableColumn<InventoryTransaction, String> productCol = new TableColumn<>("Product");
        productCol.setPrefWidth(200);
        productCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getProduct().getProductName()));

        TableColumn<InventoryTransaction, String> qtyCol = new TableColumn<>("Qty");
        qtyCol.setPrefWidth(70);
        qtyCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getQuantity())));

        TableColumn<InventoryTransaction, String> prevCol = new TableColumn<>("Prev Stock");
        prevCol.setPrefWidth(90);
        prevCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getPreviousStock())));

        TableColumn<InventoryTransaction, String> newCol = new TableColumn<>("New Stock");
        newCol.setPrefWidth(90);
        newCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getNewStock())));

        TableColumn<InventoryTransaction, String> refCol = new TableColumn<>("Reference");
        refCol.setPrefWidth(120);
        refCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getReferenceInvoice() == null ? "" : c.getValue().getReferenceInvoice()));

        TableColumn<InventoryTransaction, String> reasonCol = new TableColumn<>("Reason");
        reasonCol.setPrefWidth(220);
        reasonCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getReason() == null ? "" : c.getValue().getReason()));

        stockMovementTable.getColumns().addAll(dateCol, typeCol, productCol, qtyCol, prevCol, newCol, refCol, reasonCol);
    }

    private void buildGstByRateColumns() {
        gstByRateTable.getColumns().clear();

        TableColumn<Object[], String> rateCol = new TableColumn<>("GST Rate");
        rateCol.setPrefWidth(100);
        rateCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue()[0] + "%"));

        TableColumn<Object[], String> taxableCol = new TableColumn<>("Taxable Value");
        taxableCol.setPrefWidth(140);
        taxableCol.setCellValueFactory(c -> new SimpleStringProperty("₹" + ((BigDecimal) c.getValue()[1]).setScale(2, RoundingMode.HALF_UP)));

        TableColumn<Object[], String> cgstCol = new TableColumn<>("CGST");
        cgstCol.setPrefWidth(110);
        cgstCol.setCellValueFactory(c -> new SimpleStringProperty("₹" + ((BigDecimal) c.getValue()[2]).setScale(2, RoundingMode.HALF_UP)));

        TableColumn<Object[], String> sgstCol = new TableColumn<>("SGST");
        sgstCol.setPrefWidth(110);
        sgstCol.setCellValueFactory(c -> new SimpleStringProperty("₹" + ((BigDecimal) c.getValue()[3]).setScale(2, RoundingMode.HALF_UP)));

        TableColumn<Object[], String> igstCol = new TableColumn<>("IGST");
        igstCol.setPrefWidth(110);
        igstCol.setCellValueFactory(c -> new SimpleStringProperty("₹" + ((BigDecimal) c.getValue()[4]).setScale(2, RoundingMode.HALF_UP)));

        gstByRateTable.getColumns().addAll(rateCol, taxableCol, cgstCol, sgstCol, igstCol);
    }

    private void exportCsv(TableView<?> table, String baseName) {
        FileChooser chooser = new FileChooser();
        chooser.setInitialFileName(baseName + "_" + from() + "_to_" + to() + ".csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        File file = chooser.showSaveDialog(table.getScene().getWindow());
        if (file == null) return;

        try {
            CsvExportUtil.exportToCsv(table, file);
            new Alert(Alert.AlertType.INFORMATION, "Exported to " + file.getName() + "\n(CSV files open directly in Excel.)").showAndWait();
        } catch (IOException e) {
            log.error("Failed to export CSV", e);
            new Alert(Alert.AlertType.ERROR, "Could not export CSV: " + e.getMessage()).showAndWait();
        }
    }
}
