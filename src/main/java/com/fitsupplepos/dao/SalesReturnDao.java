package com.fitsupplepos.dao;

import com.fitsupplepos.model.SalesReturn;
import org.hibernate.Session;
import org.hibernate.query.Query;

import java.util.List;

public class SalesReturnDao extends GenericDao<SalesReturn, Long> {

    public SalesReturnDao() { super(SalesReturn.class); }

    /** Must run inside the caller's existing transaction session so it sees uncommitted returns from this same request. */
    public int sumReturnedForSaleItem(Session session, Long saleItemId) {
        Query<Long> q = session.createQuery(
                "select coalesce(sum(r.returnQuantity), 0) from SalesReturn r where r.saleItem.id = :sid", Long.class);
        q.setParameter("sid", saleItemId);
        return q.getSingleResult().intValue();
    }

    public List<SalesReturn> findRecent(int limit) {
        return query(session -> {
            Query<SalesReturn> q = session.createQuery("from SalesReturn order by returnedAt desc", SalesReturn.class);
            q.setMaxResults(limit);
            return q.list();
        });
    }
}
