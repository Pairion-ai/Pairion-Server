package com.pairion.memory.config;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Spring configuration for the memory module's dedicated SQLite datasource.
 *
 * <p>Configures a named {@code memoryDataSource} HikariCP pool for SQLite in single-connection
 * mode, along with a matching {@link EntityManagerFactory} and {@link PlatformTransactionManager}.
 * The database file is stored at {@code ${PAIRION_HOME:${user.home}/.pairion}/memory/pairion.db}.
 *
 * <p>This is a secondary datasource and does not conflict with Spring Boot auto-configuration
 * because it uses explicitly named beans. No {@code @Primary} annotation is used.
 */
@Configuration
@EnableJpaRepositories(
        basePackages = "com.pairion.memory.repository",
        entityManagerFactoryRef = "memoryEntityManagerFactory",
        transactionManagerRef = "memoryTransactionManager")
public class MemoryDataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(MemoryDataSourceConfig.class);

    @Value("${PAIRION_HOME:${user.home}/.pairion}")
    private String pairionHome;

    /**
     * Creates and configures the SQLite datasource for the memory module.
     *
     * <p>The database file path is derived from {@code PAIRION_HOME}. The parent directory is
     * created automatically if it does not exist. On any {@link IOException} during directory
     * creation, a warning is logged and startup continues — the datasource may fail later if the
     * path is genuinely inaccessible.
     *
     * @return the configured HikariCP datasource
     */
    @Bean(name = "memoryDataSource")
    public DataSource memoryDataSource() {
        Path dbDir = Paths.get(pairionHome, "memory");
        try {
            Files.createDirectories(dbDir);
        } catch (IOException e) {
            log.warn("memory.db.dir.create.failed: path={}, error={}", dbDir, e.getMessage());
        }

        String jdbcUrl = "jdbc:sqlite:" + dbDir.resolve("pairion.db");

        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(jdbcUrl);
        ds.setDriverClassName("org.sqlite.JDBC");
        ds.setMaximumPoolSize(1);
        ds.setConnectionTimeout(30000);
        ds.setPoolName("memory-pool");
        return ds;
    }

    /**
     * Creates the JPA {@link EntityManagerFactory} for the memory module's SQLite datasource.
     *
     * <p>Scans {@code com.pairion.memory.entity} for JPA entities. Uses the SQLite community
     * dialect and {@code hbm2ddl.auto=update} for schema management.
     *
     * @param memoryDataSource the memory datasource bean
     * @return the configured entity manager factory
     */
    @Bean(name = "memoryEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean memoryEntityManagerFactory(
            DataSource memoryDataSource) {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(memoryDataSource);
        em.setPackagesToScan("com.pairion.memory.entity");
        em.setJpaVendorAdapter(new HibernateJpaVendorAdapter());

        Properties jpaProperties = new Properties();
        jpaProperties.setProperty(
                "hibernate.dialect", "org.hibernate.community.dialect.SQLiteDialect");
        jpaProperties.setProperty("hibernate.hbm2ddl.auto", "update");
        jpaProperties.setProperty("hibernate.show_sql", "false");
        em.setJpaProperties(jpaProperties);

        return em;
    }

    /**
     * Creates the {@link PlatformTransactionManager} for the memory module's entity manager.
     *
     * @param memoryEntityManagerFactory the memory entity manager factory bean
     * @return the configured transaction manager
     */
    @Bean(name = "memoryTransactionManager")
    public PlatformTransactionManager memoryTransactionManager(
            EntityManagerFactory memoryEntityManagerFactory) {
        return new JpaTransactionManager(memoryEntityManagerFactory);
    }
}
