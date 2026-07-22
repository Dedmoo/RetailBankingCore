# Architecture

C4 and component views for **retail-banking-core**. This is a portfolio MVP, not a production bank core.

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
  TrfC --> RateF --> TrfS
  TrfS --> Accounts
  TrfS --> Transfers
  TrfS --> Ledger
  JwtF --> JwtS
```

## Runtime packaging

- Local / CI: JVM process + Postgres (Docker Compose or Testcontainers)
- `docker compose up --build`: app image + Postgres 16 on one network
- Schema only via Flyway (`V1__init_schema.sql`), no `ddl-auto`
