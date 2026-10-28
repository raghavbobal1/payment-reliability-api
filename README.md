# Payment reliability API

A small Spring Boot project around one annoying payments problem: the client times out, retries the request, and you don't want to take the money twice.

It's a simulated transfer API, not connected to a real bank. I kept it small enough that the happy path, failure cases, and concurrency logic are easy to inspect.

## What it does

- Starts with three fake accounts, each with a balance.
- Sends transfers between accounts using Java 21 and Spring Boot.
- Uses an `Idempotency-Key` header. Retrying the same request returns the original transfer instead of debiting the account again.
- Returns `409` when someone reuses a key with different transfer details.
- Rejects invalid amounts, missing accounts, self-transfers and insufficient funds.
- Locks the in-memory ledger during writes to avoid overdraws within one running instance.
- Has tests for duplicate requests, different payloads, concurrent retries and competing transfers.

## Running locally

Java 21 and Maven 3.9+ required.

```bash
mvn spring-boot:run
```

```bash
curl http://localhost:8080/api/accounts

curl -i -X POST http://localhost:8080/api/transfers \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: invoice-1042' \
  -d '{"fromAccount":"ACC-1001","toAccount":"ACC-1002","amount":25.00}'
```

Run that POST twice with **the same key**. The first returns `201`; the retry returns `200` with `replayed: true`, and the money only moves once. Change the amount while keeping the same key and you'll get a `409` conflict.

To run the tests:

```bash
mvn test
```

There's also a `requests.http` file for the IntelliJ HTTP client. The health endpoint is `/actuator/health`.

## What I deliberately didn't do

The accounts, transfers and idempotency keys live in memory, and everything resets on restart. The synchronized method is fine for **one JVM**, not multiple app instances. There's no user authorization, persistent ledger, reconciliation or external payment processor.

A real system would need a database transaction with uniqueness constraints, row locking, audit events and proper auth. This is a learning/interview project, **not a production payment service**.

I wrote down the tradeoffs in [docs/NOTES.md](docs/NOTES.md).

## Next things to build

- PostgreSQL persistence with transaction boundaries and idempotency uniqueness.
- Spring Security and account-level authorization.
- HTTP integration tests and a simple failure/retry walkthrough.
- A basic React client if the API is solid first.
