package com.fitsupplepos.dao;

import com.fitsupplepos.model.Customer;
import org.hibernate.query.Query;

import java.util.List;
import java.util.Optional;

public class CustomerDao extends GenericDao<Customer, Long> {

    public CustomerDao() { super(Customer.class); }

    public Optional<Customer> findByMobile(String mobile) {
        return query(session -> {
            Query<Customer> q = session.createQuery("from Customer where mobile = :m", Customer.class);
            q.setParameter("m", mobile);
            return q.uniqueResultOptional();
        });
    }

    public List<Customer> search(String term) {
        return query(session -> {
            String like = "%" + (term == null ? "" : term.toLowerCase()) + "%";
            Query<Customer> q = session.createQuery(
                    "from Customer where lower(name) like :t or mobile like :t order by name", Customer.class);
            q.setParameter("t", like);
            return q.list();
        });
    }

    public List<Customer> findAllOrderedByName() {
        return query(session -> session.createQuery("from Customer order by name", Customer.class).list());
    }
}
