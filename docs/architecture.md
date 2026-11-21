# System Architecture: Smart Public Bus Tracking & Management System

## 1. High-Level Architecture Overview

The system is engineered as an enterprise-grade, distributed transit management platform comprising three primary tiers:

```
┌───────────────────────────────────────────────────────────┐
│                      Client Layer                         │
├─────────────────────────────┬─────────────────────────────┤
│   Android Application       │       Web Application       │
│  (Kotlin / Jetpack Compose) │    (React / TypeScript)     │
│   • Passenger Dashboard     │    • Bus Owner Portal       │
│   • Driver Console          │    • System Admin Portal    │
│   • Conductor Console       │                             │
└──────────────┬──────────────┴──────────────┬──────────────┘
               │ HTTPS (REST) / WSS (Socket) │
               ▼                             ▼
┌───────────────────────────────────────────────────────────┐
│                   Backend API & Gateway                   │
│               (Node.js / Express / TypeScript)            │
│   • JWT Auth & RBAC Middleware                            │
│   • Transit Routing & Scheduling Engine                   │
│   • Real-Time Telemetry & Stale Detection                 │
│   • Fare Calculation & Idempotent Payments                │
│   • Offline Sync & Duplicate Event Resolution             │
│   • Real-Time WebSocket Broadcasts (Socket.IO)            │
└──────────────────────────────┬────────────────────────────┘
                               │ Prisma ORM
                               ▼
┌───────────────────────────────────────────────────────────┐
│                    Shared Data Store                      │
│                  (PostgreSQL Database)                    │
│   • Normalized relational schema with strict foreign keys │
│   • Organization data isolation (Multi-tenant)            │
│   • Audit logs & idempotent event constraints             │
└───────────────────────────────────────────────────────────┘
```

---

## 2. Multi-Role Authorization Matrix

Authentication is verified server-side through cryptographically signed HMAC SHA-256 JWT access tokens (15-minute lifespan) and database-persisted refresh tokens (7-day lifespan with rotation on use).

| Endpoint Group | Passenger | Driver | Conductor | Bus Owner | Admin |
|---|:---:|:---:|:---:|:---:|:---:|
| `POST /auth/register` | ✅ | ❌ | ❌ | ❌ | ❌ |
| `POST /auth/login` | ✅ | ✅ | ✅ | ✅ | ✅ |
| `GET /routes` & `/stops` | ✅ | ✅ | ✅ | ✅ | ✅ |
| `POST /routes` & `/stops` | ❌ | ❌ | ❌ | ✅ | ✅ |
| `GET /buses` | ✅ | ✅ | ✅ | ✅ (Own Org) | ✅ (All) |
| `POST /buses` (CRUD) | ❌ | ❌ | ❌ | ✅ (Own Org) | ✅ (All) |
| `POST /trips/:id/start` & `/end` | ❌ | ✅ (Assigned) | ❌ | ✅ | ✅ |
| `POST /trips/:id/location` | ❌ | ✅ (Active Run)| ❌ | ❌ | ✅ |
| `POST /tickets` (Book) | ✅ | ❌ | ❌ | ❌ | ❌ |
| `POST /tickets/verify` | ❌ | ❌ | ✅ | ✅ | ✅ |
| `POST /tickets/:id/board` & `/exit`| ❌ | ❌ | ✅ | ✅ | ✅ |
| `POST /tickets/onboard` (Cash) | ❌ | ❌ | ✅ | ✅ | ✅ |
| `POST /events/sync` (Offline) | ❌ | ✅ | ✅ | ✅ | ✅ |
| `GET /admin/dashboard` | ❌ | ❌ | ❌ | ✅ (Own Org) | ✅ (All) |
| `GET /admin/reports` | ❌ | ❌ | ❌ | ✅ (Own Org) | ✅ (All) |
| `POST /journey-sharing` | ✅ | ❌ | ❌ | ❌ | ❌ |

---

## 3. Real-Time Telemetry & Stale Data Prevention

### Principles:
1. **Never present stale data as live:** If the latest GPS coordinate update is older than 300 seconds (5 minutes), the backend explicitly attaches `isStale: true` and warning metadata. The web admin and Android UI render an amber alert banner informing operators and passengers of stale telemetry.
2. **Foreground GPS Isolation:** The Android driver app operates a dedicated `ForegroundService` with a persistent notification. Location tracking only activates upon starting an assigned trip and automatically terminates when the driver ends the trip.

---

## 4. Passenger Counting Mathematics

A ticket purchase alone **never** increases the onboard count. The count is strictly calculated from real physical events:

$$\text{Current Onboard} = \sum \text{Valid Boarding Events} - \sum \text{Valid Exit Events}$$

- **Prepaid passengers:** Boarding recorded when conductor scans the QR code.
- **Onboard cash passengers:** Boarding recorded when conductor prints/issues the ticket.
- **Exits:** Recorded when conductor taps passenger exit at destination stops.
- **Duplicate Prevention:** Unique constraints on `(ticketId)` for boarding and exit events prevent double counting.
