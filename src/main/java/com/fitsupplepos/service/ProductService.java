package com.fitsupplepos.service;

import com.fitsupplepos.config.SessionManager;
import com.fitsupplepos.dao.ProductBatchDao;
import com.fitsupplepos.dao.ProductDao;
import com.fitsupplepos.exception.BusinessException;
import com.fitsupplepos.model.Product;
import com.fitsupplepos.model.ProductBatch;
import org.hibernate.query.Query;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ProductService {

    private final ProductDao productDao = new ProductDao();
    private final ProductBatchDao batchDao = new ProductBatchDao();

    /** Row shown in the Products list: the product plus its live aggregated stock. */
    public static class ProductStockRow {
        public final Product product;
        public final int totalStock;
        public final String stockStatus; // OUT_OF_STOCK / LOW_STOCK / OK

        public ProductStockRow(Product product, int totalStock) {
            this.product = product;
            this.totalStock = totalStock;
            if (totalStock <= 0) {
                this.stockStatus = "OUT OF STOCK";
            } else if (totalStock <= product.getMinimumStock()) {
                this.stockStatus = "LOW STOCK";
            } else {
                this.stockStatus = "OK";
            }
        }
    }

    public Product createProduct(Product product) {
        validate(product);
        if (product.getSku() != null && !product.getSku().isBlank()
                && productDao.findBySku(product.getSku()).isPresent()) {
            throw new BusinessException("A product with SKU \"" + product.getSku() + "\" already exists.");
        }
        if (product.getBarcode() != null && !product.getBarcode().isBlank()
                && productDao.findByBarcode(product.getBarcode()).isPresent()) {
            throw new BusinessException("A product with barcode \"" + product.getBarcode() + "\" already exists.");
        }
        return productDao.save(product);
    }

    public Product updateProduct(Product product) {
        validate(product);
        product.setUpdatedAt(LocalDateTime.now());
        return productDao.update(product);
    }

    public void deactivateProduct(Long productId) {
        productDao.findById(productId).ifPresent(p -> {
            p.setActive(false);
            productDao.update(p);
        });
    }

    private void validate(Product product) {
        if (product.getProductName() == null || product.getProductName().isBlank()) {
            throw new BusinessException("Product name is required.");
        }
        if (product.getCategory() == null) {
            throw new BusinessException("Product category is required.");
        }
        if (product.getSellingPrice() == null || product.getSellingPrice().signum() < 0) {
            throw new BusinessException("Selling price must be zero or greater.");
        }
    }

    public List<ProductStockRow> listActiveWithStock() {
        return SessionManager.withSession(session -> {
            List<Product> products = productDao.findAllActive();
            List<ProductStockRow> rows = new ArrayList<>();
            for (Product p : products) {
                Query<Long> q = session.createQuery(
                        "select coalesce(sum(b.quantityAvailable), 0) from ProductBatch b where b.product.id = :pid",
                        Long.class);
                q.setParameter("pid", p.getId());
                int total = q.getSingleResult().intValue();
                rows.add(new ProductStockRow(p, total));
            }
            return rows;
        });
    }

    public List<Product> search(String term) {
        return productDao.search(term);
    }

    public Optional<Product> findByBarcode(String barcode) {
        return productDao.findByBarcode(barcode);
    }

    public List<ProductBatch> getBatches(Long productId) {
        return batchDao.findAllForProduct(productId);
    }

    public List<ProductBatch> getExpiringWithin(int days) {
        return batchDao.findExpiringWithin(days);
    }

    public List<ProductBatch> getExpired() {
        return batchDao.findExpired();
    }
}
