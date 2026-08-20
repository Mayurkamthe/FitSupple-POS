package com.fitsupplepos.service;

import com.fitsupplepos.config.SessionManager;
import com.fitsupplepos.model.ProductBatch;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Aggregates real-time dashboard figures directly from the database via Hibernate/HQL.
 * No mock or placeholder numbers — every figure here reflects actual rows in SQLite.
 */
public class DashboardService {

    public static class DashboardStats {
        public BigDecimal todaySales = BigDecimal.ZERO;
        public BigDecimal todayPurchases = BigDecimal.ZERO;
        public BigDecimal todayExpenses = BigDecimal.ZERO;
        public BigDecimal grossProfit = BigDecimal.ZERO;
        public BigDecimal netProfit = BigDecimal.ZERO;
        public long totalCustomers;
        public long totalProducts;
        public long lowStockCount;
        public long expiringCount;
        public long todayInvoices;
    }

    public DashboardStats getTodayStats() {
        DashboardStats stats = new DashboardStats();
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);

        SessionManager.withSessionVoid(session -> {
            BigDecimal sales = session.createQuery(
                            "select coalesce(sum(s.grandTotal), 0) from Sale s where s.saleDate >= :start and s.saleDate < :end",
                            BigDecimal.class)
                    .setParameter("start", startOfDay).setParameter("end", endOfDay)
                    .getSingleResult();
            stats.todaySales = sales;

            Long invoiceCount = session.createQuery(
                            "select count(s) from Sale s where s.saleDate >= :start and s.saleDate < :end", Long.class)
                    .setParameter("start", startOfDay).setParameter("end", endOfDay)
                    .getSingleResult();
            stats.todayInvoices = invoiceCount;

            BigDecimal purchases = session.createQuery(
                            "select coalesce(sum(p.grandTotal), 0) from Purchase p where p.purchaseDate >= :start and p.purchaseDate < :end",
                            BigDecimal.class)
                    .setParameter("start", startOfDay).setParameter("end", endOfDay)
                    .getSingleResult();
            stats.todayPurchases = purchases;

            BigDecimal expenses = session.createQuery(
                            "select coalesce(sum(e.amount), 0) from Expense e where e.expenseDate = :today",
                            BigDecimal.class)
                    .setParameter("today", LocalDate.now())
                    .getSingleResult();
            stats.todayExpenses = expenses;

            // Gross profit (today) = sum(sale item taxable value) - sum(qty * batch purchase price)
            BigDecimal costOfGoodsSold = session.createQuery(
                            "select coalesce(sum(si.quantity * si.batch.purchasePrice), 0) " +
                                    "from SaleItem si where si.sale.saleDate >= :start and si.sale.saleDate < :end",
                            BigDecimal.class)
                    .setParameter("start", startOfDay).setParameter("end", endOfDay)
                    .getSingleResult();
            stats.grossProfit = stats.todaySales.subtract(costOfGoodsSold);
            stats.netProfit = stats.grossProfit.subtract(stats.todayExpenses);

            stats.totalCustomers = session.createQuery("select count(c) from Customer c", Long.class).getSingleResult();
            stats.totalProducts = session.createQuery("select count(p) from Product p where p.active = true", Long.class).getSingleResult();

            // Low stock: sum of available batch qty per product below its minimumStock threshold.
            List<Object[]> stockRows = session.createQuery(
                    "select p.id, p.minimumStock, coalesce(sum(b.quantityAvailable), 0) " +
                            "from Product p left join ProductBatch b on b.product = p " +
                            "where p.active = true group by p.id, p.minimumStock", Object[].class).list();
            long lowStock = stockRows.stream()
                    .filter(row -> ((Number) row[2]).intValue() <= ((Number) row[1]).intValue())
                    .count();
            stats.lowStockCount = lowStock;

            LocalDate expiryCutoff = LocalDate.now().plusDays(30);
            Long expiring = session.createQuery(
                            "select count(b) from ProductBatch b where b.expiryDate is not null " +
                                    "and b.expiryDate >= :today and b.expiryDate <= :cutoff and b.quantityAvailable > 0",
                            Long.class)
                    .setParameter("today", LocalDate.now())
                    .setParameter("cutoff", expiryCutoff)
                    .getSingleResult();
            stats.expiringCount = expiring;
        });

        return stats;
    }
}
