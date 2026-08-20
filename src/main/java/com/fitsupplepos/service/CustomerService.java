package com.fitsupplepos.service;

import com.fitsupplepos.config.SessionManager;
import com.fitsupplepos.dao.CustomerDao;
import com.fitsupplepos.exception.BusinessException;
import com.fitsupplepos.model.Customer;
import com.fitsupplepos.model.enums.CustomerSegment;
import org.hibernate.query.Query;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public class CustomerService {

    private final CustomerDao customerDao = new CustomerDao();

    public static class CustomerStats {
        public long orderCount;
        public BigDecimal totalSpending = BigDecimal.ZERO;
        public LocalDateTime lastPurchase;
        public String favouriteProduct = "-";
    }

    public Customer create(Customer customer) {
        validate(customer);
        if (customerDao.findByMobile(customer.getMobile()).isPresent()) {
            throw new BusinessException("A customer with mobile number " + customer.getMobile() + " already exists.");
        }
        return customerDao.save(customer);
    }

    public Customer update(Customer customer) {
        validate(customer);
        return customerDao.update(customer);
    }

    private void validate(Customer customer) {
        if (customer.getName() == null || customer.getName().isBlank()) {
            throw new BusinessException("Customer name is required.");
        }
        if (customer.getMobile() == null || customer.getMobile().isBlank()) {
            throw new BusinessException("Mobile number is required.");
        }
    }

    public List<Customer> listAll() {
        return customerDao.findAllOrderedByName();
    }

    public List<Customer> search(String term) {
        return customerDao.search(term);
    }

    public Optional<Customer> findByMobile(String mobile) {
        return customerDao.findByMobile(mobile);
    }

    public CustomerStats getStats(Long customerId) {
        return SessionManager.withSession(session -> {
            CustomerStats stats = new CustomerStats();

            Long orders = session.createQuery(
                            "select count(s) from Sale s where s.customer.id = :cid", Long.class)
                    .setParameter("cid", customerId).getSingleResult();
            stats.orderCount = orders;

            BigDecimal total = session.createQuery(
                            "select coalesce(sum(s.grandTotal), 0) from Sale s where s.customer.id = :cid", BigDecimal.class)
                    .setParameter("cid", customerId).getSingleResult();
            stats.totalSpending = total;

            List<LocalDateTime> lastDates = session.createQuery(
                            "select s.saleDate from Sale s where s.customer.id = :cid order by s.saleDate desc",
                            LocalDateTime.class)
                    .setParameter("cid", customerId).setMaxResults(1).list();
            if (!lastDates.isEmpty()) {
                stats.lastPurchase = lastDates.get(0);
            }

            List<Object[]> favRows = session.createQuery(
                            "select si.product.productName, sum(si.quantity) as qty from SaleItem si " +
                                    "where si.sale.customer.id = :cid group by si.product.productName order by qty desc",
                            Object[].class)
                    .setParameter("cid", customerId).setMaxResults(1).list();
            if (!favRows.isEmpty()) {
                stats.favouriteProduct = (String) favRows.get(0)[0];
            }

            return stats;
        });
    }

    /** Computes the customer's marketing/behavioural segment from real purchase history. */
    public CustomerSegment computeSegment(Customer customer) {
        CustomerStats stats = getStats(customer.getId());

        if (stats.orderCount == 0) {
            return CustomerSegment.NEW;
        }
        if (stats.lastPurchase != null) {
            long daysSinceLast = java.time.Duration.between(stats.lastPurchase, LocalDateTime.now()).toDays();
            if (daysSinceLast >= 60) return CustomerSegment.INACTIVE_60;
            if (daysSinceLast >= 30) return CustomerSegment.INACTIVE_30;
        }
        if (stats.totalSpending.compareTo(BigDecimal.valueOf(20000)) >= 0) {
            return CustomerSegment.HIGH_VALUE;
        }
        if (stats.orderCount >= 5) {
            return CustomerSegment.VIP;
        }
        return CustomerSegment.REGULAR;
    }
}
