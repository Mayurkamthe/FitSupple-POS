package com.fitsupplepos.model;

import com.fitsupplepos.model.enums.BillingMode;
import jakarta.persistence.*;

/**
 * Single-row table holding the shop's GST configuration.
 */
@Entity
@Table(name = "gst_setting")
public class GstSetting {

    @Id
    private Long id = 1L; // singleton row

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_mode", nullable = false, length = 10)
    private BillingMode billingMode = BillingMode.NON_GST;

    @Column(length = 20)
    private String gstin;

    @Column(name = "state_code", length = 5)
    private String stateCode;

    @Column(name = "default_gst_rate", precision = 5, scale = 2)
    private java.math.BigDecimal defaultGstRate = java.math.BigDecimal.ZERO;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public BillingMode getBillingMode() { return billingMode; }
    public void setBillingMode(BillingMode billingMode) { this.billingMode = billingMode; }
    public String getGstin() { return gstin; }
    public void setGstin(String gstin) { this.gstin = gstin; }
    public String getStateCode() { return stateCode; }
    public void setStateCode(String stateCode) { this.stateCode = stateCode; }
    public java.math.BigDecimal getDefaultGstRate() { return defaultGstRate; }
    public void setDefaultGstRate(java.math.BigDecimal defaultGstRate) { this.defaultGstRate = defaultGstRate; }
}
