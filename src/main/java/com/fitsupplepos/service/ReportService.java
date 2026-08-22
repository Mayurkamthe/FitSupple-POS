package com.fitsupplepos.service;

import com.fitsupplepos.config.SessionManager;
import com.fitsupplepos.dao.InventoryTransactionDao;
import com.fitsupplepos.model.InventoryTransaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Read-only reporting layer. Every method here is a direct HQL aggregate query against
 * live data — Sales, Purchases, Expenses, InventoryTransaction — nothing is precomputed
 * or cached, so reports always reflect the current state of the database.
 */
public class ReportService {

    private final InventoryTransactionDao inventoryTransactionDao = new InventoryTransactionDao();

    public record NameValueRow(String name, long quantity, BigDecimal revenue) {}

    public record GstSummaryRow(String label, BigDecimal taxable, BigDecimal cgst, BigDecimal sgst, BigDecimal igst) {}

    public record ProfitSummary(BigDecimal revenue, BigDecimal costOfGoodsSold, BigDecimal discounts,
                                 BigDecimal expenses, BigDecimal grossProfit, BigDecimal netProfit) {}

    private LocalDateTime start(LocalDate date) { return date.atStartOfDay(); }
    private LocalDateTime end(LocalDate date) { return date.plusDays(1).atStartOfDay(); }

    // ---------------------------------------------------------------- Sales ----

    public List<NameValueRow> salesByProduct(LocalDate from, LocalDate to) {
        return SessionManager.withSession(session -> session.createQuery(
                "select si.product.productName, sum(si.quantity), sum(si.lineTotal) " +
                        "from SaleItem si where si.sale.saleDate >= :start and si.sale.saleDate < :end " +
                        "group by si.product.productName order by sum(si.lineTotal) desc",
                Object[].class)
                .setParameter("start", start(from)).setParameter("end", end(to))
                .list().stream()
                .map(r -> new NameValueRow((String) r[0], (Long) r[1], (BigDecimal) r[2]))
                .toList());
    }

    public List<NameValueRow> salesByCategory(LocalDate from, LocalDate to) {
        return SessionManager.withSession(session -> session.createQuery(
                "select si.product.category, sum(si.quantity), sum(si.lineTotal) " +
                        "from SaleItem si where si.sale.saleDate >= :start and si.sale.saleDate < :end " +
                        "group by si.product.category order by sum(si.lineTotal) desc",
                Object[].class)
                .setParameter("start", start(from)).setParameter("end", end(to))
                .list().stream()
                .map(r -> new NameValueRow(((com.fitsupplepos.model.enums.ProductCategory) r[0]).getDisplayName(),
                        (Long) r[1], (BigDecimal) r[2]))
                .toList());
    }

    public List<NameValueRow> salesByCustomer(LocalDate from, LocalDate to) {
        return SessionManager.withSession(session -> session.createQuery(
                "select coalesce(c.name, 'Walk-in Customer'), count(s), sum(s.grandTotal) " +
                        "from Sale s left join s.customer c where s.saleDate >= :start and s.saleDate < :end " +
                        "group by c.id, c.name order by sum(s.grandTotal) desc",
                Object[].class)
                .setParameter("start", start(from)).setParameter("end", end(to))
                .list().stream()
                .map(r -> new NameValueRow((String) r[0], (Long) r[1], (BigDecimal) r[2]))
                .toList());
    }

    public BigDecimal totalSales(LocalDate from, LocalDate to) {
        return SessionManager.withSession(session -> session.createQuery(
                "select coalesce(sum(s.grandTotal), 0) from Sale s where s.saleDate >= :start and s.saleDate < :end",
                BigDecimal.class)
                .setParameter("start", start(from)).setParameter("end", end(to))
                .getSingleResult());
    }

    // ------------------------------------------------------------ Purchases ----

    public List<NameValueRow> purchasesBySupplier(LocalDate from, LocalDate to) {
        return SessionManager.withSession(session -> session.createQuery(
                "select p.supplier.name, count(p), sum(p.grandTotal) " +
                        "from Purchase p where p.purchaseDate >= :start and p.purchaseDate < :end " +
                        "group by p.supplier.name order by sum(p.grandTotal) desc",
                Object[].class)
                .setParameter("start", start(from)).setParameter("end", end(to))
                .list().stream()
                .map(r -> new NameValueRow((String) r[0], (Long) r[1], (BigDecimal) r[2]))
                .toList());
    }

    public List<NameValueRow> purchasesByProduct(LocalDate from, LocalDate to) {
        return SessionManager.withSession(session -> session.createQuery(
                "select pi.product.productName, sum(pi.quantity), sum(pi.lineTotal) " +
                        "from PurchaseItem pi where pi.purchase.purchaseDate >= :start and pi.purchase.purchaseDate < :end " +
                        "group by pi.product.productName order by sum(pi.lineTotal) desc",
                Object[].class)
                .setParameter("start", start(from)).setParameter("end", end(to))
                .list().stream()
                .map(r -> new NameValueRow((String) r[0], (Long) r[1], (BigDecimal) r[2]))
                .toList());
    }

    // ------------------------------------------------------------------ GST ----

    public GstSummaryRow gstSummary(LocalDate from, LocalDate to) {
        return SessionManager.withSession(session -> {
            Object[] row = session.createQuery(
                    "select coalesce(sum(s.taxableAmount),0), coalesce(sum(s.cgstAmount),0), " +
                            "coalesce(sum(s.sgstAmount),0), coalesce(sum(s.igstAmount),0) " +
                            "from Sale s where s.saleDate >= :start and s.saleDate < :end and s.billingMode = com.fitsupplepos.model.enums.BillingMode.GST",
                    Object[].class)
                    .setParameter("start", start(from)).setParameter("end", end(to))
                    .getSingleResult();
            return new GstSummaryRow("GST Summary", (BigDecimal) row[0], (BigDecimal) row[1], (BigDecimal) row[2], (BigDecimal) row[3]);
        });
    }

    public List<Object[]> gstByRate(LocalDate from, LocalDate to) {
        return SessionManager.withSession(session -> session.createQuery(
                "select si.gstRate, sum(si.taxableValue), sum(si.cgstAmount), sum(si.sgstAmount), sum(si.igstAmount) " +
                        "from SaleItem si where si.sale.saleDate >= :start and si.sale.saleDate < :end and si.gstRate > 0 " +
                        "group by si.gstRate order by si.gstRate",
                Object[].class)
                .setParameter("start", start(from)).setParameter("end", end(to))
                .list());
    }

    // --------------------------------------------------------------- Profit ----

    public ProfitSummary profitSummary(LocalDate from, LocalDate to) {
        return SessionManager.withSession(session -> {
            BigDecimal revenue = session.createQuery(
                    "select coalesce(sum(s.grandTotal),0) from Sale s where s.saleDate >= :start and s.saleDate < :end",
                    BigDecimal.class)
                    .setParameter("start", start(from)).setParameter("end", end(to)).getSingleResult();

            BigDecimal cogs = session.createQuery(
                    "select coalesce(sum(si.quantity * si.batch.purchasePrice),0) from SaleItem si " +
                            "where si.sale.saleDate >= :start and si.sale.saleDate < :end",
                    BigDecimal.class)
                    .setParameter("start", start(from)).setParameter("end", end(to)).getSingleResult();

            BigDecimal discounts = session.createQuery(
                    "select coalesce(sum(s.discountAmount),0) from Sale s where s.saleDate >= :start and s.saleDate < :end",
                    BigDecimal.class)
                    .setParameter("start", start(from)).setParameter("end", end(to)).getSingleResult();

            BigDecimal expenses = session.createQuery(
                    "select coalesce(sum(e.amount),0) from Expense e where e.expenseDate >= :from and e.expenseDate <= :to",
                    BigDecimal.class)
                    .setParameter("from", from).setParameter("to", to).getSingleResult();

            BigDecimal grossProfit = revenue.subtract(cogs);
            BigDecimal netProfit = grossProfit.subtract(expenses);

            return new ProfitSummary(revenue, cogs, discounts, expenses, grossProfit, netProfit);
        });
    }

    // -------------------------------------------------------- Stock movement ----

    public List<InventoryTransaction> stockMovement(LocalDate from, LocalDate to) {
        return SessionManager.withSession(session -> session.createQuery(
                "from InventoryTransaction where createdAt >= :start and createdAt < :end order by createdAt desc",
                InventoryTransaction.class)
                .setParameter("start", start(from)).setParameter("end", end(to))
                .list());
    }
}
