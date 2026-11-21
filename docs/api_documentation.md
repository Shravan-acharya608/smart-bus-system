# API Documentation: Smart Public Bus Management System

Base URL: `http://localhost:3000/api/v1`

All responses follow the standard envelope format:
```json
{
  "success": true,
  "data": { ... },
  "message": "Optional message"
}
```
Error responses:
```json
{
  "success": false,
  "error": {
    "code": "ERROR_CODE",
    "message": "Human-readable description",
    "details": null
  }
}
```

---

## 1. Authentication Endpoints

### `POST /auth/register`
Creates a new Passenger account.
- **Access:** Public
- **Request Body:**
  ```json
  {
    "email": "user@example.com",
    "password": "Password@123",
    "firstName": "John",
    "lastName": "Doe",
    "phone": "+919876543210"
  }
  ```
- **Response (201 Created):**
  ```json
  {
    "success": true,
    "data": {
      "user": { "id": "uuid", "email": "user@example.com", "role": "PASSENGER" },
      "accessToken": "jwt_token...",
      "refreshToken": "uuid..."
    }
  }
  ```

### `POST /auth/login`
Authenticates any role.
- **Access:** Public
- **Request Body:**
  ```json
  {
    "email": "admin@smartbus.local",
    "password": "Admin@123456",
    "role": "ADMIN"
  }
  ```
- **Response (200 OK):**
  ```json
  {
    "success": true,
    "data": {
      "user": { "id": "uuid", "role": "ADMIN", "firstName": "System" },
      "accessToken": "jwt...",
      "refreshToken": "uuid..."
    }
  }
  ```

---

## 2. Routes & Stops Endpoints

### `GET /routes`
Search transit routes.
- **Query Parameters:** `origin`, `destination`, `search`, `page`, `limit`
- **Response:** List of matching routes with ordered stops and active running buses.

### `POST /routes`
Creates route with ordered stops transactionally.
- **Access:** `ADMIN`, `BUS_OWNER`
- **Request Body:**
  ```json
  {
    "name": "Majestic to Electronic City",
    "code": "356-CW",
    "description": "Via Silk Board",
    "stops": [
      { "name": "Majestic Bus Station", "latitude": 12.9716, "longitude": 77.5946, "stopOrder": 1, "fareFromStart": 0 },
      { "name": "Silk Board", "latitude": 12.9177, "longitude": 77.6238, "stopOrder": 2, "fareFromStart": 20 },
      { "name": "Electronic City Toll", "latitude": 12.8452, "longitude": 77.6602, "stopOrder": 3, "fareFromStart": 35 }
    ]
  }
  ```

---

## 3. Trips & Telemetry Endpoints

### `GET /trips`
Lists scheduled and active trips. Filter by `status=ACTIVE`.

### `POST /trips/:id/start`
Starts a scheduled trip.
- **Access:** Assigned `DRIVER`, `ADMIN`, `BUS_OWNER`
- Sets `status: "ACTIVE"` and actual start time.

### `POST /trips/:id/end`
Completes an active trip and stops telemetry.
- **Access:** Assigned `DRIVER`, `ADMIN`, `BUS_OWNER`

### `POST /trips/:id/location`
Transmits driver GPS coordinates.
- **Access:** Assigned `DRIVER`
- **Request Body:**
  ```json
  {
    "latitude": 12.971598,
    "longitude": 77.594562,
    "speed": 34.5,
    "heading": 182.0,
    "accuracy": 8.0,
    "timestamp": "2026-10-16T12:00:00Z"
  }
  ```
- Broadcasts real-time coordinate update to Socket.IO room `trip:{id}`.

### `GET /trips/:id/location`
Retrieves latest GPS telemetry.
- **Includes Stale Data Check:**
  ```json
  {
    "success": true,
    "data": {
      "latitude": 12.971598,
      "longitude": 77.594562,
      "isStale": false,
      "ageSeconds": 8
    }
  }
  ```

### `GET /trips/:id/passenger-count`
Returns real-time onboard load:
```json
{
  "success": true,
  "data": {
    "currentOnboard": 28,
    "capacity": 45,
    "availableSeats": 17,
    "totalBoardings": 34,
    "totalExits": 6,
    "hasInconsistency": false
  }
}
```

---

## 4. Ticketing & Conductor Endpoints

### `POST /tickets`
Books a digital prepaid ticket.
- **Access:** `PASSENGER`
- Calculates fare between origin and destination stops. Returns `ticketCode` and `qrData`.

### `POST /tickets/verify`
Conductor QR verification.
- **Access:** `CONDUCTOR`, `ADMIN`
- **Request Body:** `{ "ticketCode": "uuid", "tripId": "uuid" }`
- Validates payment status and rejects already-used or mismatched tickets.

### `POST /tickets/:id/board`
Records passenger boarding event.
- Deduplicates via unique database constraint. Broadcasts updated passenger count.

### `POST /tickets/:id/exit`
Records passenger exit event.
- Reduces active onboard count.

### `POST /tickets/onboard`
Issues cash/UPI ticket directly on the bus.
- Creates ticket marked `isOfflineIssued: true`, records payment, and logs boarding event.

---

## 5. Offline Sync Endpoint

### `POST /events/sync`
Bulk synchronization of conductor offline records.
- **Access:** `CONDUCTOR`, `DRIVER`, `ADMIN`
- **Request Body:**
  ```json
  {
    "events": [
      {
        "clientEventId": "board_12345",
        "eventType": "BOARDING",
        "tripId": "uuid",
        "ticketId": "uuid",
        "timestamp": "2026-10-16T11:45:00Z"
      }
    ]
  }
  ```
- **Response:** Detailed summary with `processed`, `skippedDuplicates`, and `errors`.

---

## 6. Reports & Admin Endpoints

### `GET /admin/dashboard`
Aggregated fleet metrics, today's revenue, active trip status, and fleet telemetry.

### `GET /admin/reports`
Financial summaries with date range filtering. Add `?format=csv` to download a CSV file.
