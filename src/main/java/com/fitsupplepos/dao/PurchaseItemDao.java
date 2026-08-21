package com.fitsupplepos.dao;

import com.fitsupplepos.model.PurchaseItem;
import org.hibernate.query.Query;

import java.util.List;

public class PurchaseItemDao extends GenericDao<PurchaseItem, Long> {
    public PurchaseItemDao() { super(PurchaseItem.class); }

    public List<PurchaseItem> findForPurchase(Long purchaseId) {
        return query(session -> {
            Query<PurchaseItem> q = session.createQuery("from PurchaseItem where purchase.id = :pid", PurchaseItem.class);
            q.setParameter("pid", purchaseId);
            return q.list();
        });
    }
}
