# Vaadin Users

A full-stack user management application built with Kotlin, Vaadin, and Spring Boot.

## Tech Stack

- **Language:** Kotlin
- **UI:** Vaadin 25 + KaribuDSL
- **Backend:** Spring Boot 4
- **Database:** PostgreSQL
- **Migrations:** Flyway
- **Build:** Gradle (Kotlin DSL)
- **Containerization:** Docker & Docker Compose

## Running the Application

### Prerequisites

- Docker & Docker Compose installed

### Start

```bash
docker-compose up
```

That's it. The application will be available at [http://localhost:8080](http://localhost:8080).

On first startup the application will:
1. Start PostgreSQL
2. Apply database schema via Flyway
3. Seed 500 test users automatically

## Default Credentials

| Role  | Username | Password  |
|-------|----------|-----------|
| Admin | `admin`  | `admin123` |
| User  | `user`   | `user123`  |

## Features

### All users
- View paginated list of users (username, email, role, created/updated date)
- Search by username or email
- Sort by username, email, creation date, last update date (ASC/DESC)
- Adjustable page size (10 / 25 / 50 / 100)

### Admin only
- Create new users
- Edit existing users
- Delete users (cannot delete own account)

## Project Structure

```
src/main/kotlin/me/andrew/vaadin_users/
├── config/
│   └── SecurityConfig.kt         # Spring Security + BCrypt
├── domain/
│   ├── AppUser.kt                # JPA entity
│   └── Role.kt                   # USER / ADMIN enum
├── repos/
│   └── AppUserRepository.kt      # JPA repository with search queries
├── service/
│   ├── UserService.kt            # Business logic + @PreAuthorize
│   ├── DatabaseUserDetailsService.kt  # Spring Security integration
│   └── IniSeed.kt                # Startup data seeding
└── ui/
    ├── LoginView.kt              # Login page
    └── DashboardView.kt          # Main dashboard
```

## Assumptions & Trade-offs

- **Authentication** is DB-backed (not in-memory) to support the full user list
- **Seeding** runs only once — if the database already has users, seeding is skipped
- **Search** is case-insensitive and searches either username or email (not both simultaneously)
- **Email validation** uses a regex pattern on both service and UI level
- **Password** is required on create, optional on edit (leave blank to keep existing)
