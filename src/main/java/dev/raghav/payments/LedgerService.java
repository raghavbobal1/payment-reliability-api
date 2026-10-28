package dev.raghav.payments;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// In-memory for now. The lock is enough for one JVM, not multiple replicas.
public class LedgerService {
    private final Map<String, BigDecimal> balances = new LinkedHashMap<>();
    private final Map<String, TransferReceipt> transfers = new LinkedHashMap<>();
    private final Map<String, SavedRequest> keys = new LinkedHashMap<>();

    public LedgerService() {
        balances.put("ACC-1001", new BigDecimal("5000.00"));
        balances.put("ACC-1002", new BigDecimal("1500.00"));
        balances.put("ACC-1003", new BigDecimal("0.00"));
    }

    public synchronized List<AccountBalance> accounts() {
        List<AccountBalance> result = new ArrayList<>();
        balances.forEach((id, balance) -> result.add(new AccountBalance(id, balance)));
        return List.copyOf(result);
    }

    public synchronized TransferReceipt findTransfer(String id) {
        TransferReceipt transfer = transfers.get(id);
        if (transfer == null) {
            throw new LedgerException(LedgerException.Kind.NOT_FOUND, "Transfer not found");
        }
        return transfer;
    }

    public synchronized TransferReceipt transfer(String key, TransferRequest request) {
        if (key == null || !key.matches("[A-Za-z0-9:_-]{8,128}")) {
            throw new LedgerException(LedgerException.Kind.INVALID_INPUT,
                    "Idempotency-Key header required (8-128 characters)");
        }
        if (request == null || request.fromAccount() == null || request.toAccount() == null ||
                request.fromAccount().isBlank() || request.toAccount().isBlank()) {
            throw new LedgerException(LedgerException.Kind.INVALID_INPUT, "Both accounts are required");
        }
        String from = request.fromAccount();
        String to = request.toAccount();
        if (from.equals(to)) {
            throw new LedgerException(LedgerException.Kind.INVALID_INPUT, "Choose two different accounts");
        }
        BigDecimal amount = checkedAmount(request.amount());

        // Retry lookup comes first: balances could have changed since the first request.
        SavedRequest previous = keys.get(key);
        if (previous != null) {
            if (!previous.from.equals(from) || !previous.to.equals(to) ||
                    previous.amount.compareTo(amount) != 0) {
                throw new LedgerException(LedgerException.Kind.CONFLICT,
                        "This key was already used for a different transfer");
            }
            return previous.receipt.asReplay();
        }

        BigDecimal source = balances.get(from);
        BigDecimal destination = balances.get(to);
        if (source == null || destination == null) {
            throw new LedgerException(LedgerException.Kind.NOT_FOUND, "Account not found");
        }
        if (source.compareTo(amount) < 0) {
            throw new LedgerException(LedgerException.Kind.INSUFFICIENT_FUNDS,
                    "Not enough money in the source account");
        }

        balances.put(from, source.subtract(amount));
        balances.put(to, destination.add(amount));
        TransferReceipt receipt = new TransferReceipt(
                UUID.randomUUID().toString(), from, to, amount, Instant.now(), false);
        transfers.put(receipt.id(), receipt);
        keys.put(key, new SavedRequest(from, to, amount, receipt));
        return receipt;
    }

    private BigDecimal checkedAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new LedgerException(LedgerException.Kind.INVALID_INPUT, "Amount must be positive");
        }
        BigDecimal money;
        try {
            money = amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new LedgerException(LedgerException.Kind.INVALID_INPUT,
                    "At most two decimal places are allowed");
        }
        if (money.compareTo(new BigDecimal("1000000.00")) > 0) {
            throw new LedgerException(LedgerException.Kind.INVALID_INPUT,
                    "Amount exceeds demo transfer limit");
        }
        return money;
    }

    private record SavedRequest(String from, String to, BigDecimal amount,
                                TransferReceipt receipt) { }
}
