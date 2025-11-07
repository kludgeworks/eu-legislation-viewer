# Agent Guidelines for formex-ingester

## Build & Test Commands
- **Build**: `./mvnw clean package` or `./mvnw spring-boot:run`
- **Test all**: `./mvnw test`
- **Test single class**: `./mvnw test -Dtest=FormexIngesterApplicationTests`
- **Test single method**: `./mvnw test -Dtest=FormexIngesterApplicationTests#contextLoads`

## Code Style
- **Java version**: Java 21
- **Framework**: Spring Boot 3.5.7 with Spring Batch
- **Package structure**: `eu.legislation.formex.ingester.{config,listener,model,processor,reader,tasklet,writer}`
- **Imports**: Group by Java standard library, then third-party, then Spring framework, blank line between groups
- **Models**: Prefer Java records for immutable DTOs (see `XmlMetadata`, `SparqlUri`)
- **Components**: Use `@Component`, `@Configuration` annotations; inject dependencies via constructor
- **Documentation**: Include Javadoc for all public classes/methods, especially explaining batch job flow
- **Naming**: camelCase for variables/methods, PascalCase for classes, UPPER_SNAKE_CASE for constants
- **Error handling**: Use unchecked exceptions (e.g., `UncheckedIOException`) for I/O errors in batch processors
- **SQL**: Use text blocks (`"""..."""`) for multi-line SQL/SPARQL queries
- **Configuration**: Store settings in `application.yaml`, inject via `@Value` annotations

## Project Specifics
- **Database**: DuckDB with single connection pool (`maximum-pool-size: 1`)
- **Batch processing**: Chunk size 10, parallel execution with ThreadPoolTaskExecutor (10-20 threads)
- **File handling**: Stream large XML files, never load fully into memory, use 64KB buffers
- **Testing**: Use `@SpringBootTest` for integration tests, Spring Batch Test for job testing
