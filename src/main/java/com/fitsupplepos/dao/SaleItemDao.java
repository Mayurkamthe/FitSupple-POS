package com.fitsupplepos.dao;

import com.fitsupplepos.model.SaleItem;
import org.hibernate.query.Query;

import java.util.List;

public class SaleItemDao extends GenericDao<SaleItem, Long> {
    public SaleItemDao() { super(SaleItem.class); }

    public List<SaleItem> findForSale(Long saleId) {
        return query(session -> {
            Query<SaleItem> q = session.createQuery("from SaleItem where sale.id = :sid", SaleItem.class);
            q.setParameter("sid", saleId);
            return q.list();
        });
    }
}
