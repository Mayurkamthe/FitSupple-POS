package com.fitsupplepos.service;

import com.fitsupplepos.config.SessionManager;
import com.fitsupplepos.exception.BusinessException;
import com.fitsupplepos.model.GstSetting;
import com.fitsupplepos.model.enums.BillingMode;

import java.math.BigDecimal;

public class GstSettingService {

    public GstSetting get() {
        return SessionManager.withSession(session -> {
            GstSetting setting = session.get(GstSetting.class, 1L);
            if (setting == null) {
                setting = new GstSetting();
                setting.setId(1L);
            }
            return setting;
        });
    }

    public GstSetting save(BillingMode billingMode, String gstin, String stateCode, BigDecimal defaultGstRate) {
        if (billingMode == BillingMode.GST && (gstin == null || gstin.isBlank())) {
            throw new BusinessException("GSTIN is required when GST billing mode is enabled.");
        }
        return SessionManager.withTransaction(session -> {
            GstSetting setting = session.get(GstSetting.class, 1L);
            if (setting == null) {
                setting = new GstSetting();
                setting.setId(1L);
            }
            setting.setBillingMode(billingMode);
            setting.setGstin(gstin);
            setting.setStateCode(stateCode);
            setting.setDefaultGstRate(defaultGstRate == null ? BigDecimal.ZERO : defaultGstRate);
            session.merge(setting);
            com.fitsupplepos.util.AuditLogger.log(session, "SETTINGS_CHANGED", "GstSetting", "1",
                    "Billing mode set to " + billingMode + (gstin != null && !gstin.isBlank() ? ", GSTIN " + gstin : "")
                            + (stateCode != null && !stateCode.isBlank() ? ", state code " + stateCode : ""));
            return setting;
        });
    }
}
