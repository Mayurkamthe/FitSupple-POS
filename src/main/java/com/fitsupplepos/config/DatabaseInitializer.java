package com.fitsupplepos.config;

import com.fitsupplepos.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs once on application startup (see Main#init): creates the on-disk directory
 * layout, opens/creates the SQLite database and brings its schema up to date via
 * Hibernate's hbm2ddl, then seeds the default OWNER account if this is a first run.
 */
public final class DatabaseInitializer {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);

    private DatabaseInitializer() {}

    public static void initialize() throws Exception {
        AppPaths.ensureDirectoriesExist();
        log.info("Application data directory: {}", AppPaths.dataDir());

        // Touch the SessionFactory now (rather than lazily) so any schema/connection
        // problem surfaces as a clear startup error instead of failing on first click.
        HibernateConfig.getSessionFactory();

        new AuthService().ensureDefaultOwnerExists();
    }
}
