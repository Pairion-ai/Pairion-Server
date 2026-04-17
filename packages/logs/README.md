# @pairion/logs

Centralized log sink. Receives forwarded logs from Clients and Nodes and writes them to the Server's pino output.

## Public Surface

- `processLogForward(batch)` — Processes a batch of forwarded log entries
- `LogForwardBatch`, `ForwardedLogEntry`, `LogLevel` — Types matching the AsyncAPI `LogForwardPayload`

## Status

M0: Writes forwarded logs to pino. SQLite-backed indexed log store comes at a later milestone.
