package dev.raghav.payments;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** Runs against real PostgreSQL, not H2 or the in-memory ledger. */
@SpringBootTest
@ActiveProfiles("postgres")
@EnabledIfEnvironmentVariable(named = "POSTGRES_TESTS", matches = "true")
class PostgresLedgerIntegrationTest {
    @Autowired LedgerService ledger;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void resetDemo() {
        jdbc.update("DELETE FROM idempotency_requests");
        jdbc.update("DELETE FROM transfers");
        jdbc.update("UPDATE accounts SET balance = CASE id WHEN 'ACC-1001' THEN 5000.00 WHEN 'ACC-1002' THEN 1500.00 ELSE 0.00 END");
    }

    private TransferRequest move(String amount) {
        return new TransferRequest("ACC-1001", "ACC-1002", new BigDecimal(amount));
    }

    private BigDecimal balance(String id) {
        return jdbc.queryForObject("SELECT balance FROM accounts WHERE id = ?", BigDecimal.class, id);
    }

    @Test void retrySurvivesNewServiceInstance() {
        TransferReceipt first = ledger.transfer("persist-key-0001", move("20.00"));
        TransferReceipt retry = ledger.transfer("persist-key-0001", move("20.0"));
        assertEquals(first.id(), retry.id());
        assertTrue(retry.replayed());
        assertEquals(0, balance("ACC-1001").compareTo(new BigDecimal("4980.00")));
        assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM transfers", Long.class));
    }

    @Test void historyIsDatabaseBackedAndContainsNoRetryDuplicates() {
        ledger.transfer("history-persist-0001", move("15.00"));
        ledger.transfer("history-persist-0001", move("15.00"));
        assertEquals(1, ledger.recentTransfers().size());
        assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM transfers", Long.class));
    }

    @Test void conflictingRetryDoesNotDebitAgain() {
        ledger.transfer("persist-key-0002", move("20.00"));
        LedgerException e = assertThrows(LedgerException.class,
                () -> ledger.transfer("persist-key-0002", move("30.00")));
        assertEquals(LedgerException.Kind.CONFLICT, e.kind());
        assertEquals(0, balance("ACC-1001").compareTo(new BigDecimal("4980.00")));
    }

    @Test void concurrentRetriesCreateOnlyOneTransfer() throws Exception {
        try (var pool = Executors.newFixedThreadPool(12)) {
            List<Callable<TransferReceipt>> requests = new ArrayList<>();
            for (int i = 0; i < 30; i++)
                requests.add(() -> ledger.transfer("persist-key-0003", move("25.00")));
            Set<String> ids = new HashSet<>();
            int replayed = 0;
            for (Future<TransferReceipt> f : pool.invokeAll(requests)) {
                TransferReceipt receipt = f.get();
                ids.add(receipt.id());
                if (receipt.replayed()) replayed++;
            }
            assertEquals(1, ids.size());
            assertEquals(29, replayed);
        }
        assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM transfers", Long.class));
        assertEquals(0, balance("ACC-1001").compareTo(new BigDecimal("4975.00")));
    }

    @Test void concurrentUniquePaymentsCannotOverdraw() throws Exception {
        try (var pool = Executors.newFixedThreadPool(16)) {
            List<Callable<Boolean>> requests = new ArrayList<>();
            for (int i = 0; i < 70; i++) {
                int n = i;
                requests.add(() -> {
                    try {
                        ledger.transfer("unique-pay-" + n, move("100.00"));
                        return true;
                    } catch (LedgerException e) {
                        if (e.kind() != LedgerException.Kind.INSUFFICIENT_FUNDS) throw e;
                        return false;
                    }
                });
            }
            int successes = 0;
            for (Future<Boolean> f : pool.invokeAll(requests)) if (f.get()) successes++;
            assertEquals(50, successes);
        }
        assertEquals(0, balance("ACC-1001").compareTo(BigDecimal.ZERO));
        assertEquals(0, balance("ACC-1002").compareTo(new BigDecimal("6500.00")));
        assertEquals(50L, jdbc.queryForObject("SELECT count(*) FROM transfers", Long.class));
    }
}
