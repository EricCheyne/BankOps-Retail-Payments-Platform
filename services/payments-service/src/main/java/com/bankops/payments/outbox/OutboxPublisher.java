package com.bankops.payments.outbox;

import com.bankops.common.events.BankEventEnvelope;
import com.bankops.common.events.Topics;
import com.bankops.payments.config.OutboxConfig;
import com.bankops.payments.repository.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final OutboxConfig outboxConfig;

    public OutboxPublisher(OutboxRepository outboxRepository,
                           KafkaTemplate<String, String> kafkaTemplate,
                           ObjectMapper objectMapper,
                           OutboxConfig outboxConfig) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.outboxConfig = outboxConfig;
    }

    @Scheduled(fixedDelayString = "${bankops.outbox.poll-ms:250}")
    public void scheduledPublish() {
        publishOnce();
    }

    @Transactional
    public int publishOnce() {
        List<OutboxEvent> events = outboxRepository.fetchBatchForPublish(outboxConfig.getBatchSize());
        int publishedCount = 0;

        for (OutboxEvent event : events) {
            try {
                publishEvent(event);
                outboxRepository.markPublished(event.id());
                publishedCount++;
                log.info("Published event: event_id={}, event_type={}, correlation_id={}",
                        event.id(), event.eventType(), event.aggregateId());
            } catch (Exception e) {
                log.error("Failed to publish outbox event: id={}", event.id(), e);
                // We do NOT mark it published, so it stays in the outbox
                // We keep moving for other events in the batch
            }
        }
        return publishedCount;
    }

    private void publishEvent(OutboxEvent event) throws JsonProcessingException, ExecutionException, InterruptedException {
        BankEventEnvelope envelope = new BankEventEnvelope(
                event.eventType(),
                event.id().toString(),
                event.createdAt(),
                event.aggregateId().toString(),
                event.payloadJson()
        );

        String payload = objectMapper.writeValueAsString(envelope);
        String key = event.aggregateId().toString();

        ProducerRecord<String, String> record = new ProducerRecord<>(Topics.BANKOPS_TRANSFER_EVENTS, key, payload);
        record.headers().add(new RecordHeader("event_type", event.eventType().getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader("correlation_id", event.aggregateId().toString().getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader("event_id", event.id().toString().getBytes(StandardCharsets.UTF_8)));

        kafkaTemplate.send(record).get();
    }
}
