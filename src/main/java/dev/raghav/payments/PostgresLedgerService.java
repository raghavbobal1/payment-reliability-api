package dev.raghav.payments;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Database-backed implementation. A PostgreSQL transaction-scoped advisory lock
 * serializes transfers so retries and balance updates are atomic across replicas.
 */
public class PostgresLedgerService extends LedgerService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public PostgresLedgerService(JdbcTemplate jdbc, TransactionTemplate transactions) {
        this.jdbc = jdbc;
        this.transactions = transactions;
    }

    @Override
    public List<AccountBalance> accounts() {
        return jdbc.query("SELECT id, balance FROM accounts ORDER BY id",
                (rs, n) -> new AccountBalance(rs.getString(1), rs.getBigDecimal(2)));
    }

    @Override
    public List<TransferReceipt> recentTransfers() {
        return jdbc.query("SELECT id, from_account, to_account, amount, created_at " +
                "FROM transfers ORDER BY created_at DESC, id DESC LIMIT 20",
                (rs, n) -> receipt(rs, false));
    }

    @Override
    public TransferReceipt findTransfer(String id) {
        try {
            UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            throw new LedgerException(LedgerException.Kind.NOT_FOUND, "Transfer not found");
        }
        List<TransferReceipt> rows = jdbc.query(
                "SELECT id, from_account, to_account, amount, created_at FROM transfers WHERE id = ?::uuid",
                (rs, n) -> receipt(rs, false), id);
        if (rows.isEmpty()) {
            throw new LedgerException(LedgerException.Kind.NOT_FOUND, "Transfer not found");
        }
        return rows.getFirst();
    }

    @Override
    public TransferReceipt transfer(String key, TransferRequest request) {
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
        BigDecimal amount = request.amount();
        if (amount == null || amount.signum() <= 0) {
            throw new LedgerException(LedgerException.Kind.INVALID_INPUT, "Amount must be positive");
        }
        try {
            amount = amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new LedgerException(LedgerException.Kind.INVALID_INPUT, "At most two decimal places are allowed");
        }
        if (amount.compareTo(new BigDecimal("1000000.00")) > 0) {
            throw new LedgerException(LedgerException.Kind.INVALID_INPUT, "Amount exceeds demo transfer limit");
        }
        BigDecimal checked = amount;
        return transactions.execute(status -> {
            // This serializes the small synthetic demo ledger across application instances.
            jdbc.execute("SELECT pg_advisory_xact_lock(7291041)");
            List<TransferReceipt> saved = jdbc.query(
                    "SELECT t.id, t.from_account, t.to_account, t.amount, t.created_at " +
                    "FROM idempotency_requests k JOIN transfers t ON t.id = k.transfer_id WHERE k.request_key = ?",
                    (rs, n) -> receipt(rs, true), key);
            if (!saved.isEmpty()) {
                TransferReceipt prior = saved.getFirst();
                if (!prior.fromAccount().equals(from) || !prior.toAccount().equals(to) ||
                        prior.amount().compareTo(checked) != 0) {
                    throw new LedgerException(LedgerException.Kind.CONFLICT,
                            "This key was already used for a different transfer");
                }
                return prior;
            }
            List<BigDecimal> sources = jdbc.query("SELECT balance FROM accounts WHERE id = ?",
                    (rs, n) -> rs.getBigDecimal(1), from);
            List<BigDecimal> destinations = jdbc.query("SELECT balance FROM accounts WHERE id = ?",
                    (rs, n) -> rs.getBigDecimal(1), to);
            if (sources.isEmpty() || destinations.isEmpty()) {
                throw new LedgerException(LedgerException.Kind.NOT_FOUND, "Account not found");
            }
            if (sources.getFirst().compareTo(checked) < 0) {
                throw new LedgerException(LedgerException.Kind.INSUFFICIENT_FUNDS,
                        "Not enough money in the source account");
            }
            jdbc.update("UPDATE accounts SET balance = balance - ? WHERE id = ?", checked, from);
            jdbc.update("UPDATE accounts SET balance = balance + ? WHERE id = ?", checked, to);
            UUID id = UUID.randomUUID();
            Instant now = Instant.now();
            jdbc.update("INSERT INTO transfers(id, from_account, to_account, amount, created_at) VALUES (?, ?, ?, ?, ?)",
                    id, from, to, checked, java.sql.Timestamp.from(now));
            jdbc.update("INSERT INTO idempotency_requests(request_key, from_account, to_account, amount, transfer_id) VALUES (?, ?, ?, ?, ?)",
                    key, from, to, checked, id);
            return new TransferReceipt(id.toString(), from, to, checked, now, false);
        });
    }

    private TransferReceipt receipt(ResultSet rs, boolean replayed) throws SQLException {
        return new TransferReceipt(rs.getString("id"), rs.getString("from_account"),
                rs.getString("to_account"), rs.getBigDecimal("amount"),
                rs.getTimestamp("created_at").toInstant(), replayed);
    }
}
