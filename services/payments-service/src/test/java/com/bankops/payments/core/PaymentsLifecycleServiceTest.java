package com.bankops.payments.core;

import com.bankops.common.events.EventTypes;
import com.bankops.payments.ledger.LedgerClient;
import com.bankops.payments.repository.OutboxRepository;
import com.bankops.payments.repository.TransferRepository;
import com.bankops.payments.repository.TransferRepository.TransferRow;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentsLifecycleServiceTest {

    @Mock
    private TransferRepository transferRepository;
    @Mock
    private OutboxRepository outboxRepository;
    @Mock
    private LedgerClient ledgerClient;

    private PaymentsLifecycleService lifecycleService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        lifecycleService = new PaymentsLifecycleService(transferRepository, outboxRepository, ledgerClient, objectMapper);
    }

    @Test
    void attemptPosting_Success() {
        UUID transferId = UUID.randomUUID();
        TransferRow transfer = new TransferRow(
                transferId, "user1", UUID.randomUUID(), UUID.randomUUID(),
                1000L, "USD", "memo", "PENDING", "CLEARED", "idemp1",
                Instant.now(), Instant.now(), null, null
        );

        when(transferRepository.findById(transferId)).thenReturn(Optional.of(transfer));
        
        UUID ledgerEntryId = UUID.randomUUID();
        when(ledgerClient.postTransferToLedger(any())).thenReturn(new LedgerClient.LedgerPostResult(ledgerEntryId, "POSTED"));

        lifecycleService.attemptPosting(transferId);

        verify(transferRepository).updateStatus(transferId, "POSTING", null);
        verify(transferRepository).setLedgerEntryId(transferId, ledgerEntryId);
        verify(transferRepository).updateStatus(transferId, "COMPLETED", null);
        
        ArgumentCaptor<String> eventTypeCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxRepository, times(2)).insertOutboxEvent(any(), eq("Transfer"), eq(transferId), eventTypeCaptor.capture(), any());
        
        List<String> eventTypes = eventTypeCaptor.getAllValues();
        assertEquals(EventTypes.TransferPosted, eventTypes.get(0));
        assertEquals(EventTypes.TransferCompleted, eventTypes.get(1));
    }

    @Test
    void attemptPosting_Failure() {
        UUID transferId = UUID.randomUUID();
        TransferRow transfer = new TransferRow(
                transferId, "user1", UUID.randomUUID(), UUID.randomUUID(),
                1000L, "USD", "memo", "PENDING", "CLEARED", "idemp1",
                Instant.now(), Instant.now(), null, null
        );

        when(transferRepository.findById(transferId)).thenReturn(Optional.of(transfer));
        when(ledgerClient.postTransferToLedger(any())).thenThrow(new RuntimeException("Connection failed"));

        lifecycleService.attemptPosting(transferId);

        verify(transferRepository).updateStatus(transferId, "POSTING", null);
        verify(transferRepository).updateStatus(transferId, "POSTING_FAILED", "LEDGER_POST_FAILED");
        verify(outboxRepository, never()).insertOutboxEvent(any(), any(), any(), any(), any());
    }

    @Test
    void attemptPosting_StillHeld() {
        UUID transferId = UUID.randomUUID();
        TransferRow transfer = new TransferRow(
                transferId, "user1", UUID.randomUUID(), UUID.randomUUID(),
                1000L, "USD", "memo", "HELD", "HELD", "idemp1",
                Instant.now(), Instant.now(), "RISK_HOLD", null
        );

        when(transferRepository.findById(transferId)).thenReturn(Optional.of(transfer));

        lifecycleService.attemptPosting(transferId);

        verify(transferRepository, never()).updateStatus(any(), eq("POSTING"), any());
        verify(ledgerClient, never()).postTransferToLedger(any());
    }
}
