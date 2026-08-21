package com.fitsupplepos.dao;

import com.fitsupplepos.model.Expense;
import org.hibernate.query.Query;

import java.time.LocalDate;
import java.util.List;

public class ExpenseDao extends GenericDao<Expense, Long> {

    public ExpenseDao() { super(Expense.class); }

    public List<Expense> findAllOrderedByDateDesc() {
        return query(session -> session.createQuery("from Expense order by expenseDate desc, createdAt desc", Expense.class).list());
    }

    public List<Expense> findBetween(LocalDate start, LocalDate end) {
        return query(session -> {
            Query<Expense> q = session.createQuery(
                    "from Expense where expenseDate >= :start and expenseDate <= :end order by expenseDate desc", Expense.class);
            q.setParameter("start", start);
            q.setParameter("end", end);
            return q.list();
        });
    }
}
