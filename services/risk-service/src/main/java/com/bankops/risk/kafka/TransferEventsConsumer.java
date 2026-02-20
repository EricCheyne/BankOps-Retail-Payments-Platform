package com.bankops.risk.kafka;

import com.bankops.common.events.BankEventEnvelope;
import com.bankops.common.events.EventTypes;
import com.bankops.common.events.Topics;
import com.bankops.risk.core.RiskDecisionEngine;
import com.bankops.risk.repository.RiskRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
public class TransferEventsConsumer {

    private static final Logger log = LoggerFactory.getLogger(TransferEventsConsumer.class);

    private final ObjectMapper objectMapper;
    private final RiskRepository riskRepository;
    private final RiskDecisionEngine riskDecisionEngine;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public TransferEventsConsumer(ObjectMapper objectMapper,
                                  RiskRepository riskRepository,
                                  RiskDecisionEngine riskDecisionEngine,
                                  KafkaTemplate<String, String> kafkaTemplate) {
        this.objectMapper = objectMapper;
        this.riskRepository = riskRepository;
        this.riskDecisionEngine = riskDecisionEngine;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = Topics.BANKOPS_TRANSFER_EVENTS, groupId = "bankops-risk-service")
    @Transactional
    public void onMessage(String message, @Headers Map<String, Object> headers) {
        try {
            BankEventEnvelope envelope = objectMapper.readValue(message, BankEventEnvelope.class);

            if (!EventTypes.TransferRequested.equals(envelope.eventType())) {
                return;
            }

            JsonNode payload = envelope.payload();
            UUID transferId = UUID.fromString(payload.get("transferId").asText());
            UUID fromAccountId = UUID.fromString(payload.get("fromAccountId").asText());
            long amountCents = payload.get("amountCents").asLong();

            log.info("Processing TransferRequested: transferId={}, fromAccountId={}, amountCents={}",
                    transferId, fromAccountId, amountCents);

            // 1. Record in velocity store
            riskRepository.insertSeen(transferId, fromAccountId, amountCents);

            // 2. Compute decision
            RiskDecisionEngine.Decision decision = riskDecisionEngine.computeDecision(transferId, fromAccountId, amountCents);

            // 3. Publish outcome event
            publishOutcome(transferId, decision);

            log.info("Risk decision for transfer {}: hold={}, reasons={}", transferId, decision.hold(), decision.reasons());

        } catch (Exception e) {
            log.error("Failed to process Kafka message: {}", message, e);
            // In a real system, we might want to throw e to retry or use a DLQ
        }
    }

    private void publishOutcome(UUID transferId, RiskDecisionEngine.Decision decision) throws Exception {
        String eventType = decision.hold() ? EventTypes.TransferRiskHeld : EventTypes.TransferRiskCleared;
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("transferId", transferId.toString());
        payload.put("decision", decision.hold() ? "HOLD" : "CLEAR");
        payload.set("reasons", objectMapper.valueToTree(decision.reasons()));
        payload.put("caseRequired", decision.hold());

        BankEventEnvelope outcomeEnvelope = new BankEventEnvelope(
                eventType,
                eventId.toString(),
                now,
                transferId.toString(),
                payload
        );

        String outcomeMessage = objectMapper.writeValueAsString(outcomeEnvelope);
        String key = transferId.toString();

        ProducerRecord<String, String> record = new ProducerRecord<>(Topics.BANKOPS_TRANSFER_EVENTS, key, outcomeMessage);
        record.headers().add(new RecordHeader("event_type", eventType.getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader("correlation_id", transferId.toString().getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader("event_id", eventId.toString().getBytes(StandardCharsets.UTF_8)));

        kafkaTemplate.send(record).get();
    }
}
