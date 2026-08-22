package com.fitsupplepos.service;

import com.fitsupplepos.config.SessionManager;
import com.fitsupplepos.dao.InvoiceSettingDao;
import com.fitsupplepos.exception.BusinessException;
import com.fitsupplepos.model.*;
import com.fitsupplepos.model.enums.TransactionType;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Owns the full purchase-entry transaction:
 *
 *   Purchase -> Purchase Items -> Product Batch (created) -> Inventory Increase -> Inventory Transaction
 *
 * Everything below runs inside a single Hibernate transaction (SessionManager.withTransaction).
 * If any line item fails validation the whole purchase is rolled back — nothing partial is saved.
 */
public class PurchaseService {

    private static final Logger log = LoggerFactory.getLogger(PurchaseService.class);

    private final InvoiceSettingDao invoiceSettingDao = new InvoiceSettingDao();
    private final InventoryService inventoryService = new InventoryService();

    /**
     * @param supplierId   supplier this purchase is from
     * @param items        line items (product, batch number, expiry, qty, purchase price, gst rate, discount)
     * @param overallDiscount discount applied at the purchase level (in addition to any line-level discount)
     */
    public Purchase recordPurchase(Long supplierId, List<PurchaseLineInput> items, BigDecimal overallDiscount) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException("A purchase must have at least one line item.");
        }

        return SessionManager.withTransaction(session -> {
            Supplier supplier = session.get(Supplier.class, supplierId);
            if (supplier == null) {
                throw new BusinessException("Selected supplier was not found.");
            }

            Purchase purchase = new Purchase();
            purchase.setSupplier(supplier);
            purchase.setInvoiceNumber(invoiceSettingDao.nextPurchaseNumber(session));
            purchase.setDiscountAmount(overallDiscount == null ? BigDecimal.ZERO : overallDiscount);

            BigDecimal subtotal = BigDecimal.ZERO;
            BigDecimal totalGst = BigDecimal.ZERO;

            for (PurchaseLineInput line : items) {
                Product product = session.get(Product.class, line.productId);
                if (product == null) {
                    throw new BusinessException("Product not found for one of the purchase lines.");
                }
                if (line.quantity <= 0) {
                    throw new BusinessException("Quantity must be greater than zero for " + product.getProductName() + ".");
                }

                BigDecimal lineBase = line.purchasePrice.multiply(BigDecimal.valueOf(line.quantity));
                BigDecimal lineDiscount = line.discountAmount == null ? BigDecimal.ZERO : line.discountAmount;
                BigDecimal taxable = lineBase.subtract(lineDiscount);
                BigDecimal gstAmount = taxable.multiply(line.gstRate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                BigDecimal lineTotal = taxable.add(gstAmount);

                subtotal = subtotal.add(lineBase);
                totalGst = totalGst.add(gstAmount);

                PurchaseItem item = new PurchaseItem();
                item.setProduct(product);
                item.setBatchNumber(line.batchNumber);
                item.setExpiryDate(line.expiryDate);
                item.setQuantity(line.quantity);
                item.setPurchasePrice(line.purchasePrice);
                item.setGstRate(line.gstRate);
                item.setGstAmount(gstAmount);
                item.setDiscountAmount(lineDiscount);
                item.setLineTotal(lineTotal);
                purchase.addItem(item);

                // Create the batch for this line item.
                ProductBatch batch = new ProductBatch();
                batch.setProduct(product);
                batch.setBatchNumber(line.batchNumber != null && !line.batchNumber.isBlank()
                        ? line.batchNumber : purchase.getInvoiceNumber());
                batch.setManufacturingDate(line.manufacturingDate);
                batch.setExpiryDate(line.expiryDate);
                batch.setPurchasePrice(line.purchasePrice);
                batch.setSellingPrice(line.sellingPrice != null ? line.sellingPrice : product.getSellingPrice());
                batch.setMrp(line.mrp != null ? line.mrp : product.getMrp());
                batch.setQuantityPurchased(line.quantity);
                batch.setQuantityAvailable(0); // increased via inventoryService below so the audit trail is consistent
                batch.setSupplier(supplier);
                session.persist(batch);
                item.setBatch(batch);

                inventoryService.increaseStock(session, batch, line.quantity, TransactionType.PURCHASE,
                        purchase.getInvoiceNumber(), "Purchase from " + supplier.getName());
            }

            BigDecimal grandTotal = subtotal.subtract(purchase.getDiscountAmount()).add(totalGst);
            purchase.setSubtotal(subtotal);
            purchase.setGstAmount(totalGst);
            purchase.setGrandTotal(grandTotal);

            session.persist(purchase);
            com.fitsupplepos.util.AuditLogger.log(session, "PURCHASE_CREATED", "Purchase", purchase.getInvoiceNumber(),
                    "From " + supplier.getName() + ", grand total ₹" + grandTotal + ", " + items.size() + " item(s)");
            log.info("Recorded purchase {} from supplier {} — {} line item(s), total {}",
                    purchase.getInvoiceNumber(), supplier.getName(), items.size(), grandTotal);
            return purchase;
        });
    }

    /** Plain input carrier used by the Purchases UI to build a purchase without exposing entities directly. */
    public static class PurchaseLineInput {
        public Long productId;
        public String batchNumber;
        public java.time.LocalDate manufacturingDate;
        public java.time.LocalDate expiryDate;
        public int quantity;
        public BigDecimal purchasePrice;
        public BigDecimal sellingPrice;
        public BigDecimal mrp;
        public BigDecimal gstRate = BigDecimal.ZERO;
        public BigDecimal discountAmount = BigDecimal.ZERO;
    }
}
