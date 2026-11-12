# IngestionServiceApp

## Overview

Parses and cleans `wards-outdated.csv`, the deliberately messy legacy ward export, and
exposes the result over REST. This is the first stage of the [HealthSafe](../README.md)
pipeline. It is an independent Maven module with no parent pom.

REST consumer: [`ward-service`](../ward-service), which loads `GET /wards` at startup and
can refresh through it.

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/health` | Liveness check, returns `OK` |
| `GET` | `/wards` | Cleaned ward records |
| `GET` | `/ingestion-report` | Counts and rejected-row details |

A ward has this shape:

```json
{
  "wardId": "W-05",
  "wing": "East Wing",
  "department": "Paediatrics",
  "bedsAvailable": null,
  "lastInspectionDate": "2024-03-01",
  "operational": true,
  "notes": ["bedsAvailable was non-numeric ('five') — flagged for follow-up"]
}
```

The report contains `totalRows`, `acceptedRows`, `uniqueWards`, `mergedDuplicates`,
`rejectedRows`, `flaggedWards`, and a list of rejected rows with row number, raw ID, and
reason.

## Cleaning rules

- Ward IDs are canonicalized to `W-NN`; `W-05`, `w-5`, and `ward_05` refer to the same
  ward. A missing or unusable ID rejects only that row.
- Text is trimmed, non-breaking spaces are normalized, and repeated whitespace collapses to
  one space. Wing names use title case.
- Known placeholders (`N/A`, `TBD`, `unknown`, `-`, `NaN`, blank, and similar) become
  `null` and are noted.
- Duplicate canonical IDs are merged. For each field the first non-null value wins; later
  values are kept only to fill nulls, and conflicts are recorded in `notes`.
- Beds must be an integer in `0..200`. Negative, out-of-range, spelled-out, and otherwise
  non-numeric values become `null` with a note.
- Dates accept ISO, `M/d/yyyy`, and `d-M-yyyy` forms, with one- or two-digit day/month.
  Valid dates are normalized to ISO; invalid dates become `null` with a note.
- Booleans accept `Y/N`, `yes/no`, `true/false`, and `1/0` case-insensitively; other
  values become `null` with a note.
- Department spelling variants are normalized, including
  `Pediatrics`/`Paediatrics`, `ICU`/`Intensive Care`, and `Maternity`.
- Optional and unknown columns are ignored, so extra export columns do not break the parse.

See [IMPLEMENTATION_NOTES.md](../IMPLEMENTATION_NOTES.md) for the reasoning and tradeoffs.

## Build and run

```
mvn package
java -jar target/ingestion-service.jar
```

Listens on port `7030`.

## Test

```
mvn test
```
