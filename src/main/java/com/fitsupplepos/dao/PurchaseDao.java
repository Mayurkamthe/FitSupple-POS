package com.fitsupplepos.dao;

import com.fitsupplepos.model.Purchase;

import java.util.List;

public class PurchaseDao extends GenericDao<Purchase, Long> {
    public PurchaseDao() { super(Purchase.class); }

    public List<Purchase> findAllOrderedByDateDesc() {
        return query(session -> session.createQuery(
                "from Purchase order by purchaseDate desc", Purchase.class).list());
    }
}
