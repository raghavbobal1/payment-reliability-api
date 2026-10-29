# Automated tests

`TransferHttpIntegrationTest` starts a real Spring Boot server on a random port and sends HTTP requests with Java HttpClient. Each test gets fresh in-memory balances.

The HTTP tests cover successful transfers, replayed requests, conflicting keys, unknown accounts, insufficient funds, invalid input, missing transfers and the recent transfer history endpoint.

`LedgerServiceTest` checks the in-memory ledger, including thirty concurrent retries and competing transfers that must not overdraw the source account.

`PostgresLedgerIntegrationTest` runs against PostgreSQL 16 when `POSTGRES_TESTS=true`. It checks retries after a service call, conflicting payloads, thirty concurrent retries, competing payments and database-backed history without duplicate rows.

## Run locally

```bash
mvn verify
```

For PostgreSQL integration tests, start the database first and run:

```bash
docker compose up -d postgres
POSTGRES_TESTS=true mvn verify
```

GitHub Actions starts PostgreSQL automatically and runs the complete suite. Check the current CI run before claiming any test total or passing status.

## Screenshots

The CI workflow starts the packaged Spring Boot app, uses Chromium to interact with the actual dashboard and uploads four screenshots as the `payment-reliability-dashboard-screenshots` artifact.

These screenshots show the initial dashboard, a created transfer, a successful idempotent retry and a conflicting retry. They are real local-app screenshots, not a public-hosting claim.
