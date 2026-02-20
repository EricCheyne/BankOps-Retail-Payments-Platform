package com.bankops.payments.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "bankops.outbox")
public class OutboxConfig {
    private int batchSize = 20;
    private int pollMs = 250;

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getPollMs() {
        return pollMs;
    }

    public void setPollMs(int pollMs) {
        this.pollMs = pollMs;
    }
}
