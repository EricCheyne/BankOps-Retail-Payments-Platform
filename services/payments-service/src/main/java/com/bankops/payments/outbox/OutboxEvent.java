package com.bankops.payments.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record OutboxEvent(
    UUID id,
    Instant createdAt,
    String aggregateType,
    UUID aggregateId,
    String eventType,
    JsonNode payloadJson,
    Instant publishedAt
) {}
