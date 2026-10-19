package org.camunda.bpm.getstarted.config;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.camunda.bpm.engine.impl.cfg.AbstractProcessEnginePlugin;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.db.sql.DbSqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Makes job acquisition use SELECT ... FOR UPDATE SKIP LOCKED on PostgreSQL instead of Camunda's
 * select-then-optimistic-lock, so nodes in a cluster stop competing for the same jobs.
 * Enabled with job-executor.acquisition-skip-locked=true (JOB_EXECUTOR_SKIP_LOCKED).
 * Design and side-effect analysis: docs/skip-locked-job-acquisition.md; benchmarks: bench/RESULTS.md.
 */
@Component
@ConditionalOnProperty(name = "job-executor.acquisition-skip-locked", havingValue = "true")
public class SkipLockedJobAcquisitionPlugin extends AbstractProcessEnginePlugin {
    private static final Logger logger
            = LoggerFactory.getLogger(SkipLockedJobAcquisitionPlugin.class);

    static final String ENGINE_STATEMENT = "selectNextJobsToExecute";
    static final String SKIP_LOCKED_STATEMENT = "selectNextJobsToExecuteSkipLocked";
    private static final String MAPPER_RESOURCE = "mappings/SkipLockedJobAcquisition.xml";

    @Override
    public void postInit(ProcessEngineConfigurationImpl configuration) {
        if (!DbSqlSessionFactory.POSTGRES.equals(configuration.getDatabaseType())) {
            logger.warn("SKIP LOCKED job acquisition is only implemented for PostgreSQL, database is {}; keeping default acquisition",
                    configuration.getDatabaseType());
            return;
        }

        // The SqlSessionFactory is built in init(), so the engine's mappings, fragments and
        // ${prefix}/${limitAfter} variables already exist and our mapper can reference them
        Configuration myBatis = configuration.getSqlSessionFactory().getConfiguration();
        try (InputStream mapper = getClass().getClassLoader().getResourceAsStream(MAPPER_RESOURCE)) {
            new XMLMapperBuilder(mapper, myBatis, MAPPER_RESOURCE, myBatis.getSqlFragments()).parse();
        } catch (IOException e) {
            throw new IllegalStateException("Could not load " + MAPPER_RESOURCE, e);
        }

        // Same mechanism Camunda uses for its own *_postgres statement variants. Copy the map so the
        // engine's static per-database defaults are left untouched.
        DbSqlSessionFactory dbSqlSessionFactory = configuration.getDbSqlSessionFactory();
        Map<String, String> mappings = new HashMap<>();
        if (dbSqlSessionFactory.getStatementMappings() != null) {
            mappings.putAll(dbSqlSessionFactory.getStatementMappings());
        }
        mappings.put(ENGINE_STATEMENT, SKIP_LOCKED_STATEMENT);
        dbSqlSessionFactory.setStatementMappings(mappings);

        logger.info("Job acquisition uses FOR UPDATE SKIP LOCKED ({} -> {})", ENGINE_STATEMENT, SKIP_LOCKED_STATEMENT);
    }
}
