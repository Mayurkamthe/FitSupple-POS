package com.fitsupplepos.dao;

import com.fitsupplepos.model.Brand;
import org.hibernate.query.Query;

import java.util.Optional;

public class BrandDao extends GenericDao<Brand, Long> {
    public BrandDao() { super(Brand.class); }

    public Optional<Brand> findByName(String name) {
        return query(session -> {
            Query<Brand> q = session.createQuery("from Brand where lower(name) = lower(:n)", Brand.class);
            q.setParameter("n", name);
            return q.uniqueResultOptional();
        });
    }
}
