# Agent Guidelines for formex-ingester

## Build & Test Commands
**IMPORTANT**: This project requires Java 21. If your default Java version is not 21, use:
```bash
JAVA_HOME=/path/to/java21 PATH=/path/to/java21/bin:$PATH ./mvnw <goal>
```

- **Build**: `./mvnw clean package` or `./mvnw spring-boot:run`
- **Test all**: `./mvnw test`
- **Test single class**: `./mvnw test -Dtest=FormexIngesterApplicationTests`
- **Test single method**: `./mvnw test -Dtest=FormexIngesterApplicationTests#contextLoads`

## Code Style
- **Language**: Kotlin 2.1.0 (migrated from Java)
- **Java version**: Java 21 (required for Kotlin 2.1.0 compatibility)
- **Framework**: Spring Boot 3.5.7 with Spring Batch
- **Package structure**: `eu.legislation.formex.ingester.{config,listener,model,processor,reader,tasklet,writer}`
- **Source directory**: `src/main/kotlin/` (Kotlin files only)
- **Imports**: Group by Java/Kotlin standard library, then third-party, then Spring framework, blank line between groups
- **Models**: Use Kotlin data classes for immutable DTOs (see `XmlMetadata`, `SparqlUri`)
- **Components**: Use `@Component`, `@Configuration` annotations; inject dependencies via constructor
- **Documentation**: Include KDoc for all public classes/methods, especially explaining batch job flow
- **Naming**: camelCase for variables/methods/functions, PascalCase for classes, UPPER_SNAKE_CASE for constants
- **Error handling**: Use unchecked exceptions (e.g., `UncheckedIOException`) for I/O errors in batch processors
- **SQL/SPARQL**: Use Kotlin triple-quoted strings (`"""..."""`) for multi-line queries
- **Configuration**: Store settings in `application.yaml`, inject via `@Value` annotations
- **Kotlin idioms**:
  - Use `data class` for DTOs instead of Java records
  - Prefer expression-bodied functions for simple methods: `fun foo() = bar`
  - Use scope functions: `apply`, `let`, `use`, `also`, `run` for concise initialization
  - Use nullable types (`T?`) instead of Optional
  - Use `lateinit var` for Spring-injected properties that can't be initialized in constructor
  - Prefer immutability: `val` over `var`

## Project Specifics
- **Database**: DuckDB with single connection pool (`maximum-pool-size: 1`)
- **Batch processing**: Chunk size 10, parallel execution with ThreadPoolTaskExecutor (10-20 threads)
- **File handling**: Stream large XML files, never load fully into memory, use 64KB buffers
- **Testing**: Use `@SpringBootTest` for integration tests, Spring Batch Test for job testing
