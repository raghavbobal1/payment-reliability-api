# PostgreSQL development setup

Run `docker compose up -d postgres` to start a local PostgreSQL 16 database. The container binds to localhost only. The default password is for local development only. Override `POSTGRES_PASSWORD` before sharing a machine or using a nonlocal environment.

The migration files define accounts, transfers and idempotency records with constraints. They are not yet connected to the running API. The existing API continues to use in-memory storage. Do not claim durability, transactional correctness or database-backed concurrency until JDBC persistence and database integration tests are implemented and pass.

To inspect the schema after migrations are wired into the application, run `docker compose exec postgres psql -U payments -d payments`.

All seeded accounts are synthetic.
