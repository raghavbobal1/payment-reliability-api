# Implementation notes

The goal is to demonstrate a common payment problem: a client can time out after a successful transfer and retry without knowing whether the first request worked.

## Idempotency

The client supplies a stable `Idempotency-Key` for the same transfer attempt. The server checks that key first. If it has already succeeded, the API returns the original receipt. A different amount or destination with the same key returns HTTP 409.

Only successful transfers are stored against a key. A failed validation or insufficient-funds attempt can be corrected and retried.

## Concurrency

The default ledger uses a synchronized in-memory implementation for local experiments. It is not shared across Java processes.

The PostgreSQL profile uses a single database transaction and a transaction-scoped advisory lock. Debit, credit, receipt and idempotency record either commit together or roll back together. The database enforces nonnegative balances and unique keys.

This approach serializes every transfer and is intentionally limited to a small demo. A real payment service would need more granular locking, permissions, reconciliation, audit logging, monitoring and operational recovery.

## Demo console

The Spring Boot app serves an interactive browser console. It loads synthetic balances from the API, creates transfers, retries the same key, tests a conflict and fetches recent transfers from the database or in-memory ledger.

The repository's GitHub Actions workflow also opens the running app in Chromium and saves screenshots of four real states as an artifact. Screenshots are not mocked and are not evidence of public hosting.
