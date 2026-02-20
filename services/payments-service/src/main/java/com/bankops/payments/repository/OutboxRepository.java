package com.bankops.payments.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class OutboxRepository {

    private final JdbcTemplate jdbcTemplate;

    public OutboxRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insertOutboxEvent(UUID id, String aggregateType, UUID aggregateId,
                                  String eventType, String payloadJson) {
        String sql = """
                INSERT INTO outbox_event (id, aggregate_type, aggregate_id, event_type, payload_json, published_at)
                VALUES (?, ?, ?, ?, ?::jsonb, null)
                """;
        jdbcTemplate.update(sql, id, aggregateType, aggregateId, eventType, payloadJson);
    }
}
