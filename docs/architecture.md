# Architecture

C4 and component views for RetailBankingCore. Portfolio service, not a production bank core.

## C4 Context

```mermaid
C4Context
  title Retail Banking Core - System Context
  Person(user, "Banking user", "Registers, opens accounts, transfers money")
  System(core, "Retail Banking Core", "JWT auth, accounts, double-entry transfers")
  SystemDb(pg, "PostgreSQL", "Users, accounts, transfers, ledger")
  Rel(user, core, "HTTPS / JSON")
  Rel(core, pg, "JDBC via HikariCP")
```

## C4 Container

```mermaid
C4Container
  title Retail Banking Core - Containers
  Person(user, "Banking user")
  Container(api, "Spring Boot API", "Java 17 / Spring Boot 3.3", "Auth, accounts, transfers, Actuator")
  ContainerDb(pg, "PostgreSQL 16", "Flyway schema", "Durable store")
  Container_Ext(ci, "GitHub Actions", "CI", "mvn verify + Testcontainers")
  Rel(user, api, "HTTPS")
  Rel(api, pg, "SQL")
  Rel(ci, api, "build / test")
```

## Component view (inside the API)

```mermaid
flowchart TB
  subgraph http [HTTP]
    AuthC[AuthController]
    AccC[AccountController]
    TrfC[TransferController]
    Health[/actuator/health]
  end

  subgraph security [Security]
    JwtF[JwtAuthenticationFilter]
    RateF[RateLimitFilter Bucket4j]
  end

  subgraph app [Services]
    AuthS[AuthService]
    AccS[AccountService]
    TrfS[TransferService]
    JwtS[JwtService]
  end

  subgraph data [Persistence]
    Users[(app_users)]
    Accounts[(accounts)]
    Transfers[(transfers)]
    Ledger[(ledger_entries)]
  end

  AuthC --> AuthS --> Users
  AuthS --> JwtS
  AccC --> AccS --> Accounts
  AccS --> Ledger
  TrfC --> RateF --> TrfS
  TrfS --> Accounts
  TrfS --> Transfers
  TrfS --> Ledger
  JwtF --> JwtS
```

## Ledger rules

- `OPENING`: HOUSE funding DEBIT + customer CREDIT, shared `journal_id`
- `TRANSFER` / `REVERSAL`: paired lines with `transfer_id`; reversal never mutates old rows
- Ledger and audit tables are append-only (Postgres triggers)
- `accounts.balance` = Σ CREDIT − Σ DEBIT; global Σ DEBIT = Σ CREDIT

## Runtime packaging

- Local / CI: JVM + Postgres (Compose or Testcontainers)
- Schema only via Flyway (`V1`–`V3`), Hibernate `ddl-auto=validate`
- OpenAPI UI: `/swagger-ui.html`
