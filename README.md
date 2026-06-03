# E-Wallet Backend

REST API for a digital wallet platform. Users can register, manage bank accounts, and perform transfers, deposits, and withdrawals with JWT-based authentication.

Built with Spring Boot as a single deployable service — no microservices, no message brokers. The focus is on correct financial operations, clear layering, and patterns you'd actually use in a production monolith.

## Tech Stack

| Layer | Technology |
|-------|------------|
| Runtime | Java 17 |
| Framework | Spring Boot 3.0 |
| Persistence | Spring Data JPA, MySQL |
| Security | Spring Security, JWT (JJWT) |
| Mapping | MapStruct |
| Validation | Jakarta Bean Validation |
| Cache | Caffeine (in-memory) |
| API Docs | SpringDoc OpenAPI |
| Testing | JUnit 5, Mockito, H2 |

## System Architecture

The application follows a **layered monolith** architecture. HTTP concerns stay in controllers; business rules live in services; data access is isolated in repositories.

```
Client (Web / Mobile)
        │
        ▼
┌───────────────────────────────────────────┐
│  Filters                                   │
│  RequestId → RateLimit → JWT Auth          │
└───────────────────────────────────────────┘
        │
        ▼
┌───────────────────────────────────────────┐
│  Controller Layer                          │
│  Auth · BankAccount · Transaction          │
│  (DTO in / out, validation)                │
└───────────────────────────────────────────┘
        │
        ▼
┌───────────────────────────────────────────┐
│  Service Layer                             │
│  Auth · BankAccount · Transaction          │
│  Idempotency · Reconciliation (scheduled)  │
└───────────────────────────────────────────┘
        │
        ▼
┌───────────────────────────────────────────┐
│  Repository Layer (Spring Data JPA)        │
└───────────────────────────────────────────┘
        │
        ▼
     MySQL
```

### Request flow (authenticated endpoint)

1. `RequestIdFilter` assigns or propagates `X-Request-Id` for log correlation.
2. `RateLimitFilter` throttles login attempts by IP.
3. `AuthTokenFilter` validates the JWT and loads the user into the security context.
4. Controller validates the request DTO and delegates to a service.
5. Service enforces **resource ownership** (users only access their own accounts).
6. For money operations: balance update and transaction record happen in a single `@Transactional` boundary.

### Core domain model

```
User ──< BankAccount
  │         │
  │         ├──< Transaction (from)
  │         └──< Transaction (to)
  └──< RefreshToken

Transaction → Type (Transfer, Deposit, Withdraw, etc.)
IdempotencyRecord (per-user duplicate request tracking)
```

## Key Design Decisions

**Atomic wallet operations** — Transfers debit one account and credit another in one transaction. If anything fails, everything rolls back.

**Concurrency control** — Pessimistic write locks during balance updates, plus optimistic locking (`@Version`) on accounts to catch concurrent modifications.

**Idempotency** — Money endpoints accept an optional `Idempotency-Key` header so clients can safely retry without double-charging.

**Authorization beyond authentication** — JWT proves identity; services verify the authenticated user owns the account being accessed.

**Cache only reference data** — Transaction types, roles, and user details are cached. Account balances are never cached.

**Nightly reconciliation** — A scheduled job replays transactions and compares computed balances against stored values.

## Project Structure

```
src/main/java/com/ewallet/
├── config/          # Security, cache, filters, OpenAPI
├── controller/    # REST endpoints
├── dto/             # Request/response objects and MapStruct mappers
├── exception/       # Custom exceptions and global handler
├── model/           # JPA entities
├── repository/      # Data access interfaces
├── security/        # JWT, filters, UserDetails
├── service/         # Business logic
└── validator/       # Custom validation (IBAN)
```

## API Overview

Base URL: `http://localhost:8080`

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/api/v1/auth/signup` | No | Register user |
| POST | `/api/v1/auth/login` | No | Get access + refresh token |
| POST | `/api/v1/auth/refresh-token` | No | Refresh access token |
| GET | `/api/v1/bank-accounts` | Yes | List current user's accounts (paginated) |
| POST | `/api/v1/bank-accounts` | Yes | Create account |
| POST | `/api/v1/bank-accounts/transfer` | Yes | Transfer between accounts |
| POST | `/api/v1/bank-accounts/add-funds` | Yes | Deposit |
| POST | `/api/v1/bank-accounts/withdraw-funds` | Yes | Withdraw |
| GET | `/api/v1/transactions` | Yes | List current user's transactions |
| GET | `/actuator/health` | No | Health check |

Interactive docs: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

### Authentication

Send the access token on protected routes:

```
Authorization: Bearer <access_token>
```

For transfer, deposit, and withdraw, optionally include:

```
Idempotency-Key: <unique-uuid>
```

### Response format

Success:
```json
{
  "timestamp": 1717459200000,
  "message": "Success",
  "data": { }
}
```

Error:
```json
{
  "status": 400,
  "message": "Transfer amount should be higher than or equal to BankAccount balance",
  "errorCode": "INSUFFICIENT_FUNDS"
}
```

## Getting Started

### Prerequisites

- Java 17+
- Maven 3.8+
- MySQL 8.x

### Database setup

```sql
CREATE DATABASE ewallet_new;
```

### Configuration

The app uses Spring profiles. For local development:

1. Copy the local config template:
   ```bash
   cp src/main/resources/application-local.properties.example \
      src/main/resources/application-local.properties
   ```

2. Set your MySQL credentials in `application-local.properties`:
   ```properties
   spring.datasource.username=your_user
   spring.datasource.password=your_password
   ```

This file is gitignored. JWT secrets for dev are provided in `application-dev.properties`.

| Profile | Purpose |
|---------|---------|
| `dev` | Local development (`ddl-auto=update`) |
| `prod` | Production (`ddl-auto=validate`, no SQL logging) |
| `test` | Unit/integration tests (H2 in-memory) |

### Run

```bash
./mvnw spring-boot:run
```

Or set active profile to `dev` in your IDE and run `EWalletApplication`.

### Test

```bash
./mvnw test
```

## Configuration Reference

| Property | Default | Description |
|----------|---------|-------------|
| `app.rate-limit.login.max-requests` | 10 | Login attempts per minute per IP |
| `app.rate-limit.transfer.max-requests` | 30 | Money ops per minute per user |
| `app.pagination.max-page-size` | 100 | Max page size on list endpoints |
| `app.reconciliation.cron` | `0 0 2 * * *` | Nightly balance check schedule |
| `app.cache.reference-data.ttl-minutes` | 60 | Cache TTL for roles/types |

## Error Codes

| Code | HTTP | Meaning |
|------|------|---------|
| `INSUFFICIENT_FUNDS` | 400 | Balance too low |
| `FORBIDDEN` | 403 | Accessing another user's resource |
| `NOT_FOUND` | 404 | Resource does not exist |
| `ALREADY_EXISTS` | 409 | Duplicate IBAN, username, etc. |
| `CONCURRENT_MODIFICATION` | 409 | Account modified during transfer |
| `IDEMPOTENCY_CONFLICT` | 422 | Same key, different payload |
| `RATE_LIMIT_EXCEEDED` | 429 | Too many requests |

## License

This project is for educational and portfolio use.
