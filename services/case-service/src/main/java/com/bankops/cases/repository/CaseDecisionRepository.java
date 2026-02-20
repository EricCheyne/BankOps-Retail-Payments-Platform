package com.bankops.cases.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class CaseDecisionRepository {

    private final JdbcTemplate jdbcTemplate;

    public CaseDecisionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insertDecision(UUID id, UUID caseId, String decision, String decisionBy, String justification) {
        String sql = """
                INSERT INTO case_decision (id, case_id, decision, decision_by, justification)
                VALUES (?, ?, ?, ?, ?)
                """;
        jdbcTemplate.update(sql, id, caseId, decision, decisionBy, justification);
    }
}
