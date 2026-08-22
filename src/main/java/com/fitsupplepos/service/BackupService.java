package com.fitsupplepos.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fitsupplepos.config.AppPaths;
import com.fitsupplepos.config.HibernateConfig;
import com.fitsupplepos.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

/**
 * Backup & Restore for the local SQLite database. Because SQLite is a single file,
 * backup/restore is a straightforward file copy — but it must be done with Hibernate's
 * connection pool fully closed first so nothing is mid-write when the file is copied
 * or replaced.
 */
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);
    private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss");

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public File createBackup() {
        try {
            Path backupDir = AppPaths.backupDir();
            Files.createDirectories(backupDir);

            Path liveDb = HibernateConfig.resolveDatabasePath();
            if (!Files.exists(liveDb)) {
                throw new BusinessException("No live database file was found to back up.");
            }

            String filename = "fitsupple_backup_" + LocalDateTime.now().format(TIMESTAMP_FMT) + ".db";
            Path backupPath = backupDir.resolve(filename);

            // Flush the pool's connections before copying so SQLite isn't mid-write.
            HibernateConfig.shutdownAndReset();
            Files.copy(liveDb, backupPath, StandardCopyOption.REPLACE_EXISTING);
            // Re-open immediately so the rest of the app keeps working.
            HibernateConfig.getSessionFactory();

            log.info("Created backup at {}", backupPath);
            return backupPath.toFile();
        } catch (IOException e) {
            log.error("Backup failed", e);
            throw new BusinessException("Backup failed: " + e.getMessage());
        }
    }

    public List<File> listBackups() {
        Path backupDir = AppPaths.backupDir();
        File dir = backupDir.toFile();
        if (!dir.exists()) {
            return List.of();
        }
        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".db"));
        if (files == null) return List.of();
        return List.of(files).stream()
                .sorted(Comparator.comparingLong(File::lastModified).reversed())
                .toList();
    }

    /** Always creates an automatic backup of the CURRENT database before overwriting it. */
    public void restoreBackup(File backupFile) {
        if (backupFile == null || !backupFile.exists()) {
            throw new BusinessException("Selected backup file was not found.");
        }
        try {
            log.info("Restoring from backup {} — creating a safety backup of the current database first.", backupFile.getName());
            createBackup(); // safety net — never overwrite live data without a fresh backup first

            Path liveDb = HibernateConfig.resolveDatabasePath();
            HibernateConfig.shutdownAndReset();
            Files.copy(backupFile.toPath(), liveDb, StandardCopyOption.REPLACE_EXISTING);
            HibernateConfig.getSessionFactory();

            log.info("Database restored from {}", backupFile.getName());
        } catch (IOException e) {
            log.error("Restore failed", e);
            // Make sure the app still has a usable session factory even if the copy failed.
            HibernateConfig.getSessionFactory();
            throw new BusinessException("Restore failed: " + e.getMessage());
        }
    }

    /**
     * Exports core business data as human-readable JSON files. Uses flat HQL projections
     * (plain scalar columns) rather than serializing entities directly — Hibernate's lazy
     * associations and bidirectional relationships (Sale <-> SaleItem, Product <-> Brand)
     * would otherwise throw LazyInitializationException or recurse infinitely once the
     * session that loaded them is closed, since Jackson serializes after the fact.
     */
    public File exportData() {
        try {
            String folderName = "export_" + LocalDateTime.now().format(TIMESTAMP_FMT);
            Path exportDir = AppPaths.backupDir().resolve(folderName);
            Files.createDirectories(exportDir);

            com.fitsupplepos.config.SessionManager.withSessionVoid(session -> {
                try {
                    List<Object[]> products = session.createQuery(
                            "select p.id, p.productName, p.sku, p.barcode, p.category, p.mrp, p.sellingPrice, p.purchasePrice, p.active " +
                                    "from Product p", Object[].class).list();
                    writeRows(exportDir.resolve("products.json"),
                            products, new String[]{"id", "productName", "sku", "barcode", "category", "mrp", "sellingPrice", "purchasePrice", "active"});

                    List<Object[]> customers = session.createQuery(
                            "select c.id, c.name, c.mobile, c.email, c.whatsappOptIn from Customer c", Object[].class).list();
                    writeRows(exportDir.resolve("customers.json"),
                            customers, new String[]{"id", "name", "mobile", "email", "whatsappOptIn"});

                    List<Object[]> sales = session.createQuery(
                            "select s.id, s.invoiceNumber, s.saleDate, s.grandTotal, s.paymentStatus from Sale s", Object[].class).list();
                    writeRows(exportDir.resolve("sales.json"),
                            sales, new String[]{"id", "invoiceNumber", "saleDate", "grandTotal", "paymentStatus"});

                    List<Object[]> purchases = session.createQuery(
                            "select p.id, p.invoiceNumber, p.purchaseDate, p.supplier.name, p.grandTotal, p.paymentStatus from Purchase p",
                            Object[].class).list();
                    writeRows(exportDir.resolve("purchases.json"),
                            purchases, new String[]{"id", "invoiceNumber", "purchaseDate", "supplierName", "grandTotal", "paymentStatus"});
                } catch (IOException e) {
                    throw new BusinessException("Data export failed: " + e.getMessage());
                }
            });

            log.info("Exported data to {}", exportDir);
            return exportDir.toFile();
        } catch (IOException e) {
            log.error("Data export failed", e);
            throw new BusinessException("Data export failed: " + e.getMessage());
        }
    }

    private void writeRows(Path file, List<Object[]> rows, String[] columnNames) throws IOException {
        List<java.util.Map<String, Object>> mapped = rows.stream().map(row -> {
            java.util.LinkedHashMap<String, Object> map = new java.util.LinkedHashMap<>();
            for (int i = 0; i < columnNames.length; i++) {
                Object value = row[i];
                map.put(columnNames[i], value == null ? null : value.toString());
            }
            return map;
        }).toList();
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), mapped);
    }
}
