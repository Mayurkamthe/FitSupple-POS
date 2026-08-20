package com.fitsupplepos.dao;

import com.fitsupplepos.model.Sale;
import org.hibernate.query.Query;

import java.util.List;
import java.util.Optional;

public class SaleDao extends GenericDao<Sale, Long> {

    public SaleDao() { super(Sale.class); }

    public List<Sale> findAllOrderedByDateDesc() {
        return query(session -> session.createQuery("from Sale order by saleDate desc", Sale.class).list());
    }

    public Optional<Sale> findByInvoiceNumber(String invoiceNumber) {
        return query(session -> {
            Query<Sale> q = session.createQuery("from Sale where invoiceNumber = :inv", Sale.class);
            q.setParameter("inv", invoiceNumber);
            return q.uniqueResultOptional();
        });
    }
}
