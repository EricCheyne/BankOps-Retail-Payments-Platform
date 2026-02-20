package com.bankops.risk.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class RiskRepository {

    private final JdbcTemplate jdbcTemplate;

    public RiskRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insertSeen(UUID transferId, UUID fromAccountId, long amountCents) {
        String sql = """
                INSERT INTO transfer_risk_seen (id, transfer_id, from_account_id, amount_cents)
                VALUES (?, ?, ?, ?)
                """;
        jdbcTemplate.update(sql, UUID.randomUUID(), transferId, fromAccountId, amountCents);
    }

    public int countRecentFromAccount(UUID fromAccountId, int windowMinutes) {
        String sql = """
                SELECT count(*)
                FROM transfer_risk_seen
                WHERE from_account_id = ?
                  AND created_at >= now() - (interval '1 minute' * ?)
                """;
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, fromAccountId, windowMinutes);
        return count != null ? count : 0;
    }
}
