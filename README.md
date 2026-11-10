# DevTrace

Minimal starter for the DevTrace web service.

## Frontend

```powershell
cd frontend
npm install
npm run dev
```

Open the URL printed by Vite (usually http://localhost:5173).

## Backend

```powershell
cd backend
mvn spring-boot:run
```

The backend requires Maven installed and starts on http://localhost:8080.

## Database migration and initial administrator

The backend uses Flyway migrations. `schema.sql` initialization is disabled so the
schema has one migration owner.

Before the first migration of an existing database, create and verify a PostgreSQL
backup and record the current `analysis_requests` row count. Set `DB_URL`,
`DB_USERNAME`, and `DB_PASSWORD` in the backend process environment. Also set
`DEVTRACE_INITIAL_ADMIN_EMAIL`, `DEVTRACE_INITIAL_ADMIN_NICKNAME`, and
`DEVTRACE_INITIAL_ADMIN_PASSWORD` for the first migration. The password must be at
least 12 characters and at most 72 UTF-8 bytes; it is BCrypt-hashed by the
migration and is never written to SQL or application logs.

For an existing database created from the previous `schema.sql`, set
`FLYWAY_BASELINE_ON_MIGRATE=true` for the first startup only after verifying that
the database is the intended DevTrace database and the backup is usable. Flyway
then marks the existing `analysis_requests` schema as version 1 and runs the user
and ownership migration. For a new, empty database, leave this setting false so
Flyway creates version 1 before applying version 2. Remove the initial-admin
password environment variable after a successful migration; Flyway does not rerun
an applied migration on restart.

The ownership migration runs transactionally. It creates the initial administrator,
assigns existing analysis rows to that account, verifies the row count and a digest
of IDs, error messages, and creation timestamps, then adds the non-null foreign key
and user/date index. If the migration fails, PostgreSQL rolls back its schema and
data changes; retain the verified backup until post-migration checks are complete.
