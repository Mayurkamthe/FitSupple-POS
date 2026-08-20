package com.fitsupplepos.dao;

import com.fitsupplepos.model.Supplier;

import java.util.List;

public class SupplierDao extends GenericDao<Supplier, Long> {
    public SupplierDao() { super(Supplier.class); }

    public List<Supplier> findAllActive() {
        return query(session -> session.createQuery(
                "from Supplier where active = true order by name", Supplier.class).list());
    }
}
