package com.mehmetserin.banking.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared Postgres for all integration tests.
 * Started once in a static block (no {@code @Container} lifecycle) so JUnit
 * does not stop/restart the database between IT classes while Spring still
 * reuses a cached application context pointed at the old JDBC URL.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class PostgresIntegrationSupport {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
        POSTGRES.start();
    }
}
