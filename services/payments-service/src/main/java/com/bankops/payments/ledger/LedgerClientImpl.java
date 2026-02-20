package com.bankops.payments.ledger;

import com.bankops.payments.repository.TransferRepository.TransferRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class LedgerClientImpl implements LedgerClient {

    private static final Logger log = LoggerFactory.getLogger(LedgerClientImpl.class);

    private final RestClient restClient;

    public LedgerClientImpl(RestClient.Builder restClientBuilder,
                            @Value("${bankops.ledger.base-url:http://localhost:8082}") String baseUrl) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    }

    @Override
    public LedgerPostResult postTransferToLedger(TransferRow transfer) {
        log.info("Posting transfer {} to ledger", transfer.id());

        Map<String, Object> request = Map.of(
                "idempotencyKey", "transfer:" + transfer.id(),
                "correlationId", transfer.id().toString(),
                "createdBy", transfer.createdBy(),
                "reasonCode", "RETAIL_TRANSFER",
                "legs", List.of(
                        Map.of(
                                "accountId", transfer.fromAccountId().toString(),
                                "direction", "DEBIT",
                                "amountCents", transfer.amountCents(),
                                "currency", transfer.currency(),
                                "balanceType", "LEDGER"
                        ),
                        Map.of(
                                "accountId", transfer.toAccountId().toString(),
                                "direction", "CREDIT",
                                "amountCents", transfer.amountCents(),
                                "currency", transfer.currency(),
                                "balanceType", "LEDGER"
                        )
                )
        );

        try {
            return restClient.post()
                    .uri("/ledger/postings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(LedgerPostResult.class);
        } catch (Exception e) {
            log.error("Failed to post transfer {} to ledger: {}", transfer.id(), e.getMessage());
            throw e;
        }
    }
}
