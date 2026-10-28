package dev.raghav.payments;
import java.math.BigDecimal;
import java.time.Instant;
public record TransferReceipt(String id, String fromAccount, String toAccount,
                              BigDecimal amount, Instant createdAt, boolean replayed) {
    TransferReceipt asReplay() {
        return new TransferReceipt(id, fromAccount, toAccount, amount, createdAt, true);
    }
}
