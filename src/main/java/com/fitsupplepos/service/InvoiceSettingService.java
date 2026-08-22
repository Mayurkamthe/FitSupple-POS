package com.fitsupplepos.service;

import com.fitsupplepos.config.SessionManager;
import com.fitsupplepos.exception.BusinessException;
import com.fitsupplepos.model.InvoiceSetting;

public class InvoiceSettingService {

    public InvoiceSetting get() {
        return SessionManager.withSession(session -> {
            InvoiceSetting setting = session.get(InvoiceSetting.class, 1L);
            if (setting == null) {
                setting = new InvoiceSetting();
                setting.setId(1L);
            }
            return setting;
        });
    }

    public InvoiceSetting save(String shopName, String address, String phone, String email, String logoPath,
                                String invoicePrefix, String purchasePrefix, String footerNote) {
        if (shopName == null || shopName.isBlank()) {
            throw new BusinessException("Shop name is required.");
        }
        return SessionManager.withTransaction(session -> {
            InvoiceSetting setting = session.get(InvoiceSetting.class, 1L);
            if (setting == null) {
                setting = new InvoiceSetting();
                setting.setId(1L);
            }
            setting.setShopName(shopName.trim());
            setting.setAddress(address);
            setting.setPhone(phone);
            setting.setEmail(email);
            setting.setLogoPath(logoPath);
            setting.setInvoicePrefix(invoicePrefix == null || invoicePrefix.isBlank() ? "INV" : invoicePrefix.trim());
            setting.setPurchasePrefix(purchasePrefix == null || purchasePrefix.isBlank() ? "PUR" : purchasePrefix.trim());
            setting.setInvoiceFooterNote(footerNote);
            session.merge(setting);
            return setting;
        });
    }
}
