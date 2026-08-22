package com.fitsupplepos.service;

import com.fitsupplepos.config.SessionManager;
import com.fitsupplepos.dao.InvoiceSettingDao;
import com.fitsupplepos.dao.ProductBatchDao;
import com.fitsupplepos.exception.BusinessException;
import com.fitsupplepos.exception.InsufficientStockException;
import com.fitsupplepos.model.*;
import com.fitsupplepos.model.enums.BillingMode;
import com.fitsupplepos.model.enums.PaymentMethod;
import com.fitsupplepos.model.enums.PaymentStatus;
import com.fitsupplepos.model.enums.TaxType;
import com.fitsupplepos.model.enums.TransactionType;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Owns the full POS billing transaction:
 *
 *   Sale -> Sale Items (FEFO batch issuance) -> Inventory Decrease -> Inventory Transaction
 *        -> Payment -> Invoice
 *
 * Runs entirely inside one Hibernate transaction. Never sells expired stock (the FEFO
 * query already excludes expired batches), and never lets stock go negative — both are
 * enforced before anything is committed, so a failure anywhere rolls back the whole bill.
 */
public class SaleService {

    private static final Logger log = LoggerFactory.getLogger(SaleService.class);

    private final InvoiceSettingDao invoiceSettingDao = new InvoiceSettingDao();
    private final InventoryService inventoryService = new InventoryService();
    private final ProductBatchDao productBatchDao = new ProductBatchDao();

    public static class CartLineInput {
        public Long productId;
        public int quantity;
        public BigDecimal discountAmount = BigDecimal.ZERO;
    }

    public Sale recordSale(Long customerId, List<CartLineInput> cartLines, PaymentMethod method, BigDecimal amountPaid) {
        if (cartLines == null || cartLines.isEmpty()) {
            throw new BusinessException("The cart is empty. Add at least one item before billing.");
        }

        return SessionManager.withTransaction(session -> {
            GstSetting gstSetting = session.get(GstSetting.class, 1L);
            BillingMode billingMode = gstSetting != null ? gstSetting.getBillingMode() : BillingMode.NON_GST;

            Customer customer = null;
            if (customerId != null) {
                customer = session.get(Customer.class, customerId);
            }

            // Inter-state (IGST) applies when the customer's registered state code differs from
            // the shop's own state code (captured in GST Settings). If either side hasn't recorded
            // a state code — e.g. walk-in customers, or the shop hasn't set one up yet — we fall
            // back to intra-state (CGST+SGST), which matches the previous always-intra-state
            // behaviour and is the safer default for a same-city retail shop.
            TaxType taxType = null;
            if (billingMode == BillingMode.GST) {
                String shopState = gstSetting != null ? gstSetting.getStateCode() : null;
                String customerState = customer != null ? customer.getStateCode() : null;
                boolean interState = shopState != null && !shopState.isBlank()
                        && customerState != null && !customerState.isBlank()
                        && !shopState.trim().equalsIgnoreCase(customerState.trim());
                taxType = interState ? TaxType.INTER_STATE : TaxType.INTRA_STATE;
            }

            Sale sale = new Sale();
            sale.setBillingMode(billingMode);
            sale.setTaxType(taxType);
            sale.setInvoiceNumber(invoiceSettingDao.nextInvoiceNumber(session));
            sale.setCustomer(customer);

            BigDecimal subtotal = BigDecimal.ZERO;
            BigDecimal totalDiscount = BigDecimal.ZERO;
            BigDecimal totalTaxable = BigDecimal.ZERO;
            BigDecimal totalCgst = BigDecimal.ZERO;
            BigDecimal totalSgst = BigDecimal.ZERO;
            BigDecimal totalIgst = BigDecimal.ZERO;

            for (CartLineInput line : cartLines) {
                Product product = session.get(Product.class, line.productId);
                if (product == null) {
                    throw new BusinessException("A product in the cart could not be found.");
                }
                if (line.quantity <= 0) {
                    throw new BusinessException("Quantity must be greater than zero for " + product.getProductName() + ".");
                }

                List<ProductBatch> batches = productBatchDao.findSellableBatchesFefo(session, product.getId());
                int remaining = line.quantity;
                BigDecimal discountPerUnit = line.discountAmount == null || line.quantity == 0
                        ? BigDecimal.ZERO
                        : line.discountAmount.divide(BigDecimal.valueOf(line.quantity), 4, RoundingMode.HALF_UP);

                int totalAvailable = batches.stream().mapToInt(ProductBatch::getQuantityAvailable).sum();
                if (totalAvailable < line.quantity) {
                    throw new InsufficientStockException(product.getProductName(), line.quantity, totalAvailable);
                }

                for (ProductBatch batch : batches) {
                    if (remaining <= 0) break;
                    if (batch.isExpired()) continue; // safety net even though FEFO query already excludes expired
                    int take = Math.min(remaining, batch.getQuantityAvailable());
                    if (take <= 0) continue;

                    BigDecimal rate = batch.getSellingPrice() != null ? batch.getSellingPrice() : product.getSellingPrice();
                    BigDecimal gross = rate.multiply(BigDecimal.valueOf(take));
                    BigDecimal subDiscount = discountPerUnit.multiply(BigDecimal.valueOf(take)).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal taxableValue = gross.subtract(subDiscount);

                    BigDecimal gstRate = billingMode == BillingMode.GST
                            ? (product.getGstRate() != null ? product.getGstRate() : BigDecimal.ZERO)
                            : BigDecimal.ZERO;
                    BigDecimal gstAmount = taxableValue.multiply(gstRate)
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

                    BigDecimal cgst = BigDecimal.ZERO;
                    BigDecimal sgst = BigDecimal.ZERO;
                    BigDecimal igst = BigDecimal.ZERO;
                    if (billingMode == BillingMode.GST) {
                        if (taxType == TaxType.INTER_STATE) {
                            igst = gstAmount;
                        } else {
                            cgst = gstAmount.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
                            sgst = gstAmount.subtract(cgst);
                        }
                    }

                    SaleItem item = new SaleItem();
                    item.setProduct(product);
                    item.setBatch(batch);
                    item.setQuantity(take);
                    item.setRate(rate);
                    item.setDiscountAmount(subDiscount);
                    item.setGstRate(gstRate);
                    item.setTaxableValue(taxableValue);
                    item.setCgstAmount(cgst);
                    item.setSgstAmount(sgst);
                    item.setIgstAmount(igst);
                    item.setLineTotal(taxableValue.add(gstAmount));
                    sale.addItem(item);

                    inventoryService.decreaseStock(session, batch, take, TransactionType.SALE,
                            sale.getInvoiceNumber(), "POS Sale");

                    subtotal = subtotal.add(gross);
                    totalDiscount = totalDiscount.add(subDiscount);
                    totalTaxable = totalTaxable.add(taxableValue);
                    totalCgst = totalCgst.add(cgst);
                    totalSgst = totalSgst.add(sgst);
                    totalIgst = totalIgst.add(igst);

                    remaining -= take;
                }

                if (remaining > 0) {
                    // Should not happen given the pre-check above, but guards against a race
                    // between the availability check and consumption within this same transaction.
                    throw new InsufficientStockException(product.getProductName(), line.quantity, line.quantity - remaining);
                }
            }

            BigDecimal rawTotal = totalTaxable.add(totalCgst).add(totalSgst).add(totalIgst);
            BigDecimal roundedTotal = rawTotal.setScale(0, RoundingMode.HALF_UP);
            BigDecimal roundOff = roundedTotal.subtract(rawTotal).setScale(2, RoundingMode.HALF_UP);

            sale.setSubtotal(subtotal);
            sale.setDiscountAmount(totalDiscount);
            sale.setTaxableAmount(totalTaxable);
            sale.setCgstAmount(totalCgst);
            sale.setSgstAmount(totalSgst);
            sale.setIgstAmount(totalIgst);
            sale.setRoundOff(roundOff);
            sale.setGrandTotal(roundedTotal);

            BigDecimal paid = amountPaid == null ? BigDecimal.ZERO : amountPaid;
            if (method == PaymentMethod.CREDIT && paid.signum() == 0) {
                sale.setPaymentStatus(PaymentStatus.UNPAID);
            } else if (paid.compareTo(roundedTotal) >= 0) {
                sale.setPaymentStatus(PaymentStatus.PAID);
            } else if (paid.signum() > 0) {
                sale.setPaymentStatus(PaymentStatus.PARTIAL);
            } else {
                sale.setPaymentStatus(PaymentStatus.UNPAID);
            }

            if (paid.signum() > 0) {
                Payment payment = new Payment();
                payment.setMethod(method);
                payment.setAmount(paid);
                sale.addPayment(payment);
            }

            session.persist(sale);
            com.fitsupplepos.util.AuditLogger.log(session, "SALE_CREATED", "Sale", sale.getInvoiceNumber(),
                    "Grand total ₹" + roundedTotal + ", " + sale.getItems().size() + " item(s), payment status " + sale.getPaymentStatus());
            log.info("Recorded sale {} — {} item line(s), grand total {}", sale.getInvoiceNumber(), sale.getItems().size(), roundedTotal);
            return sale;
        });
    }
}
