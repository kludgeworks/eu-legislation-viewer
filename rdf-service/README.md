# RDF Service Module

This module provides access to EU legal documents via the EU Publications SPARQL endpoint (Cellar).

## Architecture

The module follows clean architecture principles with clear separation of concerns:

```
rdf-service/
├── api/              # Public API (WorkService, model classes)
├── spi/              # Service Provider Interface (for extensibility)
├── internal/         # Internal implementation
│   ├── config/       # Spring configuration
│   ├── mapper/       # Data mappers (SPARQL results → domain objects)
│   └── repository/   # SPARQL query execution
└── resources/
    └── sparql/       # SPARQL query files
```

## Key Components

### Work Model

The simplified `Work` data class represents an EU legal document with:
- Basic metadata (URI, title, dates)
- Legal properties (CELEX number, ELI, in-force status)
- Related entity URIs (expressions, subdivisions, related works)

### WorkService

The main service provides methods to:
- Retrieve a work by CELEX number
- Check if a work exists
- Load related entities (expressions, etc.)

### Error Handling

Uses Arrow's `Either<DomainError, T>` for functional error handling:
- `NotFound` - Document not found
- `ValidationError` - Invalid input
- `QueryFailure` - SPARQL query errors

## Configuration

Configure in `application.yml`:

```yaml
rdf:
  endpoint:
    url: https://publications.europa.eu/webapi/rdf/sparql
  connection:
    timeout: 5000
    socketTimeout: 30000
    pool:
      maxTotal: 50
      maxPerRoute: 20
  query:
    defaultTimeout: 30000
    maxResults: 1000
```

## Usage Example

```kotlin
@Service
class MyService(private val workService: WorkService) {
    
    suspend fun getRegulation(celexNumber: String): Work? {
        return workService.getWorkByCelex(celexNumber)
            .fold(
                ifLeft = { error -> 
                    logger.error("Failed to retrieve work: $error")
                    null
                },
                ifRight = { work -> work }
            )
    }
}
```

## SPARQL Queries

The module uses parameterized SPARQL queries stored in `/resources/sparql/work/`:
- `find-work-by-celex.sparql` - Retrieve work by CELEX number
- `find-work-expressions.sparql` - Get expressions for a work
- Additional queries for subdivisions, amendments, etc.

## Dependencies

- Spring Boot Starter Web
- Arrow Core (functional programming)
- RDF4J (SPARQL client)
- Kotlinx DateTime
- Spring Modulith

## Testing

Run integration tests to validate SPARQL queries:

```bash
./gradlew :rdf-service:test
```

## Future Enhancements

1. **Caching** - Add caching for frequently accessed works
2. **Batch Operations** - Retrieve multiple works in one query
3. **Full-Text Search** - Integrate with EU Publications search
4. **Consolidations** - Query consolidated versions
5. **Change Tracking** - Monitor amendments and corrections