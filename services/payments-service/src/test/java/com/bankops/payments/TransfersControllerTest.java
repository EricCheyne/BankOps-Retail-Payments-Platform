package com.bankops.payments;

import com.bankops.payments.api.CreateTransferRequest;
import com.bankops.payments.api.TransferResponse;
import com.bankops.payments.repository.OutboxRepository;
import com.bankops.payments.repository.TransferRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.*;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
public class TransfersControllerTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @MockBean
    private TransferRepository transferRepository;

    @MockBean
    private OutboxRepository outboxRepository;

    @Test
    public void testCreateTransferSuccess() {
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        CreateTransferRequest request = new CreateTransferRequest(from, to, 1000, "USD", "test");

        UUID transferId = UUID.randomUUID();
        TransferResponse mockResponse = new TransferResponse(
                transferId, "PENDING", "PENDING", from, to, 1000, "USD", "test", Instant.now()
        );

        when(transferRepository.findResponseById(any(UUID.class))).thenReturn(Optional.of(mockResponse));

        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", "key-123");
        headers.set("X-Actor", "user-1");

        HttpEntity<CreateTransferRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<TransferResponse> responseEntity = restTemplate.postForEntity("/transfers", entity, TransferResponse.class);

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(responseEntity.getBody()).isNotNull();
        assertThat(responseEntity.getBody().id()).isEqualTo(transferId);
        assertThat(responseEntity.getBody().status()).isEqualTo("PENDING");
    }

    @Test
    public void testIdempotencyReplay() {
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        CreateTransferRequest request = new CreateTransferRequest(from, to, 1000, "USD", "test");

        UUID transferId = UUID.randomUUID();
        TransferResponse existingResponse = new TransferResponse(
                transferId, "PENDING", "PENDING", from, to, 1000, "USD", "test", Instant.now()
        );

        // First call fails with DuplicateKeyException
        when(transferRepository.findByIdempotencyKey(eq("key-replay")))
                .thenReturn(Optional.of(existingResponse));
        
        // Mocking the insert to throw DuplicateKeyException
        when(transferRepository.findByIdempotencyKey(eq("key-replay"))).thenReturn(Optional.of(existingResponse));
        // We need to trigger the catch block in TransferService
        // Since we can't easily mock the insert call directly in a way that throws and then we mock the find, 
        // we'll rely on the service logic.
        
        // In TransferService:
        // try { transferRepository.insertTransfer(...) } catch (DuplicateKeyException e) { ... findByIdempotencyKey(...) }
        
        // Let's mock insertTransfer to throw DuplicateKeyException
        org.mockito.Mockito.doThrow(new DuplicateKeyException("duplicate"))
                .when(transferRepository).insertTransfer(any(), any(), any(), any(), anyLong(), any(), any(), any(), any(), eq("key-replay"));

        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", "key-replay");

        HttpEntity<CreateTransferRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<TransferResponse> responseEntity = restTemplate.postForEntity("/transfers", entity, TransferResponse.class);

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(responseEntity.getBody()).isNotNull();
        assertThat(responseEntity.getBody().id()).isEqualTo(transferId);
    }
}
