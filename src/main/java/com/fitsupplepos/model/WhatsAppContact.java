package com.fitsupplepos.model;

import jakarta.persistence.*;

/**
 * Mirrors a Customer's WhatsApp-relevant fields for fast lookups/opt-in tracking,
 * decoupled from Customer so WhatsApp-specific fields don't bloat the CRM entity.
 */
@Entity
@Table(name = "whatsapp_contact")
public class WhatsAppContact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, unique = true)
    private Customer customer;

    @Column(name = "whatsapp_number", nullable = false, length = 20)
    private String whatsappNumber;

    @Column(name = "opted_in", nullable = false)
    private boolean optedIn = true;

    @Column(name = "last_message_at")
    private java.time.LocalDateTime lastMessageAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }
    public String getWhatsappNumber() { return whatsappNumber; }
    public void setWhatsappNumber(String whatsappNumber) { this.whatsappNumber = whatsappNumber; }
    public boolean isOptedIn() { return optedIn; }
    public void setOptedIn(boolean optedIn) { this.optedIn = optedIn; }
    public java.time.LocalDateTime getLastMessageAt() { return lastMessageAt; }
    public void setLastMessageAt(java.time.LocalDateTime lastMessageAt) { this.lastMessageAt = lastMessageAt; }
}
