package com.fitsupplepos.controller;

import com.fitsupplepos.service.DashboardService;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.time.LocalDate;

public class DashboardController {

    @FXML private Label dateLabel;
    @FXML private Label todaySalesLabel;
    @FXML private Label todayPurchasesLabel;
    @FXML private Label todayExpensesLabel;
    @FXML private Label grossProfitLabel;
    @FXML private Label netProfitLabel;
    @FXML private Label totalCustomersLabel;
    @FXML private Label totalProductsLabel;
    @FXML private Label lowStockLabel;
    @FXML private Label expiringLabel;
    @FXML private Label todayInvoicesLabel;

    private final DashboardService dashboardService = new DashboardService();

    @FXML
    public void initialize() {
        dateLabel.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy")));
        refresh();
    }

    public void refresh() {
        DashboardService.DashboardStats stats = dashboardService.getTodayStats();
        todaySalesLabel.setText(money(stats.todaySales));
        todayPurchasesLabel.setText(money(stats.todayPurchases));
        todayExpensesLabel.setText(money(stats.todayExpenses));
        grossProfitLabel.setText(money(stats.grossProfit));
        netProfitLabel.setText(money(stats.netProfit));
        totalCustomersLabel.setText(String.valueOf(stats.totalCustomers));
        totalProductsLabel.setText(String.valueOf(stats.totalProducts));
        lowStockLabel.setText(String.valueOf(stats.lowStockCount));
        expiringLabel.setText(String.valueOf(stats.expiringCount));
        todayInvoicesLabel.setText(String.valueOf(stats.todayInvoices));
    }

    private String money(java.math.BigDecimal value) {
        return "\u20B9" + value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
