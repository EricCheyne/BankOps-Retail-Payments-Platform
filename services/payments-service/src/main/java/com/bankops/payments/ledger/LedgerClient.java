package com.bankops.payments.ledger;

import com.bankops.payments.repository.TransferRepository.TransferRow;
import java.util.UUID;

public interface LedgerClient {
    LedgerPostResult postTransferToLedger(TransferRow transfer);

    record LedgerPostResult(UUID entryId, String status) {}
}
