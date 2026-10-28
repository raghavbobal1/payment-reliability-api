package dev.raghav.payments;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TransferController {
    private final LedgerService ledger;

    public TransferController(LedgerService ledger) {
        this.ledger = ledger;
    }

    @GetMapping("/accounts")
    public List<AccountBalance> accounts() {
        return ledger.accounts();
    }

    @PostMapping("/transfers")
    public ResponseEntity<TransferReceipt> create(
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @RequestBody(required = false) TransferRequest request) {
        TransferReceipt receipt = ledger.transfer(key, request);
        return ResponseEntity.status(receipt.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .header("Idempotent-Replayed", Boolean.toString(receipt.replayed()))
                .body(receipt);
    }

    @GetMapping("/transfers/{id}")
    public TransferReceipt transfer(@PathVariable String id) {
        return ledger.findTransfer(id);
    }
}
