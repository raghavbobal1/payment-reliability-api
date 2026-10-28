# Notes

A few things I wanted to keep track of while working on this API.

## Retrying a payment

A client should use the same idempotency key when it retries the same transfer. The API first checks whether that key already belongs to a completed transfer.

If the key and transfer details match, it returns the first receipt. If someone uses the same key for a different transfer, the API returns a conflict.

I check the key before checking the current balance. Otherwise a retry could fail just because another transfer changed the account balance after the original request succeeded.

At the moment I only save successful transfers against a key. Failed requests can be corrected and sent again.

## Concurrent requests

I used `synchronized` around the balance update and key lookup. It's straightforward and helps make the tests meaningful without setting up a database.

The downside is that it only works inside one Java process. If I run two copies of the API, each copy has its own accounts and its own lock. They won't agree about which transfers have already happened.

The next version should use PostgreSQL with a transaction for the debit, credit and idempotency record. The key will also need a unique constraint in the database.

## Things I haven't built yet

This isn't a full ledger. There's no separate debit and credit journal or reconciliation process. There's also no authentication or way to check whether a user owns an account.

Before considering anything like a real deployment, I'd need persistent storage, account permissions, audit history, monitoring and proper failure recovery.

For now I want to keep it small and get the transaction handling right first.
