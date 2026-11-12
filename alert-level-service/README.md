# AlertLevelServiceApp

## Overview

Tracks the hospital Emergency Status on a scale of `0..8`, where `8` is a full Code Blue.
It is an independent Maven module and part of the [HealthSafe](../README.md) project.

REST consumer: [`staffing-service`](../staffing-service), which reads the current level
before computing an on-call schedule.

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/health` | Liveness check, returns `OK` |
| `GET` | `/alert-level` | Current level as `{ "level": 0 }` |
| `PUT` | `/alert-level` | Set the level from `{ "level": 0-8 }` |
| `POST` | `/alert-level` | Same as `PUT` |

The body must contain an integral JSON `level` between `0` and `8`; missing, non-integer,
or out-of-range values return `400`. The service starts at level `0`, and state is in
memory only.

## Build and run

```
mvn package
java -jar target/alert-level-service.jar
```

Listens on port `7032`.

## Test

```
mvn test
```
