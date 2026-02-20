package com.bankops.common.events;

public final class EventTypes {
    private EventTypes() {}

    public static final String TransferRequested = "TransferRequested";
    public static final String TransferRiskHeld = "TransferRiskHeld";
    public static final String TransferRiskCleared = "TransferRiskCleared";
    public static final String CaseCreated = "CaseCreated";
    public static final String CaseApproved = "CaseApproved";
    public static final String CaseRejected = "CaseRejected";
    public static final String TransferPosted = "TransferPosted";
    public static final String TransferCompleted = "TransferCompleted";
}
