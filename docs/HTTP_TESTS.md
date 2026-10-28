# HTTP integration tests

The tests in `src/test/java/dev/raghav/payments/TransferHttpIntegrationTest.java` run the full Spring Boot application on a random local port and send requests with Java's `HttpClient`. These are HTTP tests, not mocked controller calls.

Each test starts with a fresh in-memory ledger. That prevents a transfer in one test from affecting another.

## Verified behavior

- A valid transfer returns `201 Created` and updates both account balances.
- A retry with the same `Idempotency-Key` returns `200 OK`, the original receipt and `Idempotent-Replayed: true`. No second debit occurs.
- Reusing a key with a different amount returns `409 Conflict`.
- An unknown account returns `404 Not Found` without changing balances.
- Insufficient funds returns `422 Unprocessable Entity` without changing balances.
- An invalid amount or missing idempotency key returns `400 Bad Request`.
- Looking up an unknown transfer returns `404 Not Found`.

The existing eight unit tests cover ledger behavior, including concurrent requests within one JVM.

## How to run

```bash
mvn verify
```

On October 28, 2026, GitHub Actions [run 40693355128](https://github.com/raghavbobal1/payment-reliability-api/actions/runs/40693355128) reported **15 tests, 0 failures, 0 errors, 0 skipped**.

## Limits

This still uses in-memory storage. The concurrency checks do not establish safety across multiple application instances. The API does not have authentication, a persistent transaction log or a database-backed idempotency constraint. Those need separate implementation and tests.
