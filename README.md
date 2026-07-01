# Ay Khedma Backend

Ay Khedma is a comprehensive service platform that bridges the gap between consumers and service providers. This Spring Boot backend serves as the robust foundation for the entire ecosystem, handling everything from user management and booking workflows to real-time communication and AI-assisted features.

## Purpose

This project provides the server-side foundation for the Ay Khedma application. It is designed to:
- Keep the system's core business data consistent (users, providers, service catalog, schedules, bookings, ratings, and locations)
- Support a real-time user experience through WebSockets and push notifications, so important events reach users immediately
- Orchestrate time-sensitive and reliability-focused workflows using scheduling (e.g., reminders, expiration/cleanup jobs)
- Provide communication channels between parties (chat sessions and notification delivery)
- Integrate with external services to extend platform capabilities (verification service, Firebase, Cloudinary, Google Maps)
- Enable AI-assisted features and tool integration (Gemini + MCP hooks)

In short, this backend powers consumer–provider interaction, keeps bookings and services accurate over time, and delivers notifications and assistance when it matters.

## This backend is responsible for:

- Exposing the REST API and WebSocket endpoints used by the mobile/web clients
- Managing core domain data (users, providers, bookings, schedules, ratings, locations)
- Handling authentication primitives (JWT/OAuth components) and OTP flows
- Delivering real-time updates and push notifications (WebSocket + Firebase)
- Automating time-based workflows (Quartz schedulers)
- Supporting AI-assisted features (Gemini integration, MCP hooks)
- Integrating with external systems (verification service, Cloudinary for media, Google Maps)

## Key Features

### Core Domain Management
- User Management: Registration, profiles, preferences, OTP verification
- Provider Management: Service provider onboarding, verification, portfolio
- Service Catalog: Dynamic service listing, categorization, pricing
- Booking System: End-to-end booking lifecycle (create, update, cancel, complete)
- Schedule Management: Provider availability, time slots, calendar integration
- Ratings & Reviews: Post-service feedback, provider ratings
- Location Services: Geo-spatial queries, service area management

### Communication & Notifications
- WebSocket Server: Real-time bidirectional communication
- Push Notifications: Firebase Cloud Messaging integration
- Email Service: Transactional emails with template support
- Chat System: Consumer-provider messaging with history

### Automation & Scheduling
- Quartz Jobs: Background processing, reminders, data cleanup
- Booking Reminders: Automated notifications before service time
- Expiration Jobs: Cleanup of expired bookings, tokens, sessions
- Report Generation: Scheduled analytics and reporting

### AI & Intelligence
- Gemini Integration: AI-powered assistance and content generation
- MCP Implementation: Model Context Protocol for tool-enabled AI
- Smart Features: Intelligent suggestions, automated responses

### Security & Authentication
- JWT-based Authentication: Token management and validation
- OTP Flow: email verification
- Role-based Access: Different permissions for consumers, providers, admins

## Tech Stack

### Core Framework
- Java 21 (LTS)
- Spring Boot 3.2.4
- Maven (build & dependency management)

### Data Layer
- PostgreSQL (with PostGIS extension for spatial data)
- Spring Data JPA (Hibernate ORM)
- Flyway (database migrations)
- Hibernate Spatial + JTS (geometry/spatial support)
- Redis (used for session/performance support)

### Real-Time & Messaging
- WebSocket (real-time bidirectional communication)
- Push notifications via Firebase Admin SDK
- STOMP over WebSocket is used for real-time messaging

### Email & Notifications
- Spring Mail with Thymeleaf templates (transactional email notifications)

### Integration & External Services
- Cloudinary (media file management)
- Google Maps API (location services)
- Google Gemini (AI assistance)
- MCP (Model Context Protocol) integration for tool-enabled AI workflows

## Prerequisites

- Java 21
- Maven
- PostgreSQL (local or remote)
- Redis (optional depending on your configuration/needs)
- Environment variables / `app.env` file (required for external services)

## Configuration

The application loads environment variables from:
- `app.env` (configured via `@PropertySource("file:app.env")`)

Important properties are defined in `src/main/resources/application.properties`, for example:
- Server:
  - `server.port` (defaults to **8081**)
- Database:
  - `DB_URL`, `DB_USER`, `DB_PASS`
- WebSocket:
  - `websocket.endpoint=/ws-notifications`
- Email:
  - `EMAIL_USERNAME`, `EMAIL_PASSWORD`
- Firebase:
  - `FIREBASE_PROJECT_ID`, `FIREBASE_DATABASE_URL`, `firebase.config.file`
- Cloudinary:
  - `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`
- Google Maps:
  - `GOOGLE_MAPS_API_KEY`
- Gemini:
  - `GEMINI_API_KEYS`, `GEMINI_MODEL`
  - `ai.gemini.use-spring-ai` (default is `false` in the properties)
- Verification service:
  - `VERIFICATION_SERVICE_URL` (default `http://127.0.0.1:8000`)
- MCP:
  - `mcp.enabled=true`
  - `mcp.endpoint=/mcp`
  - `mcp.server.url=http://localhost:8081/mcp`

### `app.env` example

An `app.env` file is already present in the repository root. Update it to match your own credentials.

## Run the application (Local)

### 1) Start required services

If you use Docker Compose:
```bash
docker compose -f compose.yaml up -d
```
This will start:
- PostgreSQL
- Redis

### 2) Start Spring Boot

```bash
./mvnw spring-boot:run
```
Or with Maven directly:
```bash
mvn spring-boot:run
```

### 3) Access

- API server: `http://localhost:{SERVER_PORT:-8081}`
- WebSocket endpoint: `ws://localhost:{port}/ws-notifications`

## Docker Compose (Optional)

`compose.yaml` includes:
- `postgres:latest`
- `redis:latest`

If you deploy to production, pin image versions suitable for your environment.

## Observability & Debugging

- Actuator is included (`spring-boot-starter-actuator`).
- WebSocket debugging logging is enabled for local troubleshooting.

## Testing

The project includes test infrastructure (JUnit + Spring Boot Test + Testcontainers).
A typical command:
```bash
mvn clean test
```

## Project Structure (package overview)

- `com.aykhedma.controller` – REST controllers and WebSocket controllers
- `com.aykhedma.service` – business logic
- `com.aykhedma.repository` – data access (Spring Data JPA)
- `com.aykhedma.model` – entities/domain models
- `com.aykhedma.dto` – request/response DTOs
- `com.aykhedma.mapper` – MapStruct mappers
- `com.aykhedma.scheduler` – scheduled background jobs
- `com.aykhedma.auth` / `com.aykhedma.security` – authentication and security components
- `com.aykhedma.mcp` – MCP client/server integration code

## Build

Create an executable jar:
```bash
./mvnw clean package
```

## Notes

- In `application.properties`, security is currently disabled:
  - `spring.security.enabled=false`
  - `management.security.enabled=false`