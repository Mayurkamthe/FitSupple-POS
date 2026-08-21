package com.fitsupplepos.dao;

import com.fitsupplepos.model.PurchaseReturn;
import org.hibernate.Session;
import org.hibernate.query.Query;

import java.util.List;

public class PurchaseReturnDao extends GenericDao<PurchaseReturn, Long> {

    public PurchaseReturnDao() { super(PurchaseReturn.class); }

    public int sumReturnedForPurchaseItem(Session session, Long purchaseItemId) {
        Query<Long> q = session.createQuery(
                "select coalesce(sum(r.returnQuantity), 0) from PurchaseReturn r where r.purchaseItem.id = :pid", Long.class);
        q.setParameter("pid", purchaseItemId);
        return q.getSingleResult().intValue();
    }

    public List<PurchaseReturn> findRecent(int limit) {
        return query(session -> {
            Query<PurchaseReturn> q = session.createQuery("from PurchaseReturn order by returnedAt desc", PurchaseReturn.class);
            q.setMaxResults(limit);
            return q.list();
        });
    }
}
