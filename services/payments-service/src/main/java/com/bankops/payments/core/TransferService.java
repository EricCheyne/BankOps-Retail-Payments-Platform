package com.bankops.payments.core;

import com.bankops.common.events.EventTypes;
import com.bankops.payments.api.CreateTransferRequest;
import com.bankops.payments.api.TransferResponse;
import com.bankops.payments.repository.OutboxRepository;
import com.bankops.payments.repository.TransferRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TransferService {

    private final TransferRepository transferRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public TransferService(TransferRepository transferRepository,
                           OutboxRepository outboxRepository,
                           ObjectMapper objectMapper) {
        this.transferRepository = transferRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public record TransferResult(TransferResponse transfer, boolean replay) {}

    @Transactional
    public TransferResult createTransfer(String idempotencyKey, CreateTransferRequest req, String createdBy) {
        String normalizedCurrency = req.currency().toUpperCase();

        if (req.fromAccountId().equals(req.toAccountId())) {
            throw new IllegalArgumentException("fromAccountId and toAccountId must be different");
        }

        try {
            UUID transferId = UUID.randomUUID();
            transferRepository.insertTransfer(
                    transferId,
                    createdBy,
                    req.fromAccountId(),
                    req.toAccountId(),
                    req.amountCents(),
                    normalizedCurrency,
                    req.memo(),
                    "PENDING",
                    "PENDING",
                    idempotencyKey
            );

            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("transferId", transferId.toString());
            payload.put("fromAccountId", req.fromAccountId().toString());
            payload.put("toAccountId", req.toAccountId().toString());
            payload.put("amountCents", req.amountCents());
            payload.put("currency", normalizedCurrency);
            payload.put("memo", req.memo());
            payload.put("createdBy", createdBy);

            outboxRepository.insertOutboxEvent(
                    UUID.randomUUID(),
                    "transfer",
                    transferId,
                    EventTypes.TransferRequested,
                    objectMapper.writeValueAsString(payload)
            );

            TransferResponse response = transferRepository.findById(transferId)
                    .orElseThrow(() -> new RuntimeException("Failed to find created transfer"));

            return new TransferResult(response, false);

        } catch (DuplicateKeyException e) {
            TransferResponse existing = transferRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new RuntimeException("Replay detected but transfer not found", e));
            return new TransferResult(existing, true);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize outbox payload", e);
        }
    }

    public TransferResponse getTransfer(UUID id) {
        return transferRepository.findById(id).orElse(null);
    }
}
