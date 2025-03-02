package org.camunda.bpm.getstarted.config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class DatabaseConfig {
    private static final Logger logger
            = LoggerFactory.getLogger(DatabaseConfig.class);

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        logger.info("Creating jdbcTemplate with datasource: {}", dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        logger.info("Created jdbcTemplate : {}", jdbcTemplate);
        return jdbcTemplate;
    }
}
