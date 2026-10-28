# Payment Reliability API

I wanted to build something around a problem that comes up in payment systems. What happens when a transfer goes through but the app times out before the customer sees a confirmation?

The customer might hit Send again. If the server treats that as a new payment, money could be sent twice.

This is a small Java 21 and Spring Boot API that handles that situation. It uses fake accounts and fake money.

## How it works

Every transfer request includes an `Idempotency-Key`. If the same request is sent again with the same key, the API returns the original transfer instead of moving the money again.

You can also try sending the same key with a different amount. That returns a `409 Conflict` because it is not the same request.

There are three test accounts with starting balances. The API checks the amount, makes sure the accounts exist and rejects transfers that would leave an account with a negative balance.

I used `BigDecimal` for amounts so money isn't handled using floating point values. The balance update is synchronized to handle concurrent requests in this demo.

## Try it locally

You need Java 21 and Maven 3.9 or newer.

Run the API:

```bash
mvn spring-boot:run
```

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

There are 15 JUnit tests: eight ledger unit tests and seven HTTP integration tests. The integration tests start the Spring Boot server on a random port and send real HTTP requests. They verify successful transfers, replayed requests, conflicting keys, unknown accounts, insufficient funds, invalid input and missing transfers. They also check balances after each outcome. See [HTTP test notes](docs/HTTP_TESTS.md). The project runs all tests in GitHub Actions.

## What is still missing

Right now everything is stored in memory. Restarting the app resets the accounts. The synchronized method only protects one running instance.

I'd like to move the balances and transfers to PostgreSQL next. That would let me work on real database transactions and make the retry logic safer when multiple instances are running.

It also needs authentication, account permissions and audit records before it could be used for anything beyond a local demo.

I wrote down some of those decisions in [my notes](docs/NOTES.md).

This project doesn't connect to any bank or handle real money.
