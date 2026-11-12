# EquipmentAlertServiceApp

## Overview

Consumes critical medical equipment failure alerts from the persistent ActiveMQ
`equipment-failure-queue` and serves the stored alerts over REST. It is an independent
Maven module and the alerting service in the [HealthSafe](../README.md) project.

- Producer: [`ward-service`](../ward-service)
- Consumer: this service

A queue is used rather than a topic because each alert needs guaranteed,
single-consumer delivery, including while this service is briefly down. This contrasts
with the broadcast `staffing-events-topic` in [`../common/`](../common).

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/health` | Liveness check, returns `OK` |
| `GET` | `/alerts` | All stored alerts, ordered by report time |
| `GET` | `/alerts/{alertId}` | One alert; `404` if unknown |

An alert has this shape:

```json
{
  "alertId": "d1c4...",
  "type": "EQUIPMENT_FAILURE",
  "wardId": "W-01",
  "equipmentId": "EQ-17",
  "description": "Ventilator stopped",
  "severity": "CRITICAL",
  "reportedAt": "2026-01-01T00:00:00Z"
}
```

## Delivery guarantees

- The consumer uses `CLIENT_ACKNOWLEDGE`.
- It stores the alert before acknowledging the JMS message.
- On a storage failure it logs the error and calls `session.recover()` so the broker
  redelivers the message.
- Alerts are deduplicated by `alertId`, making redelivery idempotent.
- Messages are persistent, so they survive a broker restart when the broker data volume is
  retained.

State is in memory; the service does not re-read broker history after a restart. Once a
message has been acknowledged, it exists only in this process's alert store.

## Configuration

| Variable | Default |
|---|---|
| `HEALTHSAFE_BROKER_URL` | `tcp://localhost:61616` |

## Build and run

```
mvn package
java -jar target/equipment-alert-service.jar
```

Listens on port `7034`.

## Test

```
mvn test
```

The tests use an embedded ActiveMQ broker and cover storage, consumption, session recovery,
idempotency, and the REST endpoints.
