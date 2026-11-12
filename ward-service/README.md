# WardServiceApp

## Overview

Serves ward and department data sourced from `ingestion-service`, receives staffing
updates from the ActiveMQ topic, and publishes equipment failures to the ActiveMQ queue.
It is an independent Maven module with no parent pom.

Part of the [HealthSafe](../README.md) project. MQ names and the broker URL come from the
duplicated `co.wethinkcode.healthsafe.mq.MqConfig` class in this module.

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/health` | Liveness check, returns `OK` |
| `GET` | `/wards` | All cleaned wards |
| `GET` | `/wards/{id}` | One ward; `404` if unknown |
| `GET` | `/departments` | Distinct departments |
| `POST` | `/wards/refresh` | Reload wards from ingestion; `503` if ingestion is unavailable |
| `GET` | `/staffing-updates` | Latest staffing event per ward |
| `GET` | `/staffing-updates/{wardId}` | Latest event for one ward; `404` if none |
| `POST` | `/wards/{id}/equipment-failures` | Publish an equipment failure; `202` on success |

Equipment failure request:

```json
{
  "equipmentId": "EQ-17",
  "description": "Ventilator stopped",
  "severity": "CRITICAL"
}
```

`equipmentId` is required. `severity` defaults to `CRITICAL`; `description` may be
omitted. Unknown wards return `404`, a missing equipment ID returns `400`, and a broker
failure returns `503`. On success, the response contains the generated `alertId`, `type`,
canonical `wardId`, and `reportedAt` alongside the request fields.

## MQ behavior

- Subscribes to `staffing-events-topic` and stores the latest `SCHEDULE_UPDATED` event per
  canonical ward ID.
- Publishes `EQUIPMENT_FAILURE` messages to `equipment-failure-queue` with persistent
  delivery. Publishing is intentionally not retried here; a broker failure is surfaced as
  `503`.

## Configuration

| Variable | Default |
|---|---|
| `INGESTION_URL` | `http://localhost:7030` |
| `HEALTHSAFE_BROKER_URL` | `tcp://localhost:61616` |

## Build and run

```
mvn package
java -jar target/ward-service.jar
```

Listens on port `7031`.

## Test

```
mvn test
```
