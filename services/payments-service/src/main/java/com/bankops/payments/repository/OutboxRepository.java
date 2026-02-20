package com.bankops.payments.repository;

import com.bankops.payments.outbox.OutboxEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class OutboxRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public OutboxRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void insertOutboxEvent(UUID id, String aggregateType, UUID aggregateId,
                                  String eventType, String payloadJson) {
        String sql = """
                INSERT INTO outbox_event (id, aggregate_type, aggregate_id, event_type, payload_json, published_at)
                VALUES (?, ?, ?, ?, ?::jsonb, null)
                """;
        jdbcTemplate.update(sql, id, aggregateType, aggregateId, eventType, payloadJson);
    }

    public List<OutboxEvent> fetchBatchForPublish(int batchSize) {
        String sql = """
                SELECT id, created_at, aggregate_type, aggregate_id, event_type, payload_json, published_at
                FROM outbox_event
                WHERE published_at IS NULL
                ORDER BY created_at
                FOR UPDATE SKIP LOCKED
                LIMIT ?
                """;
        return jdbcTemplate.query(sql, this::mapRowToOutboxEvent, batchSize);
    }

    public void markPublished(UUID id) {
        String sql = "UPDATE outbox_event SET published_at = now() WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }

    private OutboxEvent mapRowToOutboxEvent(ResultSet rs, int rowNum) throws SQLException {
        try {
            return new OutboxEvent(
                    rs.getObject("id", UUID.class),
                    toInstant(rs.getTimestamp("created_at")),
                    rs.getString("aggregate_type"),
                    rs.getObject("aggregate_id", UUID.class),
                    rs.getString("event_type"),
                    objectMapper.readTree(rs.getString("payload_json")),
                    toInstant(rs.getTimestamp("published_at"))
            );
        } catch (IOException e) {
            throw new SQLException("Failed to parse payload_json", e);
        }
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp != null ? timestamp.toInstant() : null;
    }
}
