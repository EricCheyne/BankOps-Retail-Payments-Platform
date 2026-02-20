package com.bankops.payments.api;

import com.bankops.payments.outbox.OutboxPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/outbox")
@ConditionalOnProperty(name = "bankops.internal-endpoints.enabled", havingValue = "true", matchIfMissing = true)
public class InternalOutboxController {

    private final OutboxPublisher outboxPublisher;

    public InternalOutboxController(OutboxPublisher outboxPublisher) {
        this.outboxPublisher = outboxPublisher;
    }

    @PostMapping("/publish-once")
    public int publishOnce() {
        return outboxPublisher.publishOnce();
    }
}
