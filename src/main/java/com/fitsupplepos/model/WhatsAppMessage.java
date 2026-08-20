package com.fitsupplepos.model;

import com.fitsupplepos.model.enums.WhatsAppMessagePurpose;
import com.fitsupplepos.model.enums.WhatsAppMessageStatus;
import com.fitsupplepos.model.enums.WhatsAppMessageType;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Log of every individual WhatsApp message sent (invoice, offer, confirmation, campaign message, ...). */
@Entity
@Table(name = "whatsapp_message")
public class WhatsAppMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id")
    private Sale sale;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 15)
    private WhatsAppMessageType messageType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private WhatsAppMessagePurpose purpose;

    @Column(name = "recipient_number", nullable = false, length = 20)
    private String recipientNumber;

    @Column(length = 2000)
    private String content;

    @Column(name = "whatsapp_message_id", length = 100)
    private String whatsappMessageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private WhatsAppMessageStatus status = WhatsAppMessageStatus.QUEUED;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }
    public Sale getSale() { return sale; }
    public void setSale(Sale sale) { this.sale = sale; }
    public WhatsAppMessageType getMessageType() { return messageType; }
    public void setMessageType(WhatsAppMessageType messageType) { this.messageType = messageType; }
    public WhatsAppMessagePurpose getPurpose() { return purpose; }
    public void setPurpose(WhatsAppMessagePurpose purpose) { this.purpose = purpose; }
    public String getRecipientNumber() { return recipientNumber; }
    public void setRecipientNumber(String recipientNumber) { this.recipientNumber = recipientNumber; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getWhatsappMessageId() { return whatsappMessageId; }
    public void setWhatsappMessageId(String whatsappMessageId) { this.whatsappMessageId = whatsappMessageId; }
    public WhatsAppMessageStatus getStatus() { return status; }
    public void setStatus(WhatsAppMessageStatus status) { this.status = status; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }
}
