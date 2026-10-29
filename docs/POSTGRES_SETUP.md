# PostgreSQL setup

The PostgreSQL profile uses JDBC for balances, transfers and idempotency records. Flyway runs V1 and V2 on startup and seeds three synthetic accounts on a fresh database.

## Start locally

```bash
docker compose up -d postgres
mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

Open the demo at http://localhost:8080/. A restart keeps the database balances and transfers.

```bash
docker compose exec postgres psql -U payments -d payments
```

Run `SELECT * FROM transfers ORDER BY created_at DESC LIMIT 20;` to inspect the stored receipts.

## What the transaction guarantees

Every successful transfer takes one PostgreSQL transaction and one transaction-scoped advisory lock. The API checks the idempotency key before checking the current balance. It then debits, credits and inserts the transfer and key records in that same transaction.

A repeated request with the same key returns the original receipt without a second debit. A repeated key with different transfer details returns HTTP 409. The unique key constraint is an additional safeguard. This demo serializes all transfers using one advisory lock, which keeps the example simple but limits throughput.

GitHub Actions runs the PostgreSQL integration tests against a real PostgreSQL 16 service. Those tests include concurrent retries, competing transfers and persistent history. The default application profile uses a separate in-memory ledger and disables Flyway.

## Cloud configuration

The repository includes a Render Blueprint for a Java web service and PostgreSQL database. It expects `DATABASE_HOST`, `DATABASE_NAME`, `DATABASE_USER` and `DATABASE_PASSWORD` and uses the `postgres` Spring profile. The app listens on `PORT` when provided.

The database credentials are provided by the hosting environment. Do not commit passwords or connection strings.

This is a shared synthetic demonstration, not a real payment processor. It has no login, account authorization or rate limiting. The demo database is not suitable for sensitive data or real money.
