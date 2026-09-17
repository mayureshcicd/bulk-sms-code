package com.message.approval;

import com.message.approval.service.TrialService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TrialServiceTest {
    @Test void disabledDoesNotReadOrInitializeTrialStorage() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        TrialService service = new TrialService(jdbc, manager);
        ReflectionTestUtils.setField(service, "checkDays", false);
        service.init();
        assertFalse(service.isExpired());
        verifyNoInteractions(jdbc, manager);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "TEST_TRIAL_DB_URL", matches = ".+")
    void sharedStartSurvivesRestartAndExpiresAtThirtyDays() {
        var ds = new DriverManagerDataSource(System.getenv("TEST_TRIAL_DB_URL"), "lenovo", "");
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        var manager = new DataSourceTransactionManager(ds);
        TrialService first = new TrialService(jdbc, manager);
        ReflectionTestUtils.setField(first, "checkDays", true);
        first.init();
        jdbc.update("UPDATE application_trial SET started_at = CURRENT_TIMESTAMP WHERE id = 1");
        assertFalse(first.isExpired());
        Object start = jdbc.queryForObject("SELECT started_at FROM application_trial WHERE id = 1", Object.class);
        TrialService second = new TrialService(jdbc, manager);
        ReflectionTestUtils.setField(second, "checkDays", true);
        second.init();
        assertEquals(start, jdbc.queryForObject("SELECT started_at FROM application_trial WHERE id = 1", Object.class));
        jdbc.update("UPDATE application_trial SET started_at = CURRENT_TIMESTAMP - INTERVAL '719 hours 59 minutes' WHERE id = 1");
        assertFalse(first.isExpired()); assertFalse(second.isExpired());
        jdbc.update("UPDATE application_trial SET started_at = CURRENT_TIMESTAMP - INTERVAL '720 hours' WHERE id = 1");
        assertTrue(first.isExpired()); assertTrue(second.isExpired());
        ReflectionTestUtils.setField(second, "checkDays", false);
        assertFalse(second.isExpired());
        ReflectionTestUtils.setField(second, "checkDays", true);
        second.init();
        assertTrue(second.isExpired());
    }
}
