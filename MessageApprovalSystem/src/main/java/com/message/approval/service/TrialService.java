package com.message.approval.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class TrialService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    @Value("${trial.check-days:true}")
    private boolean checkDays;

    public TrialService(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(manager);
    }

    @PostConstruct
    public void init() {
        if (!checkDays) return;
        transaction.executeWithoutResult(status -> {
            // Serialize first startup across both applications, including schema creation.
            jdbc.execute("SELECT pg_advisory_xact_lock(724193061)");
            jdbc.execute("CREATE TABLE IF NOT EXISTS application_trial (id INTEGER PRIMARY KEY CHECK (id = 1), started_at TIMESTAMPTZ NOT NULL)");
            jdbc.update("INSERT INTO application_trial (id, started_at) VALUES (1, CURRENT_TIMESTAMP) ON CONFLICT (id) DO NOTHING");
        });
    }

    public boolean isExpired() {
        if (!checkDays) return false;
        // Use the database clock so different application hosts have the same deadline.
        Boolean expired = jdbc.queryForObject(
                "SELECT CURRENT_TIMESTAMP >= started_at + INTERVAL '720 hours' FROM application_trial WHERE id = 1",
                Boolean.class);
        return !Boolean.FALSE.equals(expired);
    }
}
