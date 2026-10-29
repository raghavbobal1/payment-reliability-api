# LinkedIn post drafts

These are optional drafts. They have not been published.

## Part 1: Why I built it

I built a small payment API around a problem that sounds simple until you hit it in production.

A transfer succeeds but the response times out. The customer retries. How do you make sure the money doesn't move twice?

I used Java and Spring Boot to build the transfer flow, with idempotency keys so a retry returns the original receipt. It's all synthetic data.

## Part 2: Making retries safe

The interesting part wasn't the endpoint. It was making retries safe when requests arrive at the same time.

I added PostgreSQL transactions, a unique idempotency key constraint and a transaction-scoped lock. The tests send concurrent requests and check that the source account never goes negative.

It's a deliberately small demo, but it made the tradeoffs around consistency much more concrete.

## Part 3: Seeing it work

I added a browser console to my payment API so you can send a fake transfer, retry it and deliberately reuse a key with a different amount.

You can see the HTTP response, updated balances and transfer history without needing Postman. I also added automated browser screenshots in CI.

The code is on GitHub. It's a learning project, not a real payment processor.
