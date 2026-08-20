package com.fitsupplepos.model;

import com.fitsupplepos.model.enums.ReturnReason;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "sales_return")
public class SalesReturn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_item_id", nullable = false)
    private SaleItem saleItem;

    @Column(name = "return_quantity", nullable = false)
    private int returnQuantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "return_reason", nullable = false, length = 30)
    private ReturnReason returnReason;

    @Column(name = "refund_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal refundAmount = BigDecimal.ZERO;

    @Column(length = 300)
    private String notes;

    @Column(name = "returned_at", nullable = false)
    private LocalDateTime returnedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Sale getSale() { return sale; }
    public void setSale(Sale sale) { this.sale = sale; }
    public SaleItem getSaleItem() { return saleItem; }
    public void setSaleItem(SaleItem saleItem) { this.saleItem = saleItem; }
    public int getReturnQuantity() { return returnQuantity; }
    public void setReturnQuantity(int returnQuantity) { this.returnQuantity = returnQuantity; }
    public ReturnReason getReturnReason() { return returnReason; }
    public void setReturnReason(ReturnReason returnReason) { this.returnReason = returnReason; }
    public BigDecimal getRefundAmount() { return refundAmount; }
    public void setRefundAmount(BigDecimal refundAmount) { this.refundAmount = refundAmount; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public LocalDateTime getReturnedAt() { return returnedAt; }
    public void setReturnedAt(LocalDateTime returnedAt) { this.returnedAt = returnedAt; }
}
