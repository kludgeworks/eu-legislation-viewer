# Progress Bar Feature (WIP)

## Status: On Hold

The Mordant CLI progress bar feature has been prepared but not yet integrated. The code is ready for future implementation.

## What's Ready

- **Mordant dependency** added to `pom.xml` (v2.7.2)
- **MordantProgressService.kt** - Service for rendering CLI progress bars, colored text, and job summaries
- **MORDANT_LOGGING.md** - Documentation on logger conflict resolution

## Why On Hold

The Mordant API has changed in version 2.7.2, and the stashed code needs to be updated to match the current API:
- `TextColors` import paths may have changed
- Some method signatures need verification
- Integration with `JobLauncherEntrypoint` needs to be designed

## Next Steps (Future Session)

1. **Update Mordant imports** to match v2.7.2 API (check official docs)
2. **Create Spring Batch listeners** to call Mordant methods:
   - `JobExecutionListener` → `printJobHeader()`, `printJobSummary()`
   - `StepExecutionListener` → `printStepHeader()`, `printStepSummary()`
   - `ChunkListener` → `updateChunkProgress()`
   - `SkipListener` → `printError()`
3. **Configure logback** to suppress console logging (let Mordant own the console)
4. **Test with real job execution** to verify progress rendering
5. **Add feature flag** to enable/disable via `application.yaml`

## Design Considerations

### Logging Conflict
- **Problem**: Both loggers and Mordant write to console → visual corruption
- **Solution**: Redirect all logs to file (`logs/formex-ingester.log`), let Mordant own console
- **Already implemented**: `logback-spring.xml` configured for file-only logging

### Integration Points
- Listeners should be optional (`@ConditionalOnProperty`)
- Progress service should handle thread-safety (already uses `ConcurrentHashMap`)
- Terminal output should degrade gracefully on non-TTY environments

## Files Location

- **Source code**: `docs/progress-bar/MordantProgressService.kt`
- **Documentation**: `docs/progress-bar/MORDANT_LOGGING.md`
- **Dependency**: Already in `pom.xml`

## Benefits When Implemented

✅ Real-time progress visualization for long-running jobs
✅ Colored output for better readability
✅ Professional CLI experience
✅ Separate logs from UI rendering
✅ Feature-flagged (can be disabled)

## References

- [Mordant GitHub](https://github.com/ajalt/mordant)
- [Mordant Documentation](https://ajalt.github.io/mordant/)
- [Spring Batch Listeners](https://docs.spring.io/spring-batch/docs/current/reference/html/step.html#listeners)
