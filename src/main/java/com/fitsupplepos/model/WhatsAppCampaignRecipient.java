package com.fitsupplepos.model;

import com.fitsupplepos.model.enums.WhatsAppMessageStatus;
import jakarta.persistence.*;

@Entity
@Table(name = "whatsapp_campaign_recipient")
public class WhatsAppCampaignRecipient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campaign_id", nullable = false)
    private WhatsAppCampaign campaign;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private WhatsAppMessageStatus status = WhatsAppMessageStatus.QUEUED;

    @Column(name = "whatsapp_message_id", length = 100)
    private String whatsappMessageId;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public WhatsAppCampaign getCampaign() { return campaign; }
    public void setCampaign(WhatsAppCampaign campaign) { this.campaign = campaign; }
    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }
    public WhatsAppMessageStatus getStatus() { return status; }
    public void setStatus(WhatsAppMessageStatus status) { this.status = status; }
    public String getWhatsappMessageId() { return whatsappMessageId; }
    public void setWhatsappMessageId(String whatsappMessageId) { this.whatsappMessageId = whatsappMessageId; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
