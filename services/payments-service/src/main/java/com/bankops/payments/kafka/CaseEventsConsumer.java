package com.bankops.payments.kafka;

import com.bankops.common.events.BankEventEnvelope;
import com.bankops.common.events.EventTypes;
import com.bankops.common.events.Topics;
import com.bankops.payments.core.PaymentsLifecycleService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class CaseEventsConsumer {

    private static final Logger log = LoggerFactory.getLogger(CaseEventsConsumer.class);

    private final PaymentsLifecycleService lifecycleService;
    private final ObjectMapper objectMapper;

    public CaseEventsConsumer(PaymentsLifecycleService lifecycleService, ObjectMapper objectMapper) {
        this.lifecycleService = lifecycleService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = Topics.BANKOPS_CASE_EVENTS, groupId = "bankops-payments-case-consumer")
    public void onMessage(String message) {
        log.debug("Received case event: {}", message);
        try {
            BankEventEnvelope envelope = objectMapper.readValue(message, BankEventEnvelope.class);

            if (EventTypes.CaseApproved.equals(envelope.eventType())) {
                handleCaseApproved(envelope);
            } else if (EventTypes.CaseRejected.equals(envelope.eventType())) {
                handleCaseRejected(envelope);
            } else {
                log.debug("Ignoring case event type: {}", envelope.eventType());
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to parse case event: {}", message, e);
        } catch (Exception e) {
            log.error("Error processing case event: {}", message, e);
        }
    }

    private void handleCaseApproved(BankEventEnvelope envelope) {
        JsonNode payload = envelope.payload();
        UUID transferId = UUID.fromString(payload.get("transferId").asText());
        UUID caseId = UUID.fromString(payload.get("caseId").asText());
        String decidedBy = payload.get("decidedBy").asText();

        lifecycleService.onCaseApproved(transferId, caseId, decidedBy);
    }

    private void handleCaseRejected(BankEventEnvelope envelope) {
        JsonNode payload = envelope.payload();
        UUID transferId = UUID.fromString(payload.get("transferId").asText());
        UUID caseId = UUID.fromString(payload.get("caseId").asText());
        String decidedBy = payload.get("decidedBy").asText();
        String justification = payload.has("justification") ? payload.get("justification").asText() : "";

        lifecycleService.onCaseRejected(transferId, caseId, decidedBy, justification);
    }
}
