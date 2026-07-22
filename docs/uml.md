# UML

Sequence, class, and data model diagrams for **retail-banking-core**.

## Transfer sequence (happy path)

```mermaid
sequenceDiagram
  actor User
  participant API as TransferController
  participant RL as RateLimitFilter
  participant Svc as TransferService
  participant Acc as AccountRepository
  participant DB as PostgreSQL

  User->>API: POST /api/transfers + JWT + Idempotency-Key
  API->>RL: check bucket
  RL->>Svc: transfer(username, key, request)
  Svc->>DB: find existing transfer by (key, user)
  alt already processed
    Svc-->>API: original TransferResponse
  else new transfer
    Svc->>Acc: lock both accounts FOR UPDATE (lower id first)
    Acc->>DB: SELECT ... FOR UPDATE
    Svc->>Svc: debit source / credit destination
    Svc->>DB: insert transfer + DEBIT + CREDIT ledger rows
    Svc-->>API: 201 TransferResponse
  end
  API-->>User: JSON body
```

## Concurrent transfer (same source)

```mermaid
sequenceDiagram
  participant T1 as Transfer A
  participant T2 as Transfer B
  participant DB as PostgreSQL accounts row

  T1->>DB: FOR UPDATE source (and dest)
  T2->>DB: waits on same row lock
  T1->>DB: debit, commit, release
  T2->>DB: lock acquired, sees new balance
  alt enough funds
    T2->>DB: debit and commit
  else insufficient
    T2-->>T2: InsufficientFundsException
  end
```

## Domain class diagram

```mermaid
classDiagram
  direction TB

  class AppUser {
    UUID id
    String username
    String email
    String passwordHash
    Instant createdAt
  }

  class Account {
    UUID id
    UUID ownerId
    String accountNumber
    String currency
    BigDecimal balance
    AccountStatus status
    +debit(amount)
    +credit(amount)
  }

  class AccountStatus {
    <<enumeration>>
    ACTIVE
    CLOSED
  }

  class Transfer {
    UUID id
    String idempotencyKey
    UUID initiatedBy
    UUID fromAccountId
    UUID toAccountId
    BigDecimal amount
    String currency
    Instant createdAt
  }

  class LedgerEntry {
    UUID id
    UUID transferId
    UUID accountId
    LedgerEntryType entryType
    BigDecimal amount
  }

  class LedgerEntryType {
    <<enumeration>>
    DEBIT
    CREDIT
  }

  class TransferService {
    +transfer(username, key, request) TransferResponse
    +getForUser(username, id) TransferResponse
  }

  class AccountService {
    +create(username, request) AccountResponse
    +listForUser(username) List
  }

  class AuthService {
    +register(request) AuthResponse
    +login(request) AuthResponse
  }

  AppUser "1" --> "*" Account : owns
  Transfer "1" --> "2" LedgerEntry : posts
  Account --> AccountStatus
  LedgerEntry --> LedgerEntryType
  TransferService --> Account : locks and mutates
  TransferService --> Transfer : persists
  TransferService --> LedgerEntry : persists
  AccountService --> Account
  AuthService --> AppUser
```

## ER (Flyway V1)

```mermaid
erDiagram
  APP_USERS ||--o{ ACCOUNTS : owns
  APP_USERS ||--o{ TRANSFERS : initiates
  ACCOUNTS ||--o{ TRANSFERS : from_or_to
  TRANSFERS ||--|{ LEDGER_ENTRIES : has

  APP_USERS {
    uuid id PK
    varchar username UK
    varchar email UK
    varchar password_hash
    timestamptz created_at
  }

  ACCOUNTS {
    uuid id PK
    uuid owner_id FK
    varchar account_number UK
    varchar currency
    numeric balance
    varchar status
    timestamptz created_at
  }

  TRANSFERS {
    uuid id PK
    varchar idempotency_key
    uuid initiated_by FK
    uuid from_account_id FK
    uuid to_account_id FK
    numeric amount
    varchar currency
    timestamptz created_at
  }

  LEDGER_ENTRIES {
    uuid id PK
    uuid transfer_id FK
    uuid account_id FK
    varchar entry_type
    numeric amount
    timestamptz created_at
  }
```
