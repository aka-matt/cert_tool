# Report Schema (v1.0.0)

Cert Tool exports FIPS Compatibility Assessment reports (and Conversion reports) as JSON, HTML,
and Markdown. All three formats share a versioned schema identified by `schemaVersion` = `1.0.0`.

## Top-level fields (JSON)

| Field          | Type        | Always present | Description                                                                          |
|----------------|-------------|----------------|--------------------------------------------------------------------------------------|
| schemaVersion  | string      | yes            | `"1.0.0"`. Consumers MUST branch on this.                                            |
| toolName       | string      | yes            | `"Cert Tool"` — identifies the producer.                                             |
| toolVersion    | string      | yes            | Producer version, kept in lock-step with the Maven `project.version`.                |
| title          | string      | yes            | Human-readable title chosen by the caller (UI fills this from the source filename).  |
| sourceLabel    | string      | yes            | Logical source label — the source file path for assessment, the plan source for conversion. **NEVER** the keystore contents or any password. |
| generatedAt    | string      | yes            | ISO-8601 UTC timestamp (`Instant.toString()`).                                       |
| disclaimer     | string      | yes            | The canonical FIPS non-certification disclaimer. **NEVER** omitted.                  |
| counts         | object      | yes            | Map of `AssessmentStatus.name()` → long. Empty statuses are present with value `0`.  |
| findings       | array       | yes            | One entry per `AssessmentFinding`. Empty array for conversion reports.               |
| conversion     | object\|null | no            | Present only when rendering a `ConversionReportEnvelope`. See below.                 |

### `counts` shape

```json
{
  "PASS": 1,
  "WARNING": 0,
  "FAIL": 1,
  "NOT_ASSESSABLE": 0,
  "NOT_APPLICABLE": 0
}
```

### `findings[]` shape

Each element carries the eight fields of `AssessmentFinding`:

| Field        | Type     | Description                                                                 |
|--------------|----------|-----------------------------------------------------------------------------|
| ruleId       | string   | Stable id from the rule (e.g. `CONTAINER_BCFKS_REQUIRED`).                  |
| title        | string   | Human-readable rule title.                                                  |
| status       | enum     | One of `PASS`, `WARNING`, `FAIL`, `NOT_ASSESSABLE`, `NOT_APPLICABLE`.       |
| severity     | enum     | One of `INFO`, `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`.                          |
| summary      | string   | Short description of what was checked.                                      |
| evidence     | string   | The data that produced the status (key size, algorithm name, etc.).         |
| remediation  | string   | How to bring the finding into compliance.                                   |
| references   | string[] | External links / NIST publication identifiers. Empty array if none.         |

### `conversion` shape (optional)

```json
{
  "preflightCodes": ["TARGET_FILE_EXISTS"],
  "reloadVerified": true,
  "targetPath": "/secure/store/target.bcfks",
  "writtenBytes": 2048
}
```

## Property ordering

JSON properties are emitted in alphabetical order so two identical inputs produce byte-identical
output. Consumers MAY rely on this for byte-level diffs.

## HTML output

Self-contained HTML document with embedded CSS. No external resources (no `<link>`, no `<script>`,
no `http://`/`https://` URLs). Schema version is carried in `<meta name="report-schema">`. Findings
appear as a table; per-status counts appear as cards above the table. All user-controlled strings
are HTML-escaped.

## Markdown output

UTF-8 Markdown. Schema version is carried in a leading `<!-- schema: 1.0.0 -->` comment.
Pipe characters and embedded newlines inside table cells are escaped. Findings appear as a GFM
table.

## Security guarantees

The following substrings MUST NEVER appear in any renderer output. The
`SanitizationGuard` enforces this and throws `RenderException` if it fires.

- Password-shaped strings: `password=hunter2`, `Password: hunter2`, `PASSWORD=…`, etc. (case-insensitive)
- PEM private-key markers: `-----BEGIN PRIVATE KEY-----`, `-----BEGIN RSA PRIVATE KEY-----`,
  `-----BEGIN ENCRYPTED PRIVATE KEY-----`, `-----BEGIN SECRET KEY-----`
- Forbidden phrases: `FIPS Certification`, `Official FIPS Validation`, `NIST Certified`,
  `正式认证结论`

Every renderer runs `SanitizationGuard.enforce(...)` on its output before returning. If a future
renderer introduces a leak, the guard refuses to hand the bytes back.

Reports MUST NOT contain passwords, private keys, secret-key material, or full keystore bytes.
Reporters never receive these as input.