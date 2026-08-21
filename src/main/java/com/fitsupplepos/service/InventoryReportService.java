package com.fitsupplepos.service;

import com.fitsupplepos.config.SessionManager;
import com.fitsupplepos.dao.ProductBatchDao;
import com.fitsupplepos.model.ProductBatch;

import java.math.BigDecimal;
import java.util.List;

/**
 * Aggregated, read-only stock views for the Inventory module: all stock (FEFO order),
 * low stock, out of stock, expiring-in-30/60-days, expired, and total valuation.
 * Nothing here mutates data — see InventoryService.manualAdjust for write operations.
 */
public class InventoryReportService {

    private final ProductBatchDao batchDao = new ProductBatchDao();

    public List<ProductBatch> findAllInStock() {
        return SessionManager.withSession(session -> session.createQuery(
                "from ProductBatch b where b.quantityAvailable > 0 " +
                        "order by b.product.productName, case when b.expiryDate is null then 1 else 0 end, b.expiryDate",
                ProductBatch.class).list());
    }

    /** Low stock = a product's total available quantity across all batches is at/under its minimumStock. */
    public List<ProductBatch> findLowStock() {
        return SessionManager.withSession(session -> session.createQuery(
                "select b from ProductBatch b where b.quantityAvailable > 0 and b.product.id in (" +
                        "  select p.id from Product p where p.active = true and (" +
                        "    select coalesce(sum(b2.quantityAvailable), 0) from ProductBatch b2 where b2.product = p" +
                        "  ) <= p.minimumStock" +
                        ") order by b.product.productName",
                ProductBatch.class).list());
    }

    public List<ProductBatch> findOutOfStock() {
        return batchDao.findOutOfStock();
    }

    public List<ProductBatch> findExpiringWithin(int days) {
        return batchDao.findExpiringWithin(days);
    }

    public List<ProductBatch> findExpired() {
        return batchDao.findExpired();
    }

    public BigDecimal totalValuationAtCost() {
        return SessionManager.withSession(session -> session.createQuery(
                "select coalesce(sum(b.quantityAvailable * b.purchasePrice), 0) from ProductBatch b where b.quantityAvailable > 0",
                BigDecimal.class).getSingleResult());
    }

    public BigDecimal totalValuationAtMrp() {
        return SessionManager.withSession(session -> session.createQuery(
                "select coalesce(sum(b.quantityAvailable * b.mrp), 0) from ProductBatch b where b.quantityAvailable > 0",
                BigDecimal.class).getSingleResult());
    }

    public long totalUnitsInStock() {
        return SessionManager.withSession(session -> session.createQuery(
                "select coalesce(sum(b.quantityAvailable), 0) from ProductBatch b", Long.class).getSingleResult());
    }
}
