package com.bankops.common.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record BankEventEnvelope(
    @JsonProperty("eventType") String eventType,
    @JsonProperty("eventId") String eventId,
    @JsonProperty("occurredAt") Instant occurredAt,
    @JsonProperty("correlationId") String correlationId,
    @JsonProperty("payload") JsonNode payload
) {}
