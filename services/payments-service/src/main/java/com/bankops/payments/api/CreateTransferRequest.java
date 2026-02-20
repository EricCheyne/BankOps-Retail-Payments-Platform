package com.bankops.payments.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateTransferRequest(
    @NotNull UUID fromAccountId,
    @NotNull UUID toAccountId,
    @Min(1) long amountCents,
    @NotBlank @Size(min = 3, max = 3) String currency,
    @Size(max = 140) String memo
) {}
