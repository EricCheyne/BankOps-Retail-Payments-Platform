package com.bankops.risk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "bankops.risk")
public class RiskRulesProperties {
    private long amountHoldThresholdCents = 50000;
    private int velocityMaxTransfers = 3;
    private int velocityWindowMinutes = 10;

    public long getAmountHoldThresholdCents() {
        return amountHoldThresholdCents;
    }

    public void setAmountHoldThresholdCents(long amountHoldThresholdCents) {
        this.amountHoldThresholdCents = amountHoldThresholdCents;
    }

    public int getVelocityMaxTransfers() {
        return velocityMaxTransfers;
    }

    public void setVelocityMaxTransfers(int velocityMaxTransfers) {
        this.velocityMaxTransfers = velocityMaxTransfers;
    }

    public int getVelocityWindowMinutes() {
        return velocityWindowMinutes;
    }

    public void setVelocityWindowMinutes(int velocityWindowMinutes) {
        this.velocityWindowMinutes = velocityWindowMinutes;
    }
}
