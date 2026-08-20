package com.fitsupplepos.model;

import jakarta.persistence.*;

/**
 * Single-row table holding shop/invoice branding & numbering configuration.
 */
@Entity
@Table(name = "invoice_setting")
public class InvoiceSetting {

    @Id
    private Long id = 1L; // singleton row

    @Column(name = "shop_name", nullable = false, length = 150)
    private String shopName = "FitSupple Nutrition Store";

    @Column(length = 300)
    private String address;

    @Column(length = 20)
    private String phone;

    @Column(length = 150)
    private String email;

    @Column(name = "logo_path", length = 300)
    private String logoPath;

    @Column(name = "invoice_prefix", nullable = false, length = 10)
    private String invoicePrefix = "INV";

    @Column(name = "purchase_prefix", nullable = false, length = 10)
    private String purchasePrefix = "PUR";

    @Column(name = "next_invoice_number", nullable = false)
    private long nextInvoiceNumber = 1;

    @Column(name = "next_purchase_number", nullable = false)
    private long nextPurchaseNumber = 1;

    @Column(name = "invoice_footer_note", length = 300)
    private String invoiceFooterNote = "Thank you for shopping with us!";

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getShopName() { return shopName; }
    public void setShopName(String shopName) { this.shopName = shopName; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getLogoPath() { return logoPath; }
    public void setLogoPath(String logoPath) { this.logoPath = logoPath; }
    public String getInvoicePrefix() { return invoicePrefix; }
    public void setInvoicePrefix(String invoicePrefix) { this.invoicePrefix = invoicePrefix; }
    public String getPurchasePrefix() { return purchasePrefix; }
    public void setPurchasePrefix(String purchasePrefix) { this.purchasePrefix = purchasePrefix; }
    public long getNextInvoiceNumber() { return nextInvoiceNumber; }
    public void setNextInvoiceNumber(long nextInvoiceNumber) { this.nextInvoiceNumber = nextInvoiceNumber; }
    public long getNextPurchaseNumber() { return nextPurchaseNumber; }
    public void setNextPurchaseNumber(long nextPurchaseNumber) { this.nextPurchaseNumber = nextPurchaseNumber; }
    public String getInvoiceFooterNote() { return invoiceFooterNote; }
    public void setInvoiceFooterNote(String invoiceFooterNote) { this.invoiceFooterNote = invoiceFooterNote; }
}
