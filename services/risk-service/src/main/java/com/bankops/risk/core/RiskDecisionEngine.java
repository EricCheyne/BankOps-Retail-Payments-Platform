package com.bankops.risk.core;

import com.bankops.risk.config.RiskRulesProperties;
import com.bankops.risk.repository.RiskRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class RiskDecisionEngine {

    private final RiskRepository riskRepository;
    private final RiskRulesProperties riskRulesProperties;

    public RiskDecisionEngine(RiskRepository riskRepository, RiskRulesProperties riskRulesProperties) {
        this.riskRepository = riskRepository;
        this.riskRulesProperties = riskRulesProperties;
    }

    public record Decision(boolean hold, List<String> reasons) {}

    public Decision computeDecision(UUID transferId, UUID fromAccountId, long amountCents) {
        List<String> reasons = new ArrayList<>();

        // Rule 1: Amount threshold
        if (amountCents >= riskRulesProperties.getAmountHoldThresholdCents()) {
            reasons.add("AMOUNT_THRESHOLD");
        }

        // Rule 2: Velocity
        int recentCount = riskRepository.countRecentFromAccount(fromAccountId, riskRulesProperties.getVelocityWindowMinutes());
        // recentCount already includes the current transfer because it was just inserted
        if (recentCount > riskRulesProperties.getVelocityMaxTransfers()) {
            reasons.add("VELOCITY");
        }

        boolean hold = !reasons.isEmpty();
        return new Decision(hold, reasons);
    }
}
