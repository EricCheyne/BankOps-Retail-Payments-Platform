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
public class TransferEventsConsumer {

    private static final Logger log = LoggerFactory.getLogger(TransferEventsConsumer.class);

    private final PaymentsLifecycleService lifecycleService;
    private final ObjectMapper objectMapper;

    public TransferEventsConsumer(PaymentsLifecycleService lifecycleService, ObjectMapper objectMapper) {
        this.lifecycleService = lifecycleService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = Topics.BANKOPS_TRANSFER_EVENTS, groupId = "bankops-payments-transfer-consumer")
    public void onMessage(String message) {
        log.debug("Received transfer event: {}", message);
        try {
            BankEventEnvelope envelope = objectMapper.readValue(message, BankEventEnvelope.class);

            if (EventTypes.TransferRiskHeld.equals(envelope.eventType())) {
                handleRiskHeld(envelope);
            } else if (EventTypes.TransferRiskCleared.equals(envelope.eventType())) {
                handleRiskCleared(envelope);
            } else {
                log.debug("Ignoring transfer event type: {}", envelope.eventType());
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to parse transfer event: {}", message, e);
        } catch (Exception e) {
            log.error("Error processing transfer event: {}", message, e);
        }
    }

    private void handleRiskHeld(BankEventEnvelope envelope) {
        JsonNode payload = envelope.payload();
        UUID transferId = UUID.fromString(payload.get("transferId").asText());
        
        List<String> reasons = new ArrayList<>();
        if (payload.has("reasons") && payload.get("reasons").isArray()) {
            for (JsonNode reason : payload.get("reasons")) {
                reasons.add(reason.asText());
            }
        }

        lifecycleService.onRiskHeld(transferId, reasons);
    }

    private void handleRiskCleared(BankEventEnvelope envelope) {
        JsonNode payload = envelope.payload();
        UUID transferId = UUID.fromString(payload.get("transferId").asText());

        lifecycleService.onRiskCleared(transferId);
    }
}
