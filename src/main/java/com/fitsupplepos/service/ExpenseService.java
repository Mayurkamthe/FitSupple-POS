package com.fitsupplepos.service;

import com.fitsupplepos.dao.ExpenseDao;
import com.fitsupplepos.exception.BusinessException;
import com.fitsupplepos.model.Expense;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class ExpenseService {

    private final ExpenseDao expenseDao = new ExpenseDao();

    public Expense create(Expense expense) {
        validate(expense);
        return expenseDao.save(expense);
    }

    public Expense update(Expense expense) {
        validate(expense);
        return expenseDao.update(expense);
    }

    public void delete(Long id) {
        expenseDao.deleteById(id);
    }

    private void validate(Expense expense) {
        if (expense.getCategory() == null) {
            throw new BusinessException("Expense category is required.");
        }
        if (expense.getAmount() == null || expense.getAmount().signum() <= 0) {
            throw new BusinessException("Amount must be greater than zero.");
        }
        if (expense.getExpenseDate() == null) {
            throw new BusinessException("Expense date is required.");
        }
    }

    public List<Expense> listAll() {
        return expenseDao.findAllOrderedByDateDesc();
    }

    public List<Expense> listBetween(LocalDate start, LocalDate end) {
        return expenseDao.findBetween(start, end);
    }

    public BigDecimal totalFor(List<Expense> expenses) {
        return expenses.stream().map(Expense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
