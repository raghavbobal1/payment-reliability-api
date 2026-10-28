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
import org.junit.jupiter.api.Test;

class LedgerServiceTest {
    private TransferRequest move(String amount) {
        return new TransferRequest("ACC-1001", "ACC-1002", new BigDecimal(amount));
    }

    private BigDecimal balance(LedgerService ledger, String account) {
        return ledger.accounts().stream()
                .filter(a -> a.accountId().equals(account))
                .findFirst().orElseThrow().balance();
    }

    @Test void firstTransferMovesMoney() {
        LedgerService ledger = new LedgerService();
        TransferReceipt receipt = ledger.transfer("invoice-0001", move("27.40"));
        assertFalse(receipt.replayed());
        assertEquals(new BigDecimal("4972.60"), balance(ledger, "ACC-1001"));
        assertEquals(new BigDecimal("1527.40"), balance(ledger, "ACC-1002"));
        assertEquals(receipt.id(), ledger.findTransfer(receipt.id()).id());
    }

    @Test void retriesReturnTheSameTransfer() {
        LedgerService ledger = new LedgerService();
        TransferReceipt first = ledger.transfer("invoice-0002", move("50.00"));
        TransferReceipt retry = ledger.transfer("invoice-0002", move("50.0"));
        assertEquals(first.id(), retry.id());
        assertTrue(retry.replayed());
        assertEquals(new BigDecimal("4950.00"), balance(ledger, "ACC-1001"));
    }

    @Test void aReusedKeyWithDifferentDetailsConflicts() {
        LedgerService ledger = new LedgerService();
        ledger.transfer("invoice-0003", move("50.00"));
        LedgerException ex = assertThrows(LedgerException.class,
                () -> ledger.transfer("invoice-0003", move("75.00")));
        assertEquals(LedgerException.Kind.CONFLICT, ex.kind());
    }

    @Test void invalidAmountsDoNotMoveMoney() {
        LedgerService ledger = new LedgerService();
        for (String amount : List.of("0.00", "-1.00", "2.345", "1000000.01")) {
            assertThrows(LedgerException.class,
                    () -> ledger.transfer("invalid-" + amount, move(amount)));
        }
        assertEquals(new BigDecimal("5000.00"), balance(ledger, "ACC-1001"));
    }

    @Test void missingKeyAndUnknownAccountFail() {
        LedgerService ledger = new LedgerService();
        assertThrows(LedgerException.class, () -> ledger.transfer("", move("10.00")));
        assertThrows(LedgerException.class,
                () -> ledger.transfer("missing-0001",
                        new TransferRequest("ACC-9999", "ACC-1002", BigDecimal.ONE)));
        assertThrows(LedgerException.class,
                () -> ledger.transfer("self-00001",
                        new TransferRequest("ACC-1001", "ACC-1001", BigDecimal.ONE)));
        assertThrows(LedgerException.class, () -> ledger.findTransfer("not-real"));
    }

    @Test void notEnoughFundsDoesNotCreateTransfer() {
        LedgerService ledger = new LedgerService();
        LedgerException ex = assertThrows(LedgerException.class,
                () -> ledger.transfer("invoice-0004", move("9999.00")));
        assertEquals(LedgerException.Kind.INSUFFICIENT_FUNDS, ex.kind());
        assertEquals(new BigDecimal("5000.00"), balance(ledger, "ACC-1001"));
    }

    @Test void thirtyConcurrentRetriesOnlyDebitOnce() throws Exception {
        LedgerService ledger = new LedgerService();
        try (var executor = Executors.newFixedThreadPool(12)) {
            List<Callable<TransferReceipt>> calls = new ArrayList<>();
            for (int i = 0; i < 30; i++)
                calls.add(() -> ledger.transfer("same-request-0001", move("25.00")));
            List<Future<TransferReceipt>> results = executor.invokeAll(calls);
            Set<String> ids = new HashSet<>();
            int replays = 0;
            for (Future<TransferReceipt> future : results) {
                TransferReceipt receipt = future.get();
                ids.add(receipt.id());
                if (receipt.replayed()) replays++;
            }
            assertEquals(1, ids.size());
            assertEquals(29, replays);
        }
        assertEquals(new BigDecimal("4975.00"), balance(ledger, "ACC-1001"));
    }

    @Test void differentConcurrentTransfersCannotOverdraw() throws Exception {
        LedgerService ledger = new LedgerService();
        int success = 0;
        try (var executor = Executors.newFixedThreadPool(16)) {
            List<Callable<Boolean>> calls = new ArrayList<>();
            for (int i = 0; i < 70; i++) {
                final int n = i;
                calls.add(() -> {
                    try {
                        ledger.transfer("payment-key-" + n, move("100.00"));
                        return true;
                    } catch (LedgerException e) {
                        if (e.kind() != LedgerException.Kind.INSUFFICIENT_FUNDS) throw e;
                        return false;
                    }
                });
            }
            for (Future<Boolean> result : executor.invokeAll(calls))
                if (result.get()) success++;
        }
        assertEquals(50, success);
        assertEquals(new BigDecimal("0.00"), balance(ledger, "ACC-1001"));
        assertEquals(new BigDecimal("6500.00"), balance(ledger, "ACC-1002"));
    }
}
