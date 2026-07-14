# Paste Base64 Design

## Goal

Replace the File → Paste Base64 placeholder with an offline loading flow that automatically detects JKS or BCFKS, prompting only when detection is inconclusive.

## Flow

`MainShellController` opens a modal multi-line text dialog. Cancel and blank input do not start a load. On confirmation, the text is decoded only in memory and the resulting bytes are submitted to the existing background executor. The detector safely attempts both supported containers. A unique success loads the existing Inspect workflow; an inconclusive result opens a JKS/BCFKS choice and retries. Invalid Base64 and failed loads update the status area with non-sensitive text.

## Boundaries and Security

The controller owns dialog orchestration only; decoding and probing use `keystore-core` abstractions. Neither log messages, status text, errors, nor settings may include the pasted Base64, decoded bytes, passwords, or key material. The input is never written to disk.

## Tests

Add focused tests for unique detection, ambiguous selection, empty/invalid input, cancellation, and secret-safe error/log handling. Preserve the existing background-task behavior.
