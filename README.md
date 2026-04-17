# Pairion Server

> The brain of the Pairion ambient AI household presence.

**[Demo video placeholder]** — Coming at M2.

Pairion Server is the central process in a Pairion household. It hosts the gateway (REST + WebSocket), the agent orchestrator, skill registry, memory subsystem, speaker-ID subsystem, household policy engine, adapter layer, Node arbitration, proactive rules engine, computer-use action queue, and skill authoring flow.

## Quick Start (Development)

```bash
# Prerequisites: Node 24+, pnpm 10+
pnpm install
pnpm build
pnpm dev
```

The Server starts on port **18789**. A dev bearer token is auto-generated at `~/.pairion/device.token` on first run.

```bash
# Health check
curl http://localhost:18789/health

# System status (requires auth)
TOKEN=$(cat ~/.pairion/device.token)
curl -H "Authorization: Bearer $TOKEN" http://localhost:18789/v1/status
```

## Architecture

See [Pairion Charter](Pairion-Charter.md) for the full vision and [Architecture](Architecture.md) for Server internals.

The Server is a pnpm monorepo with 13 packages under `packages/`:

| Package | Purpose |
|---------|---------|
| `@pairion/core` | Shared types, event bus, logger, errors |
| `@pairion/gateway` | REST + WebSocket edge |
| `@pairion/adapters` | Pluggable backend interfaces |
| `@pairion/logs` | Centralized log sink |
| `@pairion/agent` | Session orchestration |
| `@pairion/household` | Users, roles, policy |
| `@pairion/speaker-id` | Voice identification |
| `@pairion/memory` | Per-user memory stores |
| `@pairion/skills` | MCP skill registry |
| `@pairion/node-mgmt` | Pi Node management |
| `@pairion/proactive` | Proactive behaviors |
| `@pairion/actions` | Computer-use approval |
| `@pairion/authoring` | Skill authoring flow |

## License

Source-available, non-commercial. See [LICENSE](LICENSE).
