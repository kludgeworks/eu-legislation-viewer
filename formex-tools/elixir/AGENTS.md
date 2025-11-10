# Agent Guidelines for FormexV4

## Commands
- **Test all**: `mix test`
- **Test single**: `mix test test/path/to/file_test.exs` or `mix test test/path/to/file_test.exs:42` (specific line)
- **Lint**: `mix credo`
- **Format**: `mix format`
- **Run sync**: `mix formex.sync`
- **Install deps**: `mix deps.get`

## Code Style
- **Imports**: Use `alias` for module references (e.g., `alias FormexV4.Cache`), `require Logger` for Logger
- **Documentation**: Add `@moduledoc` for modules, `@doc` for public functions with examples in doctest format
- **Types**: Use `@behaviour` for behaviors, guard clauses `when is_binary(uri)` for type checking
- **Naming**: snake_case for functions/variables, PascalCase for modules, prefix private functions with `defp`
- **Error handling**: Return `{:ok, result}` or `{:error, reason}` tuples, use pattern matching in case statements
- **Logging**: Use `Logger.debug/info/warning/error` with descriptive messages, include context (URIs, counts, etc.)
- **Constants**: Module attributes for defaults (e.g., `@default_timeout 120_000`)
- **Testing**: Use `ExUnit.Case`, `async: false` for tests with side effects, setup blocks for test initialization
- **Telemetry**: Emit telemetry events for important operations (`:telemetry.execute/3`)
