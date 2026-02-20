package com.bankops.risk.core;

import com.bankops.risk.config.RiskRulesProperties;
import com.bankops.risk.repository.RiskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class RiskDecisionEngineTest {

    private RiskRepository riskRepository;
    private RiskRulesProperties properties;
    private RiskDecisionEngine engine;

    @BeforeEach
    void setUp() {
        riskRepository = Mockito.mock(RiskRepository.class);
        properties = new RiskRulesProperties();
        properties.setAmountHoldThresholdCents(50000);
        properties.setVelocityMaxTransfers(3);
        properties.setVelocityWindowMinutes(10);
        engine = new RiskDecisionEngine(riskRepository, properties);
    }

    @Test
    void shouldClearWhenUnderThresholdAndVelocity() {
        UUID transferId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        long amount = 10000;

        when(riskRepository.countRecentFromAccount(eq(accountId), anyInt())).thenReturn(1);

        RiskDecisionEngine.Decision decision = engine.computeDecision(transferId, accountId, amount);

        assertFalse(decision.hold());
        assertTrue(decision.reasons().isEmpty());
    }

    @Test
    void shouldHoldWhenOverAmountThreshold() {
        UUID transferId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        long amount = 50000;

        when(riskRepository.countRecentFromAccount(eq(accountId), anyInt())).thenReturn(1);

        RiskDecisionEngine.Decision decision = engine.computeDecision(transferId, accountId, amount);

        assertTrue(decision.hold());
        assertTrue(decision.reasons().contains("AMOUNT_THRESHOLD"));
    }

    @Test
    void shouldHoldWhenOverVelocityThreshold() {
        UUID transferId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        long amount = 10000;

        // Max is 3, so 4 should trigger HOLD
        when(riskRepository.countRecentFromAccount(eq(accountId), anyInt())).thenReturn(4);

        RiskDecisionEngine.Decision decision = engine.computeDecision(transferId, accountId, amount);

        assertTrue(decision.hold());
        assertTrue(decision.reasons().contains("VELOCITY"));
    }

    @Test
    void shouldHoldWithMultipleReasons() {
        UUID transferId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        long amount = 100000;

        when(riskRepository.countRecentFromAccount(eq(accountId), anyInt())).thenReturn(5);

        RiskDecisionEngine.Decision decision = engine.computeDecision(transferId, accountId, amount);

        assertTrue(decision.hold());
        assertTrue(decision.reasons().contains("AMOUNT_THRESHOLD"));
        assertTrue(decision.reasons().contains("VELOCITY"));
        assertEquals(2, decision.reasons().size());
    }
}
