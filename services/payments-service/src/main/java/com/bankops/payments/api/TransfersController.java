package com.bankops.payments.api;

import com.bankops.payments.core.TransferService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/transfers")
@Validated
public class TransfersController {

    private final TransferService transferService;

    public TransfersController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> createTransfer(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Actor", defaultValue = "system") String actor,
            @RequestBody @Valid CreateTransferRequest request) {

        TransferService.TransferResult result = transferService.createTransfer(idempotencyKey, request, actor);

        if (result.replay()) {
            return ResponseEntity.ok(result.transfer());
        } else {
            return ResponseEntity.status(HttpStatus.CREATED).body(result.transfer());
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransferResponse> getTransfer(@PathVariable UUID id) {
        TransferResponse response = transferService.getTransfer(id);
        if (response != null) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}
