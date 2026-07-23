# Retail Banking Core

[![CI](https://github.com/Dedmoo/RetailBankingCore/actions/workflows/ci.yml/badge.svg)](https://github.com/Dedmoo/RetailBankingCore/actions/workflows/ci.yml)

Retail banking monolith: JWT auth, customer accounts, house funding account for balanced
openings, double-entry journals, transfer reversal, statement, append-only ledger + audit,
Postgres + Flyway, OpenAPI.

Java 17 / Spring Boot 3.3. Teaching retail core for portfolio review. Not a licensed bank,
not PCI, not a payment rail.

## Documentation

| Doc | Content |
|-----|---------|
| [docs/architecture.md](docs/architecture.md) | C4 context/container, component view, packaging |
| [docs/uml.md](docs/uml.md) | Sequences, class diagram, ER (Flyway V1–V3) |
| `/swagger-ui.html` | OpenAPI (when app is running) |

## Scope (honest)

| Capability | Status |
|------------|--------|
| JWT register / login | Implemented |
| Customer account open; opening funded via HOUSE debit + customer CREDIT (balanced journal) | Implemented |
| Account statement (`GET .../statement`) | Implemented |
| Freeze / unfreeze / close (zero balance) | Implemented |
| Transfer + compensating reversal (`POST .../reverse`) | Implemented |
| Append-only ledger + append-only audit_events | Implemented |
| Per-account and global debit=credit reconciliation | Implemented |
| Idempotency-Key on transfer and reverse | Implemented |
| `X-Request-Id` (echo / generate) on every response | Implemented |
| Pessimistic locks, fixed lock order | Implemented |
| Bucket4j rate limit on transfers (in-memory) | Implemented |
| Security response headers | Implemented |
| Flyway V1–V3, Hibernate validate | Implemented |
| Springdoc OpenAPI | Implemented |
| Testcontainers ITs (transfer, concurrency, lifecycle, recon, reversal) | Implemented |
| FX / multi-currency conversion | Not included |
| Multi-tenant orgs / roles | Not included |
| Cards, loans, interest accrual | Not included |
| Refresh tokens / revocation | Not included |
| Redis rate limit | Not included |
| KYC/AML / fraud engines | Not included |

## Architecture

Quick request path (full C4 + UML in [docs/](docs/)):

```mermaid
flowchart LR
    Client -->|JWT| Auth["AuthController /api/auth"]
    Client -->|JWT| AccApi["AccountController /api/accounts"]
    Client -->|JWT + Idempotency-Key| TrfApi["TransferController /api/transfers"]

    Auth --> AuthSvc["AuthService"] --> Users[("app_users")]

    AccApi --> AccSvc["AccountService"] --> Accounts[("accounts")]

    TrfApi --> RateLimit["RateLimitFilter (Bucket4j)"] --> TrfSvc["TransferService"]
    TrfSvc -->|SELECT ... FOR UPDATE, lowest id first| Accounts
    TrfSvc --> Transfers[("transfers")]
    TrfSvc --> Ledger[("ledger_entries")]
```

Transfer happy path:

```mermaid
sequenceDiagram
    actor User
    participant API as TransferController
    participant Svc as TransferService
    participant DB as PostgreSQL
    User->>API: POST /api/transfers + Idempotency-Key
    API->>Svc: transfer(...)
    Svc->>DB: lock accounts FOR UPDATE
    Svc->>DB: debit/credit + transfer + 2 ledger rows
    Svc-->>API: TransferResponse
    API-->>User: 201 Created
```

## Domain rules enforced

- Money movement only while `ACTIVE` (customer accounts). `FROZEN` / `CLOSED` reject debit/credit.
- Opening: HOUSE funding account is DEBITed, customer is CREDITed, same `journal_id` (Σ DEBIT = Σ CREDIT).
- Transfer / reversal: paired ledger lines; reversal posts compensating entries (ledger never updated).
- Close requires zero balance. Freeze/unfreeze/close are owner-only.
- Source account must belong to the caller for transfers.
- Account balance = Σ CREDIT − Σ DEBIT; global Σ DEBIT amounts = Σ CREDIT amounts.
- Idempotent transfer and reverse via `Idempotency-Key`.

## API

| Method | Path | Auth | Description |
|--------|------|------|--------------|
| `POST` | `/api/auth/register` | none | Create a user, returns a JWT |
| `POST` | `/api/auth/login` | none | Returns a JWT |
| `POST` | `/api/accounts` | JWT | Open an account (currency + opening balance) |
| `GET` | `/api/accounts` | JWT | List the caller's accounts |
| `GET` | `/api/accounts/{id}` | JWT | Get one owned account |
| `GET` | `/api/accounts/{id}/statement` | JWT | Ledger lines for the account |
| `POST` | `/api/accounts/{id}/freeze` | JWT | Freeze (blocks transfers) |
| `POST` | `/api/accounts/{id}/unfreeze` | JWT | Return to ACTIVE |
| `POST` | `/api/accounts/{id}/close` | JWT | Close when balance is zero |
| `POST` | `/api/transfers` | JWT + `Idempotency-Key` | Double-entry transfer |
| `POST` | `/api/transfers/{id}/reverse` | JWT + `Idempotency-Key` | Compensating reversal |
| `GET` | `/api/transfers/{id}` | JWT | Get a transfer the caller initiated |
| `GET` | `/actuator/health` | none | Liveness/readiness probe |

## Running it

### Everything in Docker (app + Postgres)

```bash
docker compose up --build
```

The API is then at `http://localhost:8080`.

### Local JVM against dockerized Postgres

```bash
docker compose up -d postgres
./mvnw spring-boot:run      # Linux / macOS
mvnw.cmd spring-boot:run    # Windows
```

### Example flow

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","email":"alice@example.com","password":"correct-horse-battery"}' \
  | jq -r .accessToken)

FROM=$(curl -s -X POST http://localhost:8080/api/accounts \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
  -d '{"currency":"USD","openingBalance":500.00}' | jq -r .id)

TO=$(curl -s -X POST http://localhost:8080/api/accounts \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
  -d '{"currency":"USD","openingBalance":0}' | jq -r .id)

curl -s -X POST http://localhost:8080/api/transfers \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: demo-transfer-1" \
  -d "{\"fromAccountId\":\"$FROM\",\"toAccountId\":\"$TO\",\"amount\":150.00,\"currency\":\"USD\"}"
```

## Tests

Two test suites, run at different Maven phases on purpose:

```bash
./mvnw test      # unit tests only — no Docker needed
./mvnw verify     # unit tests + Testcontainers integration tests — needs a Docker daemon
```

- **Unit tests** (`*Test.java`): `Account` invariants (funds, freeze/close), `TransferService`
  mocks (idempotency, lock order, ownership), `JwtService`.
- **Integration tests** (`*IT.java`, Failsafe + Testcontainers Postgres): happy-path transfer,
  concurrent overdraw safety, account freeze/close lifecycle, ledger reconciliation and
  append-only trigger.

CI (`.github/workflows/ci.yml`) runs `./mvnw -B verify` on `ubuntu-latest`, which has a Docker
daemon available, so both suites run on every push/PR.

## Configuration

| Property | Default | Purpose |
|----------|---------|---------|
| `BANKING_JWT_SECRET` | dev-only fallback in `application.yml` | HMAC signing key for JWTs — set a real 32+ byte secret outside local dev |
| `banking.jwt.expiration-minutes` | `60` | JWT lifetime |
| `banking.ratelimit.transfer.capacity` | `10` | Requests allowed per window on `POST /api/transfers` |
| `banking.ratelimit.transfer.refill-minutes` | `1` | Window length for the above |

## License

MIT — see [LICENSE](LICENSE).
