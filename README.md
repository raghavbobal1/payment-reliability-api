# Payment Reliability API

I wanted to build something around a problem that comes up in payment systems. What happens when a transfer goes through but the app times out before the customer sees a confirmation?

The customer might hit Send again. If the server treats that as a new payment, money could be sent twice.

This is a small Java 21 and Spring Boot API that handles that situation. It uses fake accounts and fake money.

## How it works

Every transfer request includes an `Idempotency-Key`. If the same request is sent again with the same key, the API returns the original transfer instead of moving the money again.

You can also try sending the same key with a different amount. That returns a `409 Conflict` because it is not the same request.

There are three test accounts with starting balances. The API checks the amount, makes sure the accounts exist and rejects transfers that would leave an account with a negative balance.

I used `BigDecimal` for amounts so money isn't handled using floating point values. The default in-memory profile uses synchronization for one JVM. The PostgreSQL profile uses one database transaction and a transaction-scoped advisory lock to serialize transfers across instances.

## Try it locally

You need Java 21 and Maven 3.9 or newer.

Run the API in the default in-memory mode:

```bash
mvn spring-boot:run
```

Open the interactive dashboard at **http://localhost:8080/**.

To use persistent PostgreSQL storage instead:

```bash
docker compose up -d postgres
mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

The PostgreSQL profile applies Flyway migrations and seeds three synthetic accounts. Restarting the API does not reset the database. Stop and remove the database volume only if you want to discard demo transfers.

Check the sample accounts:

```bash
curl http://localhost:8080/api/accounts
```

Send $25:

```bash
curl -i -X POST http://localhost:8080/api/transfers \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: invoice-1042' \
  -d '{"fromAccount":"ACC-1001","toAccount":"ACC-1002","amount":25.00}'
```

Run the same request a second time. The first response is `201 Created`. The retry returns `200 OK` with `replayed: true`. Only $25 is transferred.

If you change the amount but keep `invoice-1042` as the key, you'll get a `409 Conflict`.

You can also try the examples in [requests.http](requests.http).

## Tests

```bash
mvn test
```

The test suite includes eight ledger unit tests, eight HTTP integration tests and five PostgreSQL integration tests. The HTTP tests start a real Spring Boot server and send real requests. The PostgreSQL tests use a real database to check persistence and concurrent retries. GitHub Actions runs the full suite and captures browser screenshots of the running demo. See [HTTP test notes](docs/HTTP_TESTS.md).

## Current scope and limitations

The interactive dashboard is served by Spring Boot at the root URL. It shows synthetic account balances, sends transfers, retries the same request and demonstrates conflicts. Recent transfers come from the `GET /api/transfers` endpoint and persist across restarts in the PostgreSQL profile.

The PostgreSQL profile uses JDBC, Flyway and a transaction-scoped advisory lock. The default profile remains in-memory so the original unit and HTTP tests work without Docker. GitHub Actions starts a real PostgreSQL service for the database integration tests.

The advisory lock serializes all demo transfers. This is deliberately simple and correct for a small teaching project, but would be a throughput bottleneck for a production payment system. Authentication, account permissions, audit trails, rate limiting, monitoring and secure deployment are not implemented. This is not a real payment processor.

GitHub Pages can host static files but cannot run this Java backend or its PostgreSQL database. The interactive console requires a running API. A Render deployment Blueprint is included, but public hosting must be verified independently before sharing a live URL.

See [PostgreSQL setup](docs/POSTGRES_SETUP.md) and [HTTP test notes](docs/HTTP_TESTS.md).

## Screenshots and architecture

GitHub Actions opens the running Spring Boot demo in Chromium and uploads screenshots of the dashboard, a successful transfer, an idempotent retry and a conflicting retry. Open the latest successful workflow run and download the `payment-reliability-dashboard-screenshots` artifact. The screenshot script is [scripts/capture-screenshots.cjs](scripts/capture-screenshots.cjs).

See the [architecture diagram](docs/architecture.svg) and [three optional LinkedIn drafts](docs/LINKEDIN_DRAFTS.md). Nothing is published to LinkedIn automatically.
