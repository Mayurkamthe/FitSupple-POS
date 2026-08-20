package com.fitsupplepos.model;

import com.fitsupplepos.model.enums.CampaignAudience;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "whatsapp_campaign")
public class WhatsAppCampaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CampaignAudience audience;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private WhatsAppTemplate template;

    @Column(name = "message_body", length = 2000)
    private String messageBody;

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(nullable = false, length = 20)
    private String status = "DRAFT"; // DRAFT / SCHEDULED / SENDING / COMPLETED / FAILED

    @OneToMany(mappedBy = "campaign", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<WhatsAppCampaignRecipient> recipients = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public CampaignAudience getAudience() { return audience; }
    public void setAudience(CampaignAudience audience) { this.audience = audience; }
    public WhatsAppTemplate getTemplate() { return template; }
    public void setTemplate(WhatsAppTemplate template) { this.template = template; }
    public String getMessageBody() { return messageBody; }
    public void setMessageBody(String messageBody) { this.messageBody = messageBody; }
    public LocalDateTime getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(LocalDateTime scheduledAt) { this.scheduledAt = scheduledAt; }
    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public List<WhatsAppCampaignRecipient> getRecipients() { return recipients; }
    public void setRecipients(List<WhatsAppCampaignRecipient> recipients) { this.recipients = recipients; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
