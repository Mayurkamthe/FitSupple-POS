package com.fitsupplepos.model;

import jakarta.persistence.*;

/** Represents a Meta-approved WhatsApp Business template (name + language + component layout). */
@Entity
@Table(name = "whatsapp_template")
public class WhatsAppTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_name", nullable = false, unique = true, length = 100)
    private String templateName;

    @Column(nullable = false, length = 10)
    private String language = "en";

    @Column(length = 30)
    private String category; // MARKETING / UTILITY / AUTHENTICATION (per Meta)

    @Column(name = "body_text", length = 2000)
    private String bodyText;

    @Column(name = "placeholder_count", nullable = false)
    private int placeholderCount = 0;

    @Column(nullable = false)
    private boolean active = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTemplateName() { return templateName; }
    public void setTemplateName(String templateName) { this.templateName = templateName; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getBodyText() { return bodyText; }
    public void setBodyText(String bodyText) { this.bodyText = bodyText; }
    public int getPlaceholderCount() { return placeholderCount; }
    public void setPlaceholderCount(int placeholderCount) { this.placeholderCount = placeholderCount; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
