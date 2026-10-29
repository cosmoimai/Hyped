package com.hyped.app.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.hyped.app.account.application.AccountService;
import com.hyped.app.account.application.port.out.AccountStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class AccountDeletionMonitoringTest {
    private static final Instant NOW = Instant.parse("2026-10-26T10:00:00Z");
    private final AccountStore store = mock(AccountStore.class);
    private final Logger logger = (Logger) LoggerFactory.getLogger(AccountService.class);
    private final ListAppender<ILoggingEvent> events = new ListAppender<>();
    private AccountService service;
    private Level originalLevel;

    @BeforeEach
    void setup() {
        originalLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        events.start();
        logger.addAppender(events);
        var beans = new StaticListableBeanFactory();
        service = new AccountService(store, null, null, null, null, null,
                beans.getBeanProvider(com.hyped.app.account.application.port.out.FirebaseAccountAdmin.class),
                null, Clock.fixed(NOW, ZoneOffset.UTC), false);
        when(store.due(any())).thenReturn(List.of());
    }

    @AfterEach
    void cleanup() {
        logger.detachAppender(events);
        logger.setLevel(originalLevel);
        events.stop();
    }

    @ParameterizedTest
    @CsvSource({"0,OK,INFO", "43199,OK,INFO", "43200,BACKLOG,WARN", "86399,BACKLOG,WARN",
            "86400,OVERDUE,ERROR", "90000,OVERDUE,ERROR", "-60,OK,INFO"})
    void reportsExactWarningAndDeadlineBoundaries(long age, String status, String level) {
        when(store.backlog()).thenReturn(new AccountStore.Backlog(2, NOW.minusSeconds(age)));
        service.tick();
        assertThat(events.list).hasSize(1);
        assertThat(events.list.getFirst().getLevel().toString()).isEqualTo(level);
        assertThat(events.list.getFirst().getFormattedMessage()).contains(
                "code=ACCOUNT_DELETION_HEALTH status=" + status,
                "pending=2 oldest_age_seconds=" + Math.max(0, age));
    }

    @Test
    void emptyQueueProducesSuccessfulHeartbeatWithoutOldestTimestamp() {
        when(store.backlog()).thenReturn(new AccountStore.Backlog(0, null));
        service.tick();
        assertThat(events.list.getFirst().getFormattedMessage())
                .endsWith("status=OK pending=0 oldest_age_seconds=0");
    }

    @Test
    void monitorFailureIsSanitizedAndDoesNotFailCompletedCleanup() {
        when(store.backlog()).thenThrow(new IllegalStateException("private token and user data"));
        service.tick();
        verify(store).due(NOW);
        assertThat(events.list.getFirst().getFormattedMessage())
                .isEqualTo("Account deletion cleanup code=ACCOUNT_DELETION_HEALTH status=UNAVAILABLE");
        assertThat(events.list.getFirst().getThrowableProxy()).isNull();
    }

    @Test
    void failedCleanupStillReportsBacklogAndPreservesOriginalFailure() {
        when(store.due(any())).thenThrow(new IllegalStateException("cleanup unavailable"));
        when(store.backlog()).thenReturn(new AccountStore.Backlog(1, NOW.minusSeconds(43200)));
        assertThatThrownBy(service::tick).isInstanceOf(IllegalStateException.class);
        assertThat(events.list.getFirst().getFormattedMessage()).contains("status=BACKLOG pending=1");
    }
}
