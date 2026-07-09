# E-Wallet API — Complete Technical Guide

> A reference for understanding, defending, and extending this API.  
> Covers what it does, how it works, why choices were made, and what to say in an interview.

---

## Table of Contents

1. [What Is This Project?](#1-what-is-this-project)
2. [High-Level Architecture](#2-high-level-architecture)
3. [Complete API Reference](#3-complete-api-reference)
4. [Authentication & Authorization](#4-authentication--authorization)
5. [Wallet Operations (The Critical Path)](#5-wallet-operations-the-critical-path)
6. [Database Design](#6-database-design)
7. [Error Handling & API Contract](#7-error-handling--api-contract)
8. [Cross-Cutting Concerns](#8-cross-cutting-concerns)
9. [Design Decisions: Why This, Not That](#9-design-decisions-why-this-not-that)
10. [What-If Scenarios & How We Handle Them](#10-what-if-scenarios--how-we-handle-them)
11. [Interview Q&A (Surface → Deep)](#11-interview-qa-surface--deep)
12. [Known Limitations & Honest Gaps](#12-known-limitations--honest-gaps)
13. [Testing Strategy](#13-testing-strategy)

---

## 1. What Is This Project?

A **REST API for a digital wallet**. Users can:

- Register and log in
- Create wallet accounts (identified by IBAN)
- Transfer money between accounts
- Deposit and withdraw funds
- View transaction history

It is a **single Spring Boot monolith** backed by **MySQL**. There is one deployable JAR, one database, no message queue, no microservices.

**Who is it for?** Portfolio projects, learning backend engineering, and technical interviews where you need to explain real design tradeoffs — not resume-driven complexity.

---

## 2. High-Level Architecture

### 2.1 Layered Monolith

```
┌─────────────────────────────────────────────────────────┐
│  HTTP Layer                                              │
│  AuthController · BankAccountController · TransactionCtrl│
└──────────────────────────┬──────────────────────────────┘
                           │ DTOs (request/response)
┌──────────────────────────▼──────────────────────────────┐
│  Service Layer                                           │
│  AuthService · BankAccountService · TransactionService   │
│  IdempotencyService · RateLimitService                   │
│  BalanceReconciliationService (scheduled)                │
└──────────────────────────┬──────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────┐
│  Repository Layer (Spring Data JPA)                      │
└──────────────────────────┬──────────────────────────────┘
                           │
                      MySQL Database
```

**Why layers?** Each layer has one job. Controllers don't touch the database. Repositories don't contain business rules. This keeps the code testable and readable.

### 2.2 Request Pipeline (Filters)

Every HTTP request passes through:

| Order | Component | Purpose |
|-------|-----------|---------|
| 1 | `RequestIdFilter` | Sets/propagates `X-Request-Id` in MDC for log tracing |
| 2 | `RateLimitFilter` | Throttles login by IP |
| 3 | `AuthTokenFilter` | Validates JWT, loads user into SecurityContext |
| 4 | Controller | Validates DTO, calls service |
| 5 | `@PreAuthorize` | Checks user has `ROLE_USER` authority |
| 6 | Service | Business logic + ownership checks |

Money operations also hit rate limiting inside `BankAccountService` (per user).

### 2.3 Package Layout

| Package | Responsibility |
|---------|----------------|
| `controller` | HTTP mapping, status codes, wraps responses in `ApiResponse<T>` |
| `service` | Business rules, transactions, authorization logic |
| `repository` | Database queries |
| `model` | JPA entities |
| `dto/request` | Incoming JSON shape + validation annotations |
| `dto/response` | Outgoing JSON shape |
| `dto/mapper` | MapStruct: entity ↔ DTO conversion |
| `security` | JWT, filters, `UserDetails`, `CurrentUserService` |
| `exception` | Custom exceptions + `GlobalExceptionHandler` |
| `config` | Spring beans: security, cache, CORS, filters |
| `validator` | Custom IBAN validation |

### 2.4 Domain Model (Entities)

```
User
 ├── bankAccounts (1:N)
 ├── refreshTokens (1:N)
 └── roles (M:N) → Role (ROLE_USER, ROLE_ADMIN)

BankAccount
 ├── user (N:1)
 ├── balance, version (optimistic lock)
 ├── iban (unique globally)
 └── fromTransactions / toTransactions (1:N)

Transaction
 ├── fromBankAccount, toBankAccount (N:1)
 ├── type (N:1) — e.g. Transfer, Deposit
 ├── operationType (enum) — TRANSFER, DEPOSIT, WITHDRAW, INITIAL_DEPOSIT
 ├── amount, status, referenceNumber (UUID)
 └── createdAt

IdempotencyRecord
 └── userId + idempotencyKey (unique) → transactionId
```

---

## 3. Complete API Reference

Base URL: `http://localhost:8080`  
All authenticated endpoints require: `Authorization: Bearer <access_token>`

### 3.1 Auth — `/api/v1/auth`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/signup` | No | Register new user |
| POST | `/login` | No | Returns access + refresh JWT |
| POST | `/refresh-token` | No* | New access token from refresh token |

*Refresh endpoint is whitelisted; client sends refresh token in `Authorization: Bearer` header.

**Signup body:**
```json
{
  "firstName": "John",
  "lastName": "Doe",
  "username": "johndoe",
  "email": "john@example.com",
  "password": "securePass123",
  "roles": ["ROLE_USER"]
}
```

**Login body:**
```json
{
  "username": "johndoe",
  "password": "securePass123"
}
```

**Login response (inside `data`):**
```json
{
  "token": "<access_jwt>",
  "refreshToken": "<refresh_jwt>",
  "id": 1,
  "username": "johndoe",
  "firstName": "John",
  "lastName": "Doe",
  "roles": ["ROLE_USER"]
}
```

### 3.2 Bank Accounts — `/api/v1/bank-accounts`

| Method | Path | Description |
|--------|------|-------------|
| GET | `/` | Paginated list of **current user's** accounts |
| GET | `/{id}` | Account by ID (must be owned) |
| GET | `/iban/{iban}` | Account by IBAN (must be owned) |
| GET | `/users/{userId}` | Accounts for user (must be current user) |
| POST | `/` | Create account |
| PUT | `/{id}` | Update account name/IBAN |
| DELETE | `/{id}` | Delete account |
| POST | `/transfer` | Transfer between accounts |
| POST | `/add-funds` | Deposit to account |
| POST | `/withdraw-funds` | Withdraw from account |

**Create account body:**
```json
{
  "iban": "GB82WEST12345698765432",
  "name": "Main Wallet",
  "balance": 100.00,
  "userId": 1
}
```

**Transfer / deposit / withdraw body:**
```json
{
  "fromBankAccountIban": "GB82WEST12345698765432",
  "toBankAccountIban": "GB82WEST98765432109876",
  "amount": 50.00,
  "description": "Payment for lunch",
  "typeId": 1
}
```

For deposit: `fromBankAccountIban` and `toBankAccountIban` are the same.  
For withdraw: same — both point to the account being debited.

**Optional header for money ops:**
```
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
```

**Pagination:** `GET /bank-accounts?page=0&size=20&sort=iban,asc`  
Max page size: 100.

### 3.3 Transactions — `/api/v1/transactions`

| Method | Path | Description |
|--------|------|-------------|
| GET | `/` | Paginated transactions for **current user** |
| GET | `/{id}` | Single transaction (must involve user's account) |
| GET | `/references/{uuid}` | Transaction by reference number |
| GET | `/users/{userId}` | Paginated transactions for user (must be current user) |

Transactions are **read-only** via API. They are created internally when money moves.

### 3.4 Operations — `/actuator`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/actuator/health` | No | Health check |
| GET | `/actuator/info` | No | App info |

### 3.5 Swagger

OpenAPI UI: `http://localhost:8080/swagger-ui.html`

---

## 4. Authentication & Authorization

### 4.1 Authentication (Who are you?)

**Mechanism:** Stateless JWT.

1. User logs in with username/password.
2. Server validates credentials via `AuthenticationManager` + BCrypt.
3. Server returns:
   - **Access token** — short-lived (~1 hour), used on every API call.
   - **Refresh token** — long-lived (~30 days), stored in DB, used to get new access tokens.
4. `AuthTokenFilter` reads `Authorization: Bearer <token>` on each request, validates signature/expiry, loads user.

**Why stateless JWT?** No server-side session storage. Scales horizontally if you add more app instances later. Good fit for mobile/SPA clients.

**Why refresh tokens in DB?** Allows revocation (mark `revoked=true`). Pure stateless JWTs can't be invalidated before expiry without a blocklist.

### 4.2 Authorization (What can you do?)

Two levels:

**Level 1 — Role check (controller):**
```java
@PreAuthorize("hasAuthority('ROLE_USER')")
```
Every protected endpoint requires the `ROLE_USER` authority.

**Level 2 — Resource ownership (service):**
```java
currentUserService.assertUserIdMatchesCurrentUser(userId);
assertAccountOwnedByCurrentUser(account);
```
Even with a valid JWT, you cannot read or modify another user's account.

**Q: Can User A transfer from User B's account?**  
No. `transferFunds` calls `assertAccountOwnedByCurrentUser(fromAccount)`. User A must own the source account.

**Q: Can User A transfer TO User B's account?**  
Yes. Destination account doesn't need to be owned by the sender — that's a normal peer transfer.

**Q: Why is `ROLE_ADMIN` defined but unused?**  
The enum exists for future admin endpoints (e.g. list all users). Currently all endpoints use `ROLE_USER`. Honest gap — admin features aren't implemented.

### 4.3 Password Security

- Stored as **BCrypt hash** — never plaintext.
- Validated on login via Spring Security's `DaoAuthenticationProvider`.

### 4.4 CSRF Disabled — Is That Safe?

Yes, for this API. CSRF protects cookie-based sessions. This API uses **Bearer tokens in headers**, not cookies. Browsers don't auto-send Authorization headers cross-site, so CSRF doesn't apply.

---

## 5. Wallet Operations (The Critical Path)

### 5.1 Transfer Flow (Step by Step)

```
Client POST /transfer + Idempotency-Key (optional)
        │
        ▼
IdempotencyService.execute()
  ├─ Key exists + same payload → return cached transaction ID
  ├─ Key exists + different payload → 422 IDEMPOTENCY_CONFLICT
  └─ New key or no key → continue
        │
        ▼
RateLimitService.checkTransferLimit(user)
        │
        ▼
Validate: amount > 0, from ≠ to
        │
        ▼
findByIbanForUpdate(from)  ← PESSIMISTIC WRITE LOCK
findByIbanForUpdate(to)    ← PESSIMISTIC WRITE LOCK
        │
        ▼
assertAccountOwnedByCurrentUser(from)
        │
        ▼
Check: from.balance >= amount
        │
        ▼
from.balance -= amount
to.balance += amount
bankAccountRepository.saveAll([from, to])
        │
        ▼
TransactionService.create(...)  ← audit record
        │
        ▼
Return { id: transactionId }
```

All of this runs inside **one `@Transactional` boundary**. Any exception → full rollback.

### 5.2 Why Pessimistic + Optimistic Locking?

| Mechanism | Where | Handles |
|-----------|-------|---------|
| **Pessimistic write lock** | `findByIbanForUpdate()` | Two transfers on same account at the same time — second waits |
| **Optimistic lock (`@Version`)** | `BankAccount.version` | Detects if row changed between read and write — returns 409 |

**Q: Why both?** Pessimistic lock serializes concurrent access during the transaction. Optimistic version is a safety net if something slips through (e.g. code path that doesn't use the lock query).

**Q: Why not Redis distributed lock?** Single database, single app instance for this project. DB locks are simpler and sufficient. Redis adds infrastructure for no gain at this scale.

### 5.3 Idempotency

**Problem:** Client sends transfer → network timeout → client retries → double debit?

**Solution:** Client sends `Idempotency-Key: <uuid>` header.

| Scenario | Behavior |
|----------|----------|
| First request with key | Executes transfer, stores key → transactionId mapping |
| Retry with same key + same body | Returns original transactionId, no second transfer |
| Same key + different body | 422 `IDEMPOTENCY_CONFLICT` |
| No key provided | Transfer executes normally (no dedup protection) |
| Two concurrent requests with same new key | DB unique constraint on `(userId, idempotencyKey)` — one wins, other gets cached result |

Request body is hashed (SHA-256 of IBANs + amount + typeId) to detect payload changes.

### 5.4 Deposit & Withdraw

Same transaction pattern as transfer, but:
- **Deposit:** credits one account (`from` = `to` = same IBAN)
- **Withdraw:** debits one account, checks sufficient balance first
- Both record `OperationType.DEPOSIT` or `WITHDRAW` on the transaction

### 5.5 Account Creation Side Effect

Creating an account with initial balance also creates an `INITIAL_DEPOSIT` transaction recording that starting balance. This gives a complete audit trail from account birth.

### 5.6 Reconciliation Job

Runs nightly (cron: `0 0 2 * * *`):

1. For each account, replay all transactions in chronological order.
2. Compute expected balance using `OperationType` rules.
3. Compare to stored `balance`.
4. Log `RECONCILIATION_MISMATCH` if they differ.

This catches bugs, partial failures, or manual DB tampering.

---

## 6. Database Design

### 6.1 Key Tables

| Table | Purpose |
|-------|---------|
| `user` | Users (note: `user` is a reserved word in MySQL — quoted in DDL) |
| `bank_account` | Wallet accounts with balance + version |
| `transaction` | Immutable record of every money movement |
| `type` | Transaction type lookup (Transfer, etc.) |
| `role` / `user_role` | RBAC |
| `refresh_token` | Stored refresh JWTs |
| `idempotency_record` | Idempotency key tracking |

### 6.2 Important Constraints & Indexes

```sql
-- BankAccount
UNIQUE(iban)                          -- global IBAN uniqueness
UNIQUE(user_id, iban)                 -- composite index
UNIQUE(user_id, name)                 -- one account name per user
version                               -- optimistic locking

-- Transaction
UNIQUE(reference_number)              -- UUID per transaction
INDEX(from_bank_account_id)
INDEX(to_bank_account_id)
INDEX(created_at)                     -- for history queries

-- IdempotencyRecord
UNIQUE(user_id, idempotency_key)
```

### 6.3 Why `BigDecimal` Not `double`?

Money must be exact. `double` has floating-point errors (`0.1 + 0.2 ≠ 0.3`). `BigDecimal` with `precision=19, scale=4` avoids that.

### 6.4 Why Not Double-Entry Ledger?

**Current model:** Single `balance` column updated in place + transaction log.

**Double-entry alternative:** Append-only ledger entries (debit/credit lines); balance = sum of entries.

| | Current | Double-Entry |
|--|---------|--------------|
| Simplicity | ✅ Easy to understand | ❌ More tables, more queries |
| Audit trail | Good (transaction log) | Excellent (immutable ledger) |
| Reconciliation | Nightly job replays txns | Built-in by design |
| Fit for project | ✅ Right size | Overkill for portfolio |

**Interview answer:** "For this scope, balance + transaction record is sufficient. In a regulated production system with compliance requirements, I'd move to append-only ledger entries."

### 6.5 JPA Fetch Strategy

- `@ManyToOne(fetch = LAZY)` on relationships — don't load user when you only need account ID.
- `@EntityGraph` on read queries that need nested data — avoids N+1 when mapping to DTOs.
- `open-in-view=false` — no lazy loading outside transaction boundary (prevents accidental N+1 in controllers).

---

## 7. Error Handling & API Contract

### 7.1 Success Response

```json
{
  "timestamp": 1717459200000,
  "message": "Success",
  "data": { }
}
```

### 7.2 Error Response

```json
{
  "status": 400,
  "message": "Human-readable message",
  "errorCode": "INSUFFICIENT_FUNDS",
  "errors": [
    { "field": "amount", "message": "Amount field cannot be null" }
  ]
}
```

`errors` array only appears for validation failures (422).

### 7.3 Complete Error Code Map

| errorCode | HTTP | When |
|-----------|------|------|
| `VALIDATION_ERROR` | 422 | Bean validation failed on request body |
| `INVALID_ARGUMENT` | 400 | Business rule violation (zero amount, same account transfer) |
| `INSUFFICIENT_FUNDS` | 400 | Balance too low |
| `NOT_FOUND` | 404 | Account/transaction doesn't exist |
| `ALREADY_EXISTS` | 409 | Duplicate IBAN, username, email |
| `FORBIDDEN` | 403 | Accessing another user's resource |
| `UNAUTHORIZED` | 401 | Bad/missing JWT or wrong login credentials |
| `CONCURRENT_MODIFICATION` | 409 | Optimistic lock conflict |
| `IDEMPOTENCY_CONFLICT` | 422 | Same idempotency key, different payload |
| `RATE_LIMIT_EXCEEDED` | 429 | Too many login or money requests |
| `INTERNAL_ERROR` | 500 | Unexpected server error |

### 7.4 Why 422 for Validation, Not 400?

422 Unprocessable Entity = syntax is fine, but semantics are wrong (invalid IBAN format, missing required field). 400 is for malformed requests. Minor distinction; either is acceptable; we chose 422 for validation consistency.

### 7.5 Empty Lists Return 200, Not 404

`GET /bank-accounts?page=0&size=20` with no accounts returns:
```json
{ "data": { "content": [], "totalElements": 0, ... } }
```
404 means "this resource identifier doesn't exist," not "collection is empty."

---

## 8. Cross-Cutting Concerns

### 8.1 Rate Limiting

| Endpoint | Limit | Key |
|----------|-------|-----|
| Login | 10/min | Client IP |
| Transfer/deposit/withdraw | 30/min | User ID |

Implementation: in-memory sliding window (`RateLimitService`). Resets on app restart. Not shared across multiple instances.

**Q: Why not Redis rate limiting?** Same reason as caching — single instance, no Redis infra. For multi-instance deployment, move to Redis or API gateway rate limiting.

### 8.2 Caching (Caffeine)

| Cache | Data | TTL |
|-------|------|-----|
| `transactionTypes` | Type entities | 60 min |
| `roles` | Role entities | 60 min |
| `userDetails` | UserDetails for JWT filter | 60 min |

**Never cached:** balances, transactions, account lists.

### 8.3 Request Correlation ID

Every request gets `X-Request-Id` (client-provided or server-generated). Logged in MDC:
```
2026-06-04 01:15:51 [http-nio-8080-exec-1] [abc-123-def] INFO BankAccountService - AUDIT operation=TRANSFER ...
```

Useful for tracing a single transfer across log lines.

### 8.4 Audit Logging

Every financial operation logs:
```
AUDIT operation=TRANSFER fromIban=... toIban=... amount=... transactionId=... userId=...
```

Not a separate audit table — structured logs. Sufficient for portfolio; production might add immutable audit table.

### 8.5 Configuration Profiles

| Profile | DB | ddl-auto | Secrets |
|---------|-----|----------|---------|
| `dev` | MySQL local | `update` | `application-local.properties` (gitignored) |
| `prod` | MySQL prod | `validate` | Environment variables |
| `test` | H2 in-memory | `create-drop` | Embedded in test config |

---

## 9. Design Decisions: Why This, Not That

### Architecture

| Decision | Why Yes | Alternative | Why Not |
|----------|---------|-------------|---------|
| **Monolith** | Simple deploy, one DB, easy to reason about | Microservices | 10 endpoints don't justify distributed complexity |
| **Layered architecture** | Clear separation, testable | Hexagonal/Clean Architecture | Over-abstraction for this codebase size |
| **DTOs + MapStruct** | API contract ≠ DB schema | Return entities directly | Leaks internal fields, couples API to JPA |
| **API versioning `/api/v1`** | Room to evolve without breaking clients | No version | Harder to migrate later |

### Security

| Decision | Why Yes | Alternative | Why Not |
|----------|---------|-------------|---------|
| **Custom JWT filter** | Full control, one code path | OAuth2 Resource Server + custom filter (old code had both) | Dual mechanisms caused config drift |
| **BCrypt passwords** | Industry standard, adaptive cost | SHA-256 hash | Too fast, vulnerable to brute force |
| **Ownership in service layer** | Business rule, not HTTP concern | Only `@PreAuthorize` on routes | Can't express "own this account" in SpEL cleanly |
| **CSRF disabled** | Bearer token API, not cookie session | Enable CSRF | Breaks stateless API clients |

### Data & Transactions

| Decision | Why Yes | Alternative | Why Not |
|----------|---------|-------------|---------|
| **`@Transactional` on service** | Atomic debit+credit+record | Manual commit/rollback | Error-prone, easy to miss a path |
| **Pessimistic lock on IBAN** | Serialize balance updates | Optimistic only | Lost updates under contention |
| **`@Version` on account** | Safety net for concurrent writes | No versioning | Silent balance corruption |
| **Idempotency keys** | Safe client retries | At-least-once without dedup | Double charges on network retry |
| **MySQL** | Relational data, ACID, familiar | MongoDB | Financial data fits relational model |

### Not Included (Deliberately)

| Technology | Why Not |
|------------|---------|
| **Kafka / RabbitMQ** | No async consumers, no event replay requirement |
| **Redis** | Single instance; Caffeine sufficient for reference data |
| **CQRS / Event Sourcing** | Read/write models don't diverge; adds operational cost |
| **Saga pattern** | Single DB transaction handles consistency |
| **API Gateway** | One service; gateway adds hop without benefit |
| **Docker/K8s in repo** | Can be added; not required to demonstrate backend skills |

---

## 10. What-If Scenarios & How We Handle Them

### Money & Concurrency

| What if... | What happens |
|------------|--------------|
| Two transfers from same account simultaneously? | Second waits on pessimistic lock; proceeds after first commits |
| Transfer succeeds but client gets timeout? | Client retries with same `Idempotency-Key` → gets same transaction ID, no double debit |
| Transfer amount exceeds balance? | `InsufficientFundsException` → 400 `INSUFFICIENT_FUNDS`; nothing saved |
| Transfer to same account? | `IllegalArgumentException` → 400 before touching DB |
| Transfer amount is zero or negative? | Rejected in `validateAmount()` → 400 |
| Account updated by admin in DB during transfer? | `@Version` mismatch → 409 `CONCURRENT_MODIFICATION` |
| Transfer succeeds, idempotency record save fails? | Whole transaction rolls back (same `@Transactional`) |

### Security

| What if... | What happens |
|------------|--------------|
| No JWT on protected endpoint? | 401 Unauthorized |
| Expired JWT? | 401 — filter rejects token |
| Valid JWT but wrong user for account? | 403 Forbidden — ownership check in service |
| Brute force login? | Rate limited at 10/min per IP → 429 |
| Refresh token revoked? | 401 "Refresh token is invalid or revoked" |
| Wrong password on login? | 401 "Invalid username or password" (not 500) |

### Data Integrity

| What if... | What happens |
|------------|--------------|
| Duplicate IBAN on account create? | 409 `ALREADY_EXISTS` |
| Duplicate username on signup? | 409 `ALREADY_EXISTS` |
| Invalid IBAN format? | 422 validation error |
| Delete account with transactions? | JPA may block or cascade depending on FK constraints — test before relying on this in prod |
| Balance drifts from transaction sum? | Nightly reconciliation logs mismatch |

### API Usage

| What if... | What happens |
|------------|--------------|
| Page size = 1000? | Capped at 100 by `PageableHandlerMethodArgumentResolverCustomizer` |
| Empty transaction list? | 200 with empty page (not 404) |
| Invalid transaction UUID in path? | 400 bad request from Spring |
| Same idempotency key, different amount? | 422 `IDEMPOTENCY_CONFLICT` |

---

## 11. Interview Q&A (Surface → Deep)

### Surface Level

**Q: What does this project do?**  
A digital wallet REST API. Users manage accounts and move money. Built with Spring Boot, MySQL, JWT auth.

**Q: What tech stack?**  
Java 17, Spring Boot 3, Spring Data JPA, MySQL, JWT, MapStruct, Caffeine cache, JUnit/Mockito.

**Q: How do you authenticate?**  
JWT access token in Authorization header. Refresh token for getting new access tokens without re-login.

**Q: What's the base URL structure?**  
`/api/v1/` prefix. Resources: `auth`, `bank-accounts`, `transactions`.

---

### Mid Level

**Q: Walk me through a transfer.**  
Client POSTs to `/bank-accounts/transfer` with from/to IBAN and amount. Service acquires pessimistic locks on both accounts, verifies ownership and sufficient balance, debits/credits in one transaction, creates audit transaction record, returns transaction ID.

**Q: How do you prevent double-spending?**  
Pessimistic write locks during balance update + single `@Transactional` boundary. Optional idempotency key for client retries.

**Q: How is authorization different from authentication?**  
AuthN: JWT proves who you are. AuthZ: service checks you own the account you're accessing. Both are required.

**Q: Why MapStruct instead of manual mapping?**  
Compile-time code generation, type-safe, no reflection. Keeps mappers separate from business logic.

**Q: Why separate request/response DTOs from entities?**  
API contract shouldn't expose JPA internals (lazy proxies, password field, version column). DTOs give explicit control over what's sent/received.

**Q: How do you handle errors consistently?**  
`@RestControllerAdvice` catches all exceptions, maps to `ErrorResponse` with HTTP status + `errorCode`.

---

### Deep Level

**Q: Why pessimistic AND optimistic locking? Isn't one enough?**  
Pessimistic lock prevents concurrent reads of stale balance during the critical section. Optimistic version catches edge cases where a code path bypasses the lock or admin modifies data. Belt and suspenders for financial data.

**Q: Explain the idempotency implementation.**  
Store `(userId, idempotencyKey) → transactionId` after successful operation. Hash request body to detect key reuse with different payload. Unique DB constraint handles race between concurrent identical requests.

**Q: What happens if idempotency record is saved but transfer rolled back?**  
They're in the same `@Transactional` method chain — if transfer fails, idempotency record isn't committed either. If they were separate transactions, you'd have orphaned keys — we avoided that.

**Q: Why is `open-in-view=false`?**  
With `true`, Hibernate session stays open during JSON serialization in controller — lazy associations can trigger N+1 queries silently. `false` forces all data loading inside service `@Transactional` boundary.

**Q: How would you scale this horizontally?**  
Run multiple app instances behind load balancer. Move rate limiting and cache to Redis. DB becomes shared bottleneck — connection pooling, read replicas for transaction history. Idempotency and locks still work because they're DB-backed.

**Q: What's the weakest part of this design?**  
In-memory rate limiting doesn't work multi-instance. Balance is updated in place rather than append-only ledger. Idempotency keys never expire (table grows). Admin role exists but no admin endpoints.

**Q: How would you add admin functionality?**  
New `@PreAuthorize("hasAuthority('ROLE_ADMIN')")` endpoints. Admin bypasses ownership checks but should still audit log. Separate admin controller package.

**Q: Why not event-driven architecture?**  
No consumer needs async notification yet. Transfer is synchronous — user waits for confirmation. Events add complexity (ordering, idempotency, dead letter queues) without clear benefit at this scale.

**Q: How do you test financial logic?**  
Unit tests mock repositories (`BankAccountServiceTest`). Integration test loads Spring context with H2 (`EWalletApplicationTests`). Idempotency and reconciliation have dedicated unit tests. No full end-to-end API test yet — honest gap.

**Q: ddl-auto=update in dev — safe for prod?**  
No. Prod uses `validate` — Hibernate checks schema matches entities but doesn't modify it. Dev uses `update` for convenience. Migrations should use Flyway/Liquibase in production — not yet in this project.

---

## 12. Known Limitations & Honest Gaps

Be upfront about these in interviews:

| Gap | Impact | What you'd do next |
|-----|--------|-------------------|
| No Flyway/Liquibase migrations | Schema managed by Hibernate in dev | Add versioned SQL migrations |
| Idempotency keys never expire | Table grows indefinitely | TTL cleanup job (e.g. 30 days) |
| In-memory rate limiting | Doesn't work with multiple instances | Redis or gateway-level limiting |
| No email verification on signup | Anyone can register with any email | Verification flow + token |
| `ROLE_ADMIN` unused | No admin panel API | Admin endpoints with separate auth |
| Transfer to any IBAN | Can't restrict to verified beneficiaries | Beneficiary whitelist per user |
| No API integration tests | `@WebMvcTest` / MockMvc missing | Add full HTTP-level tests |
| Password in local config file | Dev convenience | Secrets manager in prod (Vault, AWS SM) |
| Self-transaction IBAN handling | Deposit/withdraw use same from/to IBAN | Works but API shape is slightly awkward |
| Delete account with history | Cascade behavior untested in prod | Soft delete or archive instead |

---

## 13. Testing Strategy

### What's Tested

| Test Class | Type | Covers |
|------------|------|--------|
| `EWalletApplicationTests` | Integration | Spring context loads with H2 |
| `BankAccountServiceTest` | Unit | Insufficient funds, same-account, zero amount |
| `IdempotencyServiceTest` | Unit | Cache hit, conflict, new key persistence |
| `BalanceReconciliationServiceTest` | Unit | Balance replay logic |
| `GlobalExceptionHandlerTest` | Unit | Error response shape and codes |

### What's NOT Tested (Yet)

- Full HTTP flow with MockMvc
- JWT filter integration
- Concurrent transfer stress test
- Repository layer with `@DataJpaTest`

### How to Run

```bash
./mvnw test
```

Test profile uses H2 in-memory DB with seed data (`data.sql` inserts Role and Type).

---

## Quick Reference Card

```
Auth:     POST /api/v1/auth/login → Bearer token on all requests
Transfer: POST /api/v1/bank-accounts/transfer + optional Idempotency-Key
Errors:   { status, message, errorCode, errors? }
Profiles: dev (local MySQL) · prod (validate schema) · test (H2)
Health:   GET /actuator/health
Docs:     /swagger-ui.html
Logs:     Look for [requestId] and AUDIT lines
```

---

*Last updated to match codebase on branch `master`. For setup instructions see [README.md](../README.md).*
