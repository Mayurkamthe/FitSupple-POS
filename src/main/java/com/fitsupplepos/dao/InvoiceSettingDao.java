package com.fitsupplepos.dao;

import com.fitsupplepos.config.SessionManager;
import com.fitsupplepos.model.InvoiceSetting;

/** Access + atomic increment for the singleton invoice/purchase numbering row. */
public class InvoiceSettingDao {

    public InvoiceSetting get() {
        return SessionManager.withSession(session -> session.get(InvoiceSetting.class, 1L));
    }

    /** Must be called from within an existing transaction (same session as the Sale/Purchase being saved). */
    public String nextInvoiceNumber(org.hibernate.Session session) {
        InvoiceSetting settings = session.get(InvoiceSetting.class, 1L);
        long number = settings.getNextInvoiceNumber();
        settings.setNextInvoiceNumber(number + 1);
        session.merge(settings);
        return settings.getInvoicePrefix() + "-" + String.format("%06d", number);
    }

    public String nextPurchaseNumber(org.hibernate.Session session) {
        InvoiceSetting settings = session.get(InvoiceSetting.class, 1L);
        long number = settings.getNextPurchaseNumber();
        settings.setNextPurchaseNumber(number + 1);
        session.merge(settings);
        return settings.getPurchasePrefix() + "-" + String.format("%06d", number);
    }
}
