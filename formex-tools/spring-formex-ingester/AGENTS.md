# Spring Formex Ingester - Agent Guidelines

## Build & Test Commands
- **Build**: `./mvnw clean compile` or `./mvnw clean package`
- **Run all tests**: `./mvnw test`
- **Run single test**: `./mvnw test -Dtest=ClassName` or `./mvnw test -Dtest=ClassName#methodName`
- **Run application**: `./mvnw spring-boot:run`

## Language & Framework
- **Kotlin 2.2.21** with Java 21, Spring Boot 4.0.0-RC2, Spring Batch 6.0
- **ORM**: JetBrains Exposed (not JPA) with SQLite database
- **Testing**: JUnit 5, Mockito, Spring Batch Test

## Code Style

### Imports & Organization
- Group imports: standard library, Spring, third-party, project
- Use explicit imports, avoid wildcards except for Exposed DSL (e.g., `import org.jetbrains.exposed.sql.*`)

### Naming & Types
- Use `camelCase` for variables/functions, `PascalCase` for classes
- Prefer `data class` for DTOs and models
- Use explicit types for public APIs, inference for private/local variables
- Nullable types: use `?` suffix and safe calls (`?.`, `?:`)

### Logging & Error Handling
- Use `KotlinLogging.logger {}` from `io.github.oshai.kotlinlogging`
- Log format: `logger.warn(throwable) { "Message with context" }`
- Persist errors to database in separate transactions using `runBlocking(Dispatchers.IO)`
- Let Spring Batch handle retries/skips; configure via step builders

### Spring Batch Patterns
- Use `@Component` for processors, readers, writers, listeners
- Transaction management: use `SpringTransactionManager` for Exposed, `DataSourceTransactionManager` for Batch metadata
- Use `transaction(database) { }` blocks for Exposed SQL operations
- Prefer `batchInsert` for bulk operations

### Testing
- Unit tests: mock external dependencies with Mockito
- E2E tests: use `@SpringBootTest`, `@SpringBatchTest`, `@ActiveProfiles` with real or in-memory database
- Disable network-dependent tests with `@Disabled` annotation
- Use `JobOperatorTestUtils` (Spring Batch 6.0+) instead of deprecated `JobLauncherTestUtils`
