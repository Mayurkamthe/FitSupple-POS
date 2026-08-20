package com.fitsupplepos.dao;

import com.fitsupplepos.model.Product;
import com.fitsupplepos.model.ProductBatch;
import org.hibernate.Session;
import org.hibernate.query.Query;

import java.time.LocalDate;
import java.util.List;

public class ProductBatchDao extends GenericDao<ProductBatch, Long> {

    public ProductBatchDao() { super(ProductBatch.class); }

    /**
     * FEFO ordering: batches with the soonest expiry date first (nulls-last), then oldest-created first.
     * Only returns batches that still have stock and are not expired.
     */
    public List<ProductBatch> findSellableBatchesFefo(Session session, Long productId) {
        Query<ProductBatch> q = session.createQuery(
                "from ProductBatch b where b.product.id = :pid and b.quantityAvailable > 0 " +
                        "and (b.expiryDate is null or b.expiryDate >= :today) " +
                        "order by case when b.expiryDate is null then 1 else 0 end, b.expiryDate asc, b.createdAt asc",
                ProductBatch.class);
        q.setParameter("pid", productId);
        q.setParameter("today", LocalDate.now());
        return q.list();
    }

    public List<ProductBatch> findAllForProduct(Long productId) {
        return query(session -> {
            Query<ProductBatch> q = session.createQuery(
                    "from ProductBatch b where b.product.id = :pid order by b.expiryDate asc nulls last", ProductBatch.class);
            q.setParameter("pid", productId);
            return q.list();
        });
    }

    public List<ProductBatch> findLowStock() {
        return query(session -> session.createQuery(
                "select b from ProductBatch b where b.quantityAvailable > 0", ProductBatch.class).list());
    }

    public List<ProductBatch> findExpiringWithin(int days) {
        return query(session -> {
            LocalDate cutoff = LocalDate.now().plusDays(days);
            Query<ProductBatch> q = session.createQuery(
                    "from ProductBatch b where b.expiryDate is not null and b.expiryDate >= :today " +
                            "and b.expiryDate <= :cutoff and b.quantityAvailable > 0 order by b.expiryDate asc",
                    ProductBatch.class);
            q.setParameter("today", LocalDate.now());
            q.setParameter("cutoff", cutoff);
            return q.list();
        });
    }

    public List<ProductBatch> findExpired() {
        return query(session -> {
            Query<ProductBatch> q = session.createQuery(
                    "from ProductBatch b where b.expiryDate is not null and b.expiryDate < :today and b.quantityAvailable > 0",
                    ProductBatch.class);
            q.setParameter("today", LocalDate.now());
            return q.list();
        });
    }

    public List<ProductBatch> findOutOfStock() {
        return query(session -> session.createQuery(
                "from ProductBatch b where b.quantityAvailable <= 0", ProductBatch.class).list());
    }
}
