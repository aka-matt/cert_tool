# Architecture Decision Records

This directory contains Architecture Decision Records (ADRs) for Cert Tool. Each ADR records a significant decision: the context, the choice made, and its consequences.

ADRs are append-only. Superseding a decision means writing a new ADR that references the old one and moving the old one to `superseded/`.

| Number | Title | Status |
|--------|-------|--------|
| [0001](./0001-maven-multi-module.md) | Maven multi-module layout | Accepted |
| [0002](./0002-java-17-and-jdk-distribution.md) | Java 17 and JDK distribution | Accepted |
| [0003](./0003-bouncy-castle-provider-strategy.md) | Bouncy Castle provider strategy | Accepted |
| [0004](./0004-javafx-and-atlantafx-versions.md) | JavaFX and AtlantaFX versions | Accepted |
| [0005](./0005-testing-strategy.md) | Testing strategy | Accepted |
| [0006](./0006-quality-gates.md) | Quality gates | Accepted |
| [0007](./0007-logging-strategy.md) | Logging strategy | Accepted |
| [0008](./0008-settings-storage.md) | Settings storage | Accepted |
| [0009](./0009-theme-strategy.md) | Theme strategy | Accepted |
| [0010](./0010-module-boundaries.md) | Module boundaries and dependency direction | Accepted |

**Format** (Michael Nygard style, lightly adapted):

- **Status**: Proposed / Accepted / Superseded
- **Context**: The forces at play
- **Decision**: What we chose
- **Consequences**: Trade-offs, follow-ups, risks