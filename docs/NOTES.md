# Notes from building this

## Why the key matters

Each logical transfer should get one idempotency key. If the response gets lost, the client can retry with the same key. The server checks the request details before returning the original receipt. If the key has already been used for a different transfer, it's a conflict.

The retry check runs before looking at balances because the account could have changed since the first successful request.

I only save **successful** transfers against keys in this version. A rejected request can be fixed and sent again using that key. More complete APIs might persist selected failed responses too; that's a contract choice.

## One lock isn't a distributed transaction

`synchronized` makes the balance check and update atomic within one JVM. That's easy to reason about, and the tests show the behavior when many requests arrive at once. Across two running instances, these maps and locks are separate. It isn't safe to use with real accounts.

Moving this to PostgreSQL means a single database transaction for debit, credit and idempotency record, with a unique constraint on the key and appropriate locks or version checks.

## What I'd need before production

Authentication, account ownership checks, authorization, immutable audit records, structured logging without sensitive values, rate limits, transfer limits per account, reconciliation, persistence, monitoring, failure recovery and a much stricter money movement model (double-entry journals).

The demo has none of those guarantees and shouldn't be exposed publicly as a live transfer service.
