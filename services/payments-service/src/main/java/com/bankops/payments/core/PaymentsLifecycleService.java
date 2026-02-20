package com.bankops.payments.core;

import com.bankops.common.events.EventTypes;
import com.bankops.payments.ledger.LedgerClient;
import com.bankops.payments.repository.OutboxRepository;
import com.bankops.payments.repository.TransferRepository;
import com.bankops.payments.repository.TransferRepository.TransferRow;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentsLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(PaymentsLifecycleService.class);

    private final TransferRepository transferRepository;
    private final OutboxRepository outboxRepository;
    private final LedgerClient ledgerClient;
    private final ObjectMapper objectMapper;

    public PaymentsLifecycleService(TransferRepository transferRepository,
                                    OutboxRepository outboxRepository,
                                    LedgerClient ledgerClient,
                                    ObjectMapper objectMapper) {
        this.transferRepository = transferRepository;
        this.outboxRepository = outboxRepository;
        this.ledgerClient = ledgerClient;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void onRiskHeld(UUID transferId, List<String> reasons) {
        log.info("Handling risk held for transfer {}. Reasons: {}", transferId, reasons);
        transferRepository.updateRiskStatus(transferId, "HELD");
        transferRepository.updateStatus(transferId, "HELD", "RISK_HOLD");
    }

    @Transactional
    public void onRiskCleared(UUID transferId) {
        log.info("Handling risk cleared for transfer {}", transferId);
        transferRepository.updateRiskStatus(transferId, "CLEARED");
        attemptPosting(transferId);
    }

    @Transactional
    public void onCaseApproved(UUID transferId, UUID caseId, String decidedBy) {
        log.info("Handling case approved for transfer {}. Case ID: {}, Decided By: {}", transferId, caseId, decidedBy);
        // Status might already be HELD, we'll transition to POSTING in attemptPosting
        attemptPosting(transferId);
    }

    @Transactional
    public void onCaseRejected(UUID transferId, UUID caseId, String decidedBy, String justification) {
        log.info("Handling case rejected for transfer {}. Case ID: {}, Decided By: {}, Justification: {}",
                transferId, caseId, decidedBy, justification);

        transferRepository.updateStatus(transferId, "REJECTED", "CASE_REJECTED");

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("transferId", transferId.toString());
        payload.put("caseId", caseId.toString());
        payload.put("decidedBy", decidedBy);
        payload.put("justification", justification);

        outboxRepository.insertOutboxEvent(
                UUID.randomUUID(),
                "Transfer",
                transferId,
                "TransferRejected",
                payload.toString()
        );
    }

    public void attemptPosting(UUID transferId) {
        // TX1: Set status to POSTING
        TransferRow transfer = prepareForPosting(transferId);
        if (transfer == null) return;

        try {
            // HTTP Call (Outside DB transaction)
            LedgerClient.LedgerPostResult result = ledgerClient.postTransferToLedger(transfer);

            // TX2: Set final status based on result
            finalizePostingSuccess(transferId, result);
        } catch (Exception e) {
            log.error("Ledger posting failed for transfer {}: {}", transferId, e.getMessage());
            // TX2: Set status to POSTING_FAILED
            finalizePostingFailure(transferId, e.getMessage());
        }
    }

    @Transactional
    protected TransferRow prepareForPosting(UUID transferId) {
        TransferRow transfer = transferRepository.findById(transferId).orElse(null);
        if (transfer == null) {
            log.error("Transfer {} not found for posting", transferId);
            return null;
        }

        // Only post if it's currently HELD and risk is CLEARED, or if it's already PENDING/POSTING (retry)
        // Actually, the requirements say "onRiskCleared -> attemptPosting" and "onCaseApproved -> attemptPosting"
        // Let's check risk status if it's CLEARED.
        if (!"CLEARED".equals(transfer.riskStatus())) {
            log.info("Transfer {} is still risk-held, skipping posting until cleared or approved", transferId);
            return null;
        }

        transferRepository.updateStatus(transferId, "POSTING", null);
        return transferRepository.findById(transferId).orElseThrow();
    }

    @Transactional
    protected void finalizePostingSuccess(UUID transferId, LedgerClient.LedgerPostResult result) {
        transferRepository.setLedgerEntryId(transferId, result.entryId());
        transferRepository.updateStatus(transferId, "COMPLETED", null);

        // TransferPosted event
        ObjectNode postedPayload = objectMapper.createObjectNode();
        postedPayload.put("transferId", transferId.toString());
        postedPayload.put("ledgerEntryId", result.entryId().toString());
        outboxRepository.insertOutboxEvent(
                UUID.randomUUID(),
                "Transfer",
                transferId,
                EventTypes.TransferPosted,
                postedPayload.toString()
        );

        // TransferCompleted event
        ObjectNode completedPayload = objectMapper.createObjectNode();
        completedPayload.put("transferId", transferId.toString());
        completedPayload.put("completedAt", Instant.now().toString());
        outboxRepository.insertOutboxEvent(
                UUID.randomUUID(),
                "Transfer",
                transferId,
                EventTypes.TransferCompleted,
                completedPayload.toString()
        );

        log.info("Transfer {} successfully posted to ledger with entry {}", transferId, result.entryId());
    }

    @Transactional
    protected void finalizePostingFailure(UUID transferId, String errorMessage) {
        transferRepository.updateStatus(transferId, "POSTING_FAILED", "LEDGER_POST_FAILED");
        log.warn("Transfer {} marked as POSTING_FAILED: {}", transferId, errorMessage);
    }
}
