package com.fitsupplepos.dao;

import com.fitsupplepos.model.InventoryTransaction;
import org.hibernate.query.Query;

import java.util.List;

public class InventoryTransactionDao extends GenericDao<InventoryTransaction, Long> {

    public InventoryTransactionDao() { super(InventoryTransaction.class); }

    public List<InventoryTransaction> findRecent(int limit) {
        return query(session -> {
            Query<InventoryTransaction> q = session.createQuery(
                    "from InventoryTransaction order by createdAt desc", InventoryTransaction.class);
            q.setMaxResults(limit);
            return q.list();
        });
    }

    public List<InventoryTransaction> findForProduct(Long productId, int limit) {
        return query(session -> {
            Query<InventoryTransaction> q = session.createQuery(
                    "from InventoryTransaction where product.id = :pid order by createdAt desc", InventoryTransaction.class);
            q.setParameter("pid", productId);
            q.setMaxResults(limit);
            return q.list();
        });
    }
}
