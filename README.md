# Spring Boot REST API Template

A minimal Spring Boot REST API template with JWT authentication, clean architecture, and comprehensive testing setup. Perfect for quickly bootstrapping new backend projects.

## 🚀 What's Included

This template provides a foundation for building modern REST APIs:

- **JWT Authentication** - Complete user registration/login flow with secure password hashing
- **Clean Architecture** - 3-layer service architecture with clear separation of concerns
- **Database Ready** - PostgreSQL with JPA/Hibernate, soft deletes, and auditing
- **Testing Setup** - Integration tests with Testcontainers
- **API Documentation** - Auto-generated with OpenAPI/Swagger UI
- **Validation** - Bean Validation with global exception handling
- **Security** - Spring Security, BCrypt password encoding, CORS configuration

## 📋 Prerequisites

- **Java 17+**
- **Docker** (for running PostgreSQL database)
- **Maven** (included via Maven Wrapper)

## 🎯 Quick Start

### 1. Clone and Customize

```bash
# Clone this template
git clone <your-repo-url>
cd spring-template-api

# Update project metadata in pom.xml:
# - groupId
# - artifactId
# - name
# - description

# Update package names from org.example.axelnyman.main to your own
```

### 2. Start the Database

```bash
# Start PostgreSQL with Docker Compose
docker-compose -f docker-compose.dev.yml up -d
```

### 3. Run the Application

```bash
# Run with local profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

The API will be available at `http://localhost:8080/api`

### 4. Explore the API

Open Swagger UI at `http://localhost:8080/swagger-ui.html` to:
- View all available endpoints
- Test authentication flow
- Explore example endpoints

## 📚 Architecture Overview

### 3-Layer Service Architecture

```
┌─────────────────────────────────────┐
│         API Layer                    │
│   (Controllers - HTTP only)          │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│         Domain Layer                 │
│   ┌────────────────────────────┐    │
│   │  AuthService               │    │
│   │  (Authentication logic)    │    │
│   └────────────┬───────────────┘    │
│                │                     │
│   ┌────────────▼───────────────┐    │
│   │  DomainService             │    │
│   │  (Business logic + DTOs)   │    │
│   └────────────┬───────────────┘    │
└────────────────┼───────────────────┘
                 │
┌────────────────▼───────────────────┐
│    Infrastructure Layer             │
│   ┌────────────────────────────┐   │
│   │  DataService               │   │
│   │  (Data access + entities)  │   │
│   └────────────┬───────────────┘   │
│                │                    │
│   ┌────────────▼───────────────┐   │
│   │  Repositories (JPA)        │   │
│   └────────────────────────────┘   │
└────────────────────────────────────┘
```

**Key Principles:**
- **DataService**: Pure data access, returns entities
- **DomainService**: Business logic, returns DTOs
- **AuthService**: Authentication-specific logic
- **Controllers**: HTTP concerns only, delegate to services

### Project Structure

```
src/main/java/
├── domain/
│   ├── model/           # JPA entities
│   ├── dtos/            # Request/Response DTOs
│   ├── services/        # Service implementations
│   ├── abstracts/       # Service interfaces
│   └── extensions/      # Entity ↔ DTO mappers
├── api/
│   └── endpoints/       # REST controllers
├── infrastructure/
│   ├── data/
│   │   ├── context/     # JPA repositories
│   │   └── services/    # Data service impl
│   ├── security/        # JWT, Spring Security config
│   └── config/          # Application configuration
└── shared/
    └── exceptions/      # Global exception handling
```

## 🧪 Testing

### Run All Tests

```bash
./mvnw test
```

### Test Categories

- **AuthIntegrationTest** - Registration, login, JWT validation
- **UserIntegrationTest** - User profile management
- **JwtAuthenticationFilterIntegrationTest** - Security filter behavior
- **JwtTokenProviderTest** - JWT token generation and validation

### Writing New Tests

All integration tests use:
- **Testcontainers** for PostgreSQL (automatic cleanup)
- **MockMvc** for HTTP testing
- **@BeforeEach** for database cleanup

## 📖 API Documentation

### Swagger UI

Available at `http://localhost:8080/swagger-ui.html` when running locally.

**Test the API:**
1. Register a new user at `/api/auth/register`
2. Copy the JWT token from the response
3. Click "Authorize" button (🔒 icon)
4. Enter: `Bearer <your-token>`
5. Test protected endpoints

### Available Endpoints

#### Authentication
- `POST /api/auth/register` - Register new user
- `POST /api/auth/login` - Login and get JWT token

#### Users (Protected)
- `GET /api/users/me` - Get current user profile

## 🔐 Security

### Password Security
- Passwords hashed with BCrypt
- Minimum 8 characters required (configurable in DTOs)

### JWT Tokens
- 24-hour expiration (configurable in `application.properties`)
- Include in header: `Authorization: Bearer <token>`

### Environment Variables

Create `.env` file for local development:

```bash
# Database
POSTGRES_USER=user
POSTGRES_PASSWORD=password
POSTGRES_DB=mydatabase

# JWT Secret (change in production!)
JWT_SECRET=your-secret-key-here
```

## 🎨 Customization Guide

### 1. Rename Package

Find and replace `org.example.axelnyman.main` with your package name across all files.

### 2. Update pom.xml

```xml
<groupId>com.yourcompany</groupId>
<artifactId>your-project-name</artifactId>
<version>0.0.1-SNAPSHOT</version>
<name>Your Project Name</name>
<description>Your project description</description>
```

### 3. Add Your First Entity

The User entity is included as an example. To add your own entities, follow this pattern:

1. Create entity in `domain/model/` with JPA annotations
2. Create repository in `infrastructure/data/context/`
3. Create DTOs in `domain/dtos/`
4. Create extensions in `domain/extensions/` for mapping
5. Add methods to service interfaces (`IDataService`, `IDomainService`)
6. Implement methods in service classes
7. Create controller in `api/endpoints/`
8. Write integration tests

See the User implementation for a complete example of this pattern.

## 📝 Development Notes

### Key Design Decisions

1. **No validation in services** - All validation done via Bean Validation in DTOs
2. **Specific exceptions** - Use domain exceptions (`UserNotFoundException`) not generic `RuntimeException`
3. **Extension classes** - All entity ↔ DTO mapping centralized in extension classes
4. **Soft deletes** - Users have `deletedAt` field instead of hard deletes
5. **JPA auditing** - Automatic `createdAt`/`updatedAt` timestamps

### Common Patterns

**Fetching with error handling:**
```java
User user = dataService.getUserById(id)
    .orElseThrow(() -> new UserNotFoundException("User not found"));
```

**Mapping collections:**
```java
return dataService.getAllUsers()
    .stream()
    .map(UserExtensions::toResponse)
    .toList();
```

**Controller responses:**
```java
return domainService.getUser(id)
    .map(user -> ResponseEntity.ok(user))
    .orElse(ResponseEntity.notFound().build());
```

## 🤝 Contributing

This is a template project. Fork it and customize for your needs!

## 📄 License

MIT License - feel free to use for any purpose.

---

**Happy Building! 🚀**

For questions or issues, check the integration tests for working examples of all features.
