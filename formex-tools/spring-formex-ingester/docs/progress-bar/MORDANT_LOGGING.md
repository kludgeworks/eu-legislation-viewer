# Mordant CLI Progress - Logger Conflict Resolution

## Problem Identified

When both **Mordant** (CLI progress rendering) and **application loggers** write to console simultaneously, you get:

- Mixed output on the same terminal
- Progress bars interrupted by log messages
- Visual corruption and loss of readable UI
- Race conditions between Mordant terminal updates and logger writes

## Solution Implemented

### 1. **Dedicated Log File** (`logback-spring.xml`)
All application logs are now redirected to `./logs/formex-ingester.log` with:
- **File-based logging only** (no console output by default)
- **Rolling file policy**: 10MB max per file, keeps 10 recent files, 1GB total cap
- **Timestamped log files**: `formex-ingester-yyyy-MM-dd.i.log`
- **Structured formatting**: Timestamps, thread names, log levels, logger names

### 2. **Logging Configuration** (`logback-spring.xml`)

```xml
<!-- Root logger - writes to FILE ONLY (Mordant handles console) -->
<root level="INFO">
    <appender-ref ref="FILE"/>
    <!-- Uncomment to debug (not recommended with Mordant) -->
    <!-- <appender-ref ref="CONSOLE"/> -->
</root>
```

### 3. **Logger Levels**
- `eu.legislation.formex.ingester`: **DEBUG** (application code)
- `org.springframework.batch`: **INFO** (batch framework)
- `org.springframework`: **WARN** (Spring framework noise reduction)

### 4. **Profile-Specific Logging**
- **Development** (`!prod`): More verbose output for debugging
- **Production** (`prod`): Minimal logging to reduce noise

## How It Works

```
┌─────────────────────────────────────────────────────────┐
│             Spring Batch Application                     │
└─────────────────────────────────────────────────────────┘
                         │
         ┌───────────────┴───────────────┐
         │                               │
    ┌────▼─────┐              ┌─────────▼────┐
    │ Loggers  │              │  Mordant     │
    │ (SLF4J)  │              │ Progress     │
    └────┬─────┘              │ Service      │
         │                    └─────────┬────┘
         │                             │
    ┌────▼──────────────┐     ┌────────▼──────────┐
    │ Logback           │     │ Terminal Output   │
    │ (via logback-     │     │ (Direct TTY       │
    │  spring.xml)      │     │  Control)         │
    └────┬──────────────┘     └────────┬──────────┘
         │                             │
    ┌────▼──────────────┐     ┌────────▼──────────┐
    │ File:             │     │ Console:          │
    │ ./logs/           │     │ Colored progress  │
    │ formex-ingester   │     │ bars, tables, etc │
    │ .log              │     │                   │
    └───────────────────┘     └───────────────────┘
```

## Benefits

✅ **Clean CLI Output**: Only Mordant renders to console
✅ **Full Logging**: All logs safely written to rolling files
✅ **No Conflicts**: No race conditions or visual corruption
✅ **Production Ready**: Minimal console noise with verbose file logging
✅ **Debuggable**: Can enable console logging by uncommenting one line
✅ **Persistent Records**: Searchable logs in `./logs/` directory

## Development Usage

### View Live Progress (Console Only)
```bash
./mvnw spring-boot:run
# Outputs only: Mordant progress bars, colored tables
# Logs go to: ./logs/formex-ingester.log
```

### View Live Logs While Running
```bash
tail -f ./logs/formex-ingester.log
# In another terminal, run the application
```

### Enable Console Logging (For Debugging)
Edit `src/main/resources/logback-spring.xml` and uncomment:
```xml
<!-- Uncomment to also log to console (not recommended with Mordant) -->
<appender-ref ref="CONSOLE"/>
```

⚠️ **Note**: This will interfere with Mordant's progress rendering.

## Log File Location & Rotation

**Default Location**: `./logs/formex-ingester.log`

**Rotation Policy**:
- Max file size: 10MB
- Max files: 10 recent files
- Total cap: 1GB
- Naming: `formex-ingester-2025-11-13.0.log`, `formex-ingester-2025-11-13.1.log`, etc.

**Change Log Location**:
```bash
# Via environment variable
export LOG_PATH=/var/log/formex-ingester/
./mvnw spring-boot:run

# Via JVM property
./mvnw spring-boot:run -Dlogging.file.name=/custom/path/app.log
```

## Logback Features Used

1. **RollingFileAppender**: Automatically rolls files based on size + date
2. **SizeAndTimeBasedRollingPolicy**: Combines date and size-based rotation
3. **Charset UTF-8**: Proper encoding for international characters
4. **Spring Profiles**: Different logging for dev/prod environments
5. **Pattern Encoding**: Structured log format with all relevant context

## What Happens to Logger Calls

All logger calls in the codebase now safely write to file instead of console:

```kotlin
// This no longer clutters the console
logger.info { "Processing item $itemId" }

// Instead, it's written to: ./logs/formex-ingester.log
// With format: 2025-11-13 23:45:12.123 [pool-1-thread-5] INFO  
//              eu.legislation.formex.ingester.SomeClass - Processing item 12345
```

## Summary

**Problem**: Loggers and Mordant compete for console output → visual corruption
**Solution**: Redirect logs to file (`./logs/`) while Mordant owns the console
**Result**: Clean, professional CLI with full logging history in files
