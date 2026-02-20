package com.bankops.cases.repository;

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
import java.util.Optional;
import java.util.UUID;

@Repository
public class OpsCaseRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public OpsCaseRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void insertCase(UUID id, UUID transferId, String status, String createdBy, String transferRequestedBy, String reasonsJson) {
        String sql = """
                INSERT INTO ops_case (id, transfer_id, status, created_by, transfer_requested_by, reasons_json)
                VALUES (?, ?, ?, ?, ?, ?::jsonb)
                """;
        jdbcTemplate.update(sql, id, transferId, status, createdBy, transferRequestedBy, reasonsJson);
    }

    public Optional<OpsCase> findById(UUID caseId) {
        String sql = "SELECT * FROM ops_case WHERE id = ?";
        return jdbcTemplate.query(sql, this::mapRowToCase, caseId).stream().findFirst();
    }

    public List<OpsCase> listByStatus(String status, int limit) {
        String sql = "SELECT * FROM ops_case WHERE status = ? ORDER BY created_at DESC LIMIT ?";
        return jdbcTemplate.query(sql, this::mapRowToCase, status, limit);
    }

    public void updateStatus(UUID caseId, String newStatus) {
        String sql = "UPDATE ops_case SET status = ? WHERE id = ?";
        jdbcTemplate.update(sql, newStatus, caseId);
    }

    private OpsCase mapRowToCase(ResultSet rs, int rowNum) throws SQLException {
        try {
            return new OpsCase(
                    rs.getObject("id", UUID.class),
                    toInstant(rs.getTimestamp("created_at")),
                    rs.getObject("transfer_id", UUID.class),
                    rs.getString("status"),
                    rs.getString("created_by"),
                    rs.getString("transfer_requested_by"),
                    objectMapper.readTree(rs.getString("reasons_json"))
            );
        } catch (IOException e) {
            throw new SQLException("Failed to parse reasons_json", e);
        }
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp != null ? timestamp.toInstant() : null;
    }

    public record OpsCase(
            UUID id,
            Instant createdAt,
            UUID transferId,
            String status,
            String createdBy,
            String transferRequestedBy,
            JsonNode reasons
    ) {}
}
