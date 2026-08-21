package com.fitsupplepos.service;

import com.fitsupplepos.config.SessionManager;
import com.fitsupplepos.dao.PurchaseReturnDao;
import com.fitsupplepos.dao.SalesReturnDao;
import com.fitsupplepos.exception.BusinessException;
import com.fitsupplepos.exception.InvalidReturnException;
import com.fitsupplepos.model.*;
import com.fitsupplepos.model.enums.ReturnReason;
import com.fitsupplepos.model.enums.TransactionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Sales Returns: Search invoice -> select item -> return qty (capped at qty sold minus
 * already returned) -> refund -> inventory increase, all inside one transaction.
 *
 * Purchase Returns: select purchase item -> return qty (capped at qty received minus
 * already returned) -> inventory decrease, all inside one transaction.
 */
public class ReturnService {

    private static final Logger log = LoggerFactory.getLogger(ReturnService.class);

    private final SalesReturnDao salesReturnDao = new SalesReturnDao();
    private final PurchaseReturnDao purchaseReturnDao = new PurchaseReturnDao();
    private final InventoryService inventoryService = new InventoryService();

    public SalesReturn processSalesReturn(Long saleItemId, int returnQuantity, ReturnReason reason, String notes) {
        if (returnQuantity <= 0) {
            throw new InvalidReturnException("Return quantity must be greater than zero.");
        }

        return SessionManager.withTransaction(session -> {
            SaleItem saleItem = session.get(SaleItem.class, saleItemId);
            if (saleItem == null) {
                throw new BusinessException("Sale item not found.");
            }

            int alreadyReturned = salesReturnDao.sumReturnedForSaleItem(session, saleItemId);
            int maxReturnable = saleItem.getQuantity() - alreadyReturned;
            if (returnQuantity > maxReturnable) {
                throw new InvalidReturnException("Cannot return " + returnQuantity + " units — only "
                        + maxReturnable + " of the " + saleItem.getQuantity() + " sold unit(s) remain returnable.");
            }

            BigDecimal perUnitRefund = saleItem.getLineTotal()
                    .divide(BigDecimal.valueOf(saleItem.getQuantity()), 4, RoundingMode.HALF_UP);
            BigDecimal refundAmount = perUnitRefund.multiply(BigDecimal.valueOf(returnQuantity))
                    .setScale(2, RoundingMode.HALF_UP);

            SalesReturn salesReturn = new SalesReturn();
            salesReturn.setSale(saleItem.getSale());
            salesReturn.setSaleItem(saleItem);
            salesReturn.setReturnQuantity(returnQuantity);
            salesReturn.setReturnReason(reason);
            salesReturn.setRefundAmount(refundAmount);
            salesReturn.setNotes(notes);
            session.persist(salesReturn);

            // Damaged/expired returns are written off rather than put back on the shelf.
            if (reason != ReturnReason.DAMAGED && reason != ReturnReason.EXPIRED) {
                inventoryService.increaseStock(session, saleItem.getBatch(), returnQuantity,
                        TransactionType.SALES_RETURN, saleItem.getSale().getInvoiceNumber(),
                        "Sales return: " + reason);
            } else {
                inventoryService.recordTransaction(session, TransactionType.DAMAGE, saleItem.getProduct(),
                        saleItem.getBatch(), returnQuantity, saleItem.getBatch().getQuantityAvailable(),
                        saleItem.getBatch().getQuantityAvailable(),
                        saleItem.getSale().getInvoiceNumber(), "Returned as " + reason + " — written off, not restocked");
            }

            log.info("Processed sales return of {} unit(s) for sale item {} (reason: {})", returnQuantity, saleItemId, reason);
            return salesReturn;
        });
    }

    public PurchaseReturn processPurchaseReturn(Long purchaseItemId, int returnQuantity, String reason) {
        if (returnQuantity <= 0) {
            throw new InvalidReturnException("Return quantity must be greater than zero.");
        }

        return SessionManager.withTransaction(session -> {
            PurchaseItem purchaseItem = session.get(PurchaseItem.class, purchaseItemId);
            if (purchaseItem == null) {
                throw new BusinessException("Purchase item not found.");
            }

            int alreadyReturned = purchaseReturnDao.sumReturnedForPurchaseItem(session, purchaseItemId);
            int maxReturnable = purchaseItem.getQuantity() - alreadyReturned;
            if (returnQuantity > maxReturnable) {
                throw new InvalidReturnException("Cannot return " + returnQuantity + " units — only "
                        + maxReturnable + " of the " + purchaseItem.getQuantity() + " received unit(s) remain returnable.");
            }

            BigDecimal perUnitRefund = purchaseItem.getLineTotal()
                    .divide(BigDecimal.valueOf(purchaseItem.getQuantity()), 4, RoundingMode.HALF_UP);
            BigDecimal refundAmount = perUnitRefund.multiply(BigDecimal.valueOf(returnQuantity))
                    .setScale(2, RoundingMode.HALF_UP);

            PurchaseReturn purchaseReturn = new PurchaseReturn();
            purchaseReturn.setPurchase(purchaseItem.getPurchase());
            purchaseReturn.setPurchaseItem(purchaseItem);
            purchaseReturn.setReturnQuantity(returnQuantity);
            purchaseReturn.setReason(reason);
            purchaseReturn.setRefundAmount(refundAmount);
            session.persist(purchaseReturn);

            inventoryService.decreaseStock(session, purchaseItem.getBatch(), returnQuantity,
                    TransactionType.PURCHASE_RETURN, purchaseItem.getPurchase().getInvoiceNumber(),
                    "Purchase return: " + reason);

            log.info("Processed purchase return of {} unit(s) for purchase item {}", returnQuantity, purchaseItemId);
            return purchaseReturn;
        });
    }
}
