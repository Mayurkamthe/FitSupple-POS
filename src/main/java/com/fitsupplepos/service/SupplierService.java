package com.fitsupplepos.service;

import com.fitsupplepos.dao.SupplierDao;
import com.fitsupplepos.exception.BusinessException;
import com.fitsupplepos.model.Supplier;

import java.util.List;

public class SupplierService {

    private final SupplierDao supplierDao = new SupplierDao();

    public Supplier create(Supplier supplier) {
        validate(supplier);
        return supplierDao.save(supplier);
    }

    public Supplier update(Supplier supplier) {
        validate(supplier);
        return supplierDao.update(supplier);
    }

    public void deactivate(Long id) {
        supplierDao.findById(id).ifPresent(s -> {
            s.setActive(false);
            supplierDao.update(s);
        });
    }

    private void validate(Supplier supplier) {
        if (supplier.getName() == null || supplier.getName().isBlank()) {
            throw new BusinessException("Supplier name is required.");
        }
    }

    public List<Supplier> listActive() {
        return supplierDao.findAllActive();
    }
}
