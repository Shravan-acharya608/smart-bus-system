# Smart Public Bus Tracking and Management System

A production-ready transit management platform built across three integrated tiers:
- **Android Mobile App (`android-app/`)**: Native Kotlin, Jetpack Compose, Material 3, MVVM, Retrofit, Room, DataStore, Google Maps, Foreground GPS Service, and ZXing QR verification. Supports **Passenger**, **Driver**, and **Conductor** dashboards in a single unified application.
- **Web Admin Portal (`web-admin/`)**: React.js with TypeScript, Tailwind CSS, Recharts, and React Router for **Bus Owners** and **System Administrators**.
- **Shared Backend API Server (`backend/`)**: Node.js, Express, TypeScript, Socket.IO, Prisma ORM, and PostgreSQL with role-based access control (RBAC).

---

## Project Structure

```
smart-bus-system/
├── android-app/             # Android Studio Kotlin project
│   ├── app/                 # App module (Compose, Retrofit, Room, Maps, ZXing)
│   ├── gradle/wrapper/      # Gradle wrapper binaries & properties
│   ├── build.gradle.kts     # Root build file
│   └── settings.gradle.kts  # Root settings file
├── web-admin/               # React / TypeScript / Tailwind web application for VS Code
│   ├── src/
│   │   ├── components/      # UI components & responsive sidebar layout
│   │   ├── pages/           # Dashboard, Buses, Routes, Trips, Staff, Tickets, Live Map, Reports
│   │   ├── store/           # Zustand authentication store
│   │   └── lib/             # Axios API client with automatic token injection
├── backend/                 # Node.js API and WebSocket real-time server
│   ├── prisma/              # Prisma schema and idempotent database seed script
│   ├── src/
│   │   ├── controllers/     # Bus, Route, Trip, Ticket, Payment, Sync, and Admin controllers
│   │   ├── middleware/      # JWT verification, Role authorization, and Error handling
│   │   ├── routes/          # Versioned REST endpoints (/api/v1/...)
│   │   └── socket/          # Socket.IO real-time telemetry rooms (trip:{id})
├── docs/                    # Technical documentation
│   ├── architecture.md      # Multi-tier architecture, telemetry, and RBAC matrix
│   ├── database_schema.md   # PostgreSQL entity-relationship models & constraints
│   └── api_documentation.md # REST API endpoint reference and payload specifications
├── docker-compose.yml       # Local PostgreSQL database and service orchestration
├── .env.example             # Documented environment variables template
└── README.md                # Complete setup and execution guide
```

---

## Quick Start Guide

### Prerequisites
- **Node.js**: v20.x or newer
- **npm**: v10.x or newer
- **Android Studio**: Hedgehog (2023.1.1) or newer with Android SDK 35
- **PostgreSQL**: v15+ (or Docker Desktop)
- **JDK**: Java 17 or 21

---

## 1. Database Setup

### Option A: Using Docker (Recommended)
From `smart-bus-system/`:
```bash
docker-compose up -d postgres
```

### Option B: Local PostgreSQL Installation
Execute in `psql`:
```sql
CREATE USER smartbus WITH PASSWORD 'smartbus_dev_pass';
CREATE DATABASE smartbus_db OWNER smartbus;
```

---

## 2. Shared Backend Setup

```bash
cd backend
cp ../.env.example .env
npm install
npx prisma generate
npx prisma migrate dev --name init
npm run seed
npm run dev
```

The backend server starts on **http://localhost:3000**.
Health check: `http://localhost:3000/api/v1/health`

### Pre-Configured Development Seed Accounts

| Role | Email | Password | Access Level |
|---|---|---|---|
| **System Admin** | `admin@smartbus.local` | `Admin@123456` | Full platform control |
| **Bus Owner** | `owner@smartbus.local` | `Owner@123456` | Own fleet & routes management |
| **Driver** | `driver@smartbus.local` | `Driver@123456` | Assigned trip start & GPS transmit |
| **Conductor** | `conductor@smartbus.local` | `Conductor@123456` | QR scan, cash tickets & offline sync |
| **Passenger** | `passenger@smartbus.local` | `Passenger@123456` | Route search, tickets & live tracking |

---

## 3. Web Admin Application Setup (VS Code)

```bash
cd web-admin
npm install
npm run dev
```

Open **http://localhost:5173** in your browser. Log in with the **Admin** or **Bus Owner** credentials above.

---

## 4. Android Application Setup (Android Studio)

1. Launch **Android Studio**.
2. Select **Open** and choose the `smart-bus-system/android-app` directory.
3. Allow Gradle to synchronize dependencies.
4. Select an Android Emulator or connected physical device.
5. Click **Run 'app'** (Shift + F10).

### Backend Connectivity:
- **Android Emulator**: Pre-configured to `http://10.0.2.2:3000/api/v1/` to automatically reach your computer's localhost.
- **Physical Device**: Tap the **Settings** icon on the Login screen top bar and enter your PC's local WiFi IP (e.g., `http://192.168.1.100:3000/api/v1/`). Ensure port 3000 is open in your firewall.

---

## 5. Core Architectural Highlights

### Strict Passenger Count Formula
$$\text{Current Onboard} = \sum \text{Valid Boarding Events} - \sum \text{Valid Exit Events}$$
- Ticket bookings **never** increment the onboard count until verified upon actual boarding.
- Conductor scanning or issuing cash tickets immediately records a unique `BoardingEvent`.
- Passenger disembarkation records an `ExitEvent`, preventing negative counts or seat overflows.

### Stale Telemetry Protection
- Vehicle telemetry older than 300 seconds (5 minutes) is flagged with `isStale: true`.
- The Android app and Web admin explicitly warn users rather than misleading passengers with outdated coordinates.

### Conductor Offline Operation
- When network drops during transit, cash ticket sales and boarding/exit records are buffered locally in SQLite via Room.
- Deduplication keys (`clientEventId`) ensure idempotent bulk synchronization when network connectivity restores.

### External Services Configuration
- **Google Maps**: Provide `MAPS_API_KEY` in `android-app/local.properties` and `VITE_GOOGLE_MAPS_API_KEY` in `web-admin/.env`.
- **Payment Sandbox**: Sandbox mode is enabled by default (`PAYMENT_SANDBOX=true`). To use live Razorpay, configure `RAZORPAY_KEY_ID` and `RAZORPAY_KEY_SECRET`.
