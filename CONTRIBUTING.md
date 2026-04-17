# Contributing to Pairion Server

Contributions are welcome. This project uses an AI-first development workflow powered by Claude Code.

## Development Workflow

1. **Every change starts with a Claude Code prompt.** Prompts are `.md` file artifacts that specify goals and constraints, never code.
2. **Every prompt reads the source-of-truth files** (`openapi.yaml`, `asyncapi.yaml`, `Architecture.md`, `Pairion-Charter.md`) before producing code.
3. **Every prompt ends with:** *Compile, Run, Test, Commit, Push to Github.*

## Commit Conventions

This project uses [Conventional Commits](https://www.conventionalcommits.org/):

```
feat(skills): add aws-bill skill
fix(agent): prevent tool-call retry storm
docs(memory): clarify partitioning invariant
```

## Quality Requirements

- **100% test coverage** on every package's public surface (enforced by CI)
- **TSDoc** on every class and public function
- **No `console.log`** in `packages/*/src/` (ESLint enforced)
- **No vendor SDK imports** outside `@pairion/adapters` (ESLint enforced)
- **Centralized structured logging** via pino

## Pull Requests

- Every PR links the Claude Code prompt that generated the change
- CI must be green before merge
- PRs that ship code must also ship tests and documentation

## Code of Conduct

See [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).
