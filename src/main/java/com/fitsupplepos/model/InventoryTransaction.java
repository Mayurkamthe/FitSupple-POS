package com.fitsupplepos.model;

import com.fitsupplepos.model.enums.TransactionType;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Full audit trail of every stock movement. Written inside the same transaction
 * as the Sale/Purchase/Return/Adjustment that caused it — never written standalone.
 */
@Entity
@Table(name = "inventory_transaction", indexes = {
        @Index(name = "idx_inv_txn_product", columnList = "product_id"),
        @Index(name = "idx_inv_txn_date", columnList = "created_at")
})
public class InventoryTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    private TransactionType transactionType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private ProductBatch batch;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "previous_stock", nullable = false)
    private int previousStock;

    @Column(name = "new_stock", nullable = false)
    private int newStock;

    @Column(name = "reference_invoice", length = 60)
    private String referenceInvoice;

    @Column(length = 300)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public TransactionType getTransactionType() { return transactionType; }
    public void setTransactionType(TransactionType transactionType) { this.transactionType = transactionType; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public ProductBatch getBatch() { return batch; }
    public void setBatch(ProductBatch batch) { this.batch = batch; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public int getPreviousStock() { return previousStock; }
    public void setPreviousStock(int previousStock) { this.previousStock = previousStock; }
    public int getNewStock() { return newStock; }
    public void setNewStock(int newStock) { this.newStock = newStock; }
    public String getReferenceInvoice() { return referenceInvoice; }
    public void setReferenceInvoice(String referenceInvoice) { this.referenceInvoice = referenceInvoice; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
