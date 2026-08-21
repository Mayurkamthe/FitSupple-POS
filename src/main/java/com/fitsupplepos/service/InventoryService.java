package com.fitsupplepos.service;

import com.fitsupplepos.config.SessionManager;
import com.fitsupplepos.exception.BusinessException;
import com.fitsupplepos.model.InventoryTransaction;
import com.fitsupplepos.model.Product;
import com.fitsupplepos.model.ProductBatch;
import com.fitsupplepos.model.enums.TransactionType;
import org.hibernate.Session;

/**
 * Writes InventoryTransaction audit rows. Every method here MUST be called from
 * inside an already-open Hibernate transaction (i.e. from within a
 * SessionManager.withTransaction block owned by the caller — Sale, Purchase,
 * Return, or Adjustment services) so the stock change and its audit trail are
 * always committed or rolled back together.
 */
public class InventoryService {

    public void recordTransaction(Session session, TransactionType type, Product product, ProductBatch batch,
                                   int quantity, int previousStock, int newStock,
                                   String referenceInvoice, String reason) {
        InventoryTransaction txn = new InventoryTransaction();
        txn.setTransactionType(type);
        txn.setProduct(product);
        txn.setBatch(batch);
        txn.setQuantity(quantity);
        txn.setPreviousStock(previousStock);
        txn.setNewStock(newStock);
        txn.setReferenceInvoice(referenceInvoice);
        txn.setReason(reason);
        session.persist(txn);
    }

    /** Increases a batch's available quantity (purchase, sales return, adjustment-in) and logs it. */
    public void increaseStock(Session session, ProductBatch batch, int quantity, TransactionType type,
                               String referenceInvoice, String reason) {
        int previous = batch.getQuantityAvailable();
        batch.setQuantityAvailable(previous + quantity);
        session.merge(batch);
        recordTransaction(session, type, batch.getProduct(), batch, quantity, previous, batch.getQuantityAvailable(),
                referenceInvoice, reason);
    }

    /** Decreases a batch's available quantity (sale, purchase return, damage, expired) and logs it. */
    public void decreaseStock(Session session, ProductBatch batch, int quantity, TransactionType type,
                               String referenceInvoice, String reason) {
        int previous = batch.getQuantityAvailable();
        int updated = previous - quantity;
        if (updated < 0) {
            throw new com.fitsupplepos.exception.InsufficientStockException(
                    batch.getProduct().getProductName(), quantity, previous);
        }
        batch.setQuantityAvailable(updated);
        session.merge(batch);
        recordTransaction(session, type, batch.getProduct(), batch, quantity, previous, updated,
                referenceInvoice, reason);
    }

    /**
     * Standalone manual stock correction, used by the Inventory screen — opens and owns its
     * own transaction (unlike increaseStock/decreaseStock above, which expect a session that's
     * already inside a caller-owned transaction such as a Purchase or Sale).
     *
     * @param deltaQuantity positive to add stock (ADJUSTMENT only), negative to remove
     *                      (ADJUSTMENT, DAMAGE, or EXPIRED write-off).
     */
    public void manualAdjust(Long batchId, int deltaQuantity, TransactionType type, String reason) {
        if (type != TransactionType.ADJUSTMENT && type != TransactionType.DAMAGE && type != TransactionType.EXPIRED) {
            throw new BusinessException("Unsupported adjustment type: " + type);
        }
        if (deltaQuantity == 0) {
            throw new BusinessException("Adjustment quantity cannot be zero.");
        }
        if (deltaQuantity < 0 && type == TransactionType.ADJUSTMENT) {
            // negative ADJUSTMENT is allowed (stock correction downward), no special handling needed
        }
        if (deltaQuantity > 0 && type != TransactionType.ADJUSTMENT) {
            throw new BusinessException("Only ADJUSTMENT can increase stock; DAMAGE and EXPIRED always reduce stock.");
        }

        SessionManager.withTransactionVoid(session -> {
            ProductBatch batch = session.get(ProductBatch.class, batchId);
            if (batch == null) {
                throw new BusinessException("Batch not found.");
            }
            if (deltaQuantity > 0) {
                increaseStock(session, batch, deltaQuantity, type, null, reason);
            } else {
                decreaseStock(session, batch, Math.abs(deltaQuantity), type, null, reason);
            }
        });
    }
}
