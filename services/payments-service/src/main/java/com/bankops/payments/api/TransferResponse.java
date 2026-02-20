package com.bankops.payments.api;

import java.time.Instant;
import java.util.UUID;

public record TransferResponse(
    UUID id,
    String status,
    String riskStatus,
    UUID fromAccountId,
    UUID toAccountId,
    long amountCents,
    String currency,
    String memo,
    Instant createdAt
) {}
