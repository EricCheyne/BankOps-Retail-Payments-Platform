package com.bankops.payments.repository;

import com.bankops.payments.api.TransferResponse;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class TransferRepository {

    private final JdbcTemplate jdbcTemplate;

    public TransferRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record TransferRow(
            UUID id,
            String createdBy,
            UUID fromAccountId,
            UUID toAccountId,
            long amountCents,
            String currency,
            String memo,
            String status,
            String riskStatus,
            String idempotencyKey,
            Instant createdAt,
            Instant updatedAt,
            String decisionReason,
            UUID ledgerEntryId
    ) {}

    private final RowMapper<TransferRow> rowMapper = (rs, rowNum) -> new TransferRow(
            UUID.fromString(rs.getString("id")),
            rs.getString("created_by"),
            UUID.fromString(rs.getString("from_account_id")),
            UUID.fromString(rs.getString("to_account_id")),
            rs.getLong("amount_cents"),
            rs.getString("currency"),
            rs.getString("memo"),
            rs.getString("status"),
            rs.getString("risk_status"),
            rs.getString("idempotency_key"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant(),
            rs.getString("decision_reason"),
            rs.getString("ledger_entry_id") != null ? UUID.fromString(rs.getString("ledger_entry_id")) : null
    );

    private final RowMapper<TransferResponse> responseRowMapper = (rs, rowNum) -> new TransferResponse(
            UUID.fromString(rs.getString("id")),
            rs.getString("status"),
            rs.getString("risk_status"),
            UUID.fromString(rs.getString("from_account_id")),
            UUID.fromString(rs.getString("to_account_id")),
            rs.getLong("amount_cents"),
            rs.getString("currency"),
            rs.getString("memo"),
            rs.getTimestamp("created_at").toInstant()
    );

    public void insertTransfer(UUID id, String createdBy, UUID fromAccountId, UUID toAccountId,
                               long amountCents, String currency, String memo,
                               String status, String riskStatus, String idempotencyKey) {
        String sql = """
                INSERT INTO transfer (id, created_by, from_account_id, to_account_id, amount_cents, currency, memo, status, risk_status, idempotency_key, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                """;
        jdbcTemplate.update(sql, id, createdBy, fromAccountId, toAccountId, amountCents, currency, memo, status, riskStatus, idempotencyKey);
    }

    public Optional<TransferResponse> findByIdempotencyKey(String idempotencyKey) {
        String sql = "SELECT * FROM transfer WHERE idempotency_key = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, responseRowMapper, idempotencyKey));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<TransferRow> findById(UUID id) {
        String sql = "SELECT * FROM transfer WHERE id = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, rowMapper, id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<TransferResponse> findResponseById(UUID id) {
        String sql = "SELECT * FROM transfer WHERE id = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, responseRowMapper, id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public void updateRiskStatus(UUID id, String riskStatus) {
        String sql = "UPDATE transfer SET risk_status = ?, updated_at = now() WHERE id = ?";
        jdbcTemplate.update(sql, riskStatus, id);
    }

    public void updateStatus(UUID id, String status, String decisionReason) {
        String sql = "UPDATE transfer SET status = ?, decision_reason = ?, updated_at = now() WHERE id = ?";
        jdbcTemplate.update(sql, status, decisionReason, id);
    }

    public void setLedgerEntryId(UUID id, UUID ledgerEntryId) {
        String sql = "UPDATE transfer SET ledger_entry_id = ?, updated_at = now() WHERE id = ?";
        jdbcTemplate.update(sql, ledgerEntryId, id);
    }
}
