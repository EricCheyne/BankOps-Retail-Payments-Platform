package com.bankops.cases.kafka;

import com.bankops.cases.repository.OpsCaseRepository;
import com.bankops.common.events.BankEventEnvelope;
import com.bankops.common.events.EventTypes;
import com.bankops.common.events.Topics;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Component
public class TransferEventsConsumer {

    private static final Logger log = LoggerFactory.getLogger(TransferEventsConsumer.class);

    private final ObjectMapper objectMapper;
    private final OpsCaseRepository opsCaseRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public TransferEventsConsumer(ObjectMapper objectMapper,
                                  OpsCaseRepository opsCaseRepository,
                                  KafkaTemplate<String, String> kafkaTemplate) {
        this.objectMapper = objectMapper;
        this.opsCaseRepository = opsCaseRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = Topics.BANKOPS_TRANSFER_EVENTS, groupId = "bankops-case-service")
    @Transactional
    public void onMessage(String message) {
        try {
            BankEventEnvelope envelope = objectMapper.readValue(message, BankEventEnvelope.class);

            if (!EventTypes.TransferRiskHeld.equals(envelope.eventType())) {
                return;
            }

            JsonNode payload = envelope.payload();
            String transferId = payload.get("transferId").asText();
            JsonNode reasons = payload.get("reasons");
            boolean caseRequired = payload.has("caseRequired") && payload.get("caseRequired").asBoolean();

            if (!caseRequired) {
                log.info("Ignoring TransferRiskHeld for transferId={} as caseRequired is false", transferId);
                return;
            }

            String transferRequestedBy = payload.has("transferRequestedBy") ? payload.get("transferRequestedBy").asText() : "unknown";

            UUID caseId = UUID.randomUUID();
            opsCaseRepository.insertCase(
                    caseId,
                    UUID.fromString(transferId),
                    "OPEN",
                    "risk-service",
                    transferRequestedBy,
                    objectMapper.writeValueAsString(reasons)
            );

            log.info("Created caseId={} for transferId={} with requestedBy={}", caseId, transferId, transferRequestedBy);

            publishCaseCreated(caseId, UUID.fromString(transferId), reasons);

        } catch (JsonProcessingException e) {
            log.error("Failed to parse event message: {}", message, e);
        } catch (Exception e) {
            log.error("Error processing TransferRiskHeld event: {}", message, e);
            throw e;
        }
    }

    private void publishCaseCreated(UUID caseId, UUID transferId, JsonNode reasons) throws JsonProcessingException {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("caseId", caseId.toString());
        payload.put("transferId", transferId.toString());
        payload.put("status", "OPEN");
        payload.set("reasons", reasons);

        BankEventEnvelope envelope = new BankEventEnvelope(
                EventTypes.CaseCreated,
                UUID.randomUUID().toString(),
                Instant.now(),
                transferId.toString(),
                payload
        );

        String json = objectMapper.writeValueAsString(envelope);

        Message<String> message = MessageBuilder
                .withPayload(json)
                .setHeader(KafkaHeaders.TOPIC, Topics.BANKOPS_CASE_EVENTS)
                .setHeader(KafkaHeaders.KEY, transferId.toString())
                .setHeader("event_type", EventTypes.CaseCreated)
                .setHeader("correlation_id", transferId.toString())
                .setHeader("event_id", envelope.eventId())
                .build();

        kafkaTemplate.send(message);
        log.info("Published CaseCreated event for caseId={}, transferId={}", caseId, transferId);
    }
}
