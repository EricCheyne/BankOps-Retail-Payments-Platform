package com.bankops.payments.repository;

import com.bankops.payments.api.TransferResponse;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

@Repository
public class TransferRepository {

    private final JdbcTemplate jdbcTemplate;

    public TransferRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<TransferResponse> rowMapper = (rs, rowNum) -> new TransferResponse(
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
                INSERT INTO transfer (id, created_by, from_account_id, to_account_id, amount_cents, currency, memo, status, risk_status, idempotency_key)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.update(sql, id, createdBy, fromAccountId, toAccountId, amountCents, currency, memo, status, riskStatus, idempotencyKey);
    }

    public Optional<TransferResponse> findByIdempotencyKey(String idempotencyKey) {
        String sql = "SELECT * FROM transfer WHERE idempotency_key = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, rowMapper, idempotencyKey));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<TransferResponse> findById(UUID id) {
        String sql = "SELECT * FROM transfer WHERE id = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, rowMapper, id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}
