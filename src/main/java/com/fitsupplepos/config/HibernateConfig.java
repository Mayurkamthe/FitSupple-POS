package com.fitsupplepos.config;

import com.fitsupplepos.model.*;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds and owns the single application-wide Hibernate {@link SessionFactory}.
 *
 * SQLite only supports one writer at a time, so the pool is deliberately capped at a
 * single connection — Hibernate/Hikari then serialize access for us instead of the app
 * hitting "database is locked" errors under concurrent access.
 */
public final class HibernateConfig {

    private static final Logger log = LoggerFactory.getLogger(HibernateConfig.class);

    private static volatile SessionFactory sessionFactory;
    private static volatile HikariDataSource dataSource;

    private HibernateConfig() {}

    public static synchronized SessionFactory getSessionFactory() {
        if (sessionFactory == null) {
            sessionFactory = buildSessionFactory();
        }
        return sessionFactory;
    }

    private static SessionFactory buildSessionFactory() {
        try {
            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl("jdbc:sqlite:" + AppPaths.databaseFile());
            hikariConfig.setDriverClassName("org.sqlite.JDBC");
            hikariConfig.setMaximumPoolSize(1);
            hikariConfig.setMinimumIdle(1);
            hikariConfig.setPoolName("fitsupple-sqlite-pool");
            hikariConfig.setConnectionTestQuery("SELECT 1");
            dataSource = new HikariDataSource(hikariConfig);

            StandardServiceRegistryBuilder registryBuilder = new StandardServiceRegistryBuilder();
            registryBuilder.applySetting(AvailableSettings.DIALECT, "org.hibernate.community.dialect.SQLiteDialect");
            registryBuilder.applySetting(AvailableSettings.DATASOURCE, dataSource);
            registryBuilder.applySetting(AvailableSettings.HBM2DDL_AUTO, "update");
            registryBuilder.applySetting(AvailableSettings.SHOW_SQL, "false");
            registryBuilder.applySetting(AvailableSettings.FORMAT_SQL, "false");
            registryBuilder.applySetting(AvailableSettings.STATEMENT_BATCH_SIZE, "0");
            // SQLite has no schema-level concurrency; keep Hibernate's connection handling
            // aligned to a single connection per transaction to avoid nested-locking issues.
            registryBuilder.applySetting(AvailableSettings.CONNECTION_PROVIDER_DISABLES_AUTOCOMMIT, "true");

            StandardServiceRegistry registry = registryBuilder.build();

            MetadataSources sources = new MetadataSources(registry);
            for (Class<?> entity : entityClasses()) {
                sources.addAnnotatedClass(entity);
            }

            SessionFactory factory = sources.buildMetadata().buildSessionFactory();
            log.info("Hibernate SessionFactory initialized against {}", AppPaths.databaseFile());
            return factory;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize Hibernate SessionFactory", e);
        }
    }

    private static Class<?>[] entityClasses() {
        return new Class<?>[] {
                AppSetting.class,
                AuditLog.class,
                Brand.class,
                Customer.class,
                Expense.class,
                GstSetting.class,
                InventoryTransaction.class,
                InvoiceSetting.class,
                Offer.class,
                Payment.class,
                Product.class,
                ProductBatch.class,
                Purchase.class,
                PurchaseItem.class,
                PurchaseReturn.class,
                Sale.class,
                SaleItem.class,
                SalesReturn.class,
                Supplier.class,
                User.class,
                WhatsAppCampaign.class,
                WhatsAppCampaignRecipient.class,
                WhatsAppContact.class,
                WhatsAppMessage.class,
                WhatsAppTemplate.class
        };
    }

    public static synchronized void shutdown() {
        if (sessionFactory != null) {
            sessionFactory.close();
            sessionFactory = null;
        }
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
    }
}
