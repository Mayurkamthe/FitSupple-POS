package com.fitsupplepos.dao;

import com.fitsupplepos.model.Product;
import org.hibernate.query.Query;

import java.util.List;
import java.util.Optional;

public class ProductDao extends GenericDao<Product, Long> {

    public ProductDao() { super(Product.class); }

    public Optional<Product> findByBarcode(String barcode) {
        return query(session -> {
            Query<Product> q = session.createQuery("from Product where barcode = :b", Product.class);
            q.setParameter("b", barcode);
            return q.uniqueResultOptional();
        });
    }

    public Optional<Product> findBySku(String sku) {
        return query(session -> {
            Query<Product> q = session.createQuery("from Product where sku = :s", Product.class);
            q.setParameter("s", sku);
            return q.uniqueResultOptional();
        });
    }

    public List<Product> search(String term) {
        return query(session -> {
            String like = "%" + (term == null ? "" : term.toLowerCase()) + "%";
            Query<Product> q = session.createQuery(
                    "from Product p where p.active = true and (" +
                            "lower(p.productName) like :t or lower(p.sku) like :t or lower(p.barcode) like :t)",
                    Product.class);
            q.setParameter("t", like);
            return q.list();
        });
    }

    public List<Product> findAllActive() {
        return query(session -> session.createQuery("from Product where active = true order by productName", Product.class).list());
    }
}
