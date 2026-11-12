# StaffingServiceApp

## Overview

Computes on-call schedules from a ward and the current hospital Emergency Status. It calls
`ward-service` and `alert-level-service` synchronously over HTTP, stores the latest schedule
per ward, and publishes a `SCHEDULE_UPDATED` event to ActiveMQ. It is an independent Maven
module and part of the [HealthSafe](../README.md) project.

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/health` | Liveness check, returns `OK` |
| `GET` | `/staffing?wardId=W-01` | Calculate a schedule without storing or publishing it |
| `POST` | `/schedules` | Calculate, store, and publish a schedule; returns `201` |
| `GET` | `/schedules/{wardId}` | Last stored schedule; `404` if none |

`POST /schedules` body:

```json
{ "wardId": "W-01" }
```

A schedule contains `wardId`, `wing`, `department`, `emergencyLevel`, `requiredDoctors`,
`doctors`, `onCallWindowHours`, and `generatedAt`.

## Scheduling rule

This is a deliberately small demo rule:

```
requiredDoctors = 1 + emergencyLevel
doctors = DOC-001 .. DOC-N
onCallWindowHours = 12
```

The stored schedule and topic event use the canonical ward ID.

## Error mapping

| Situation | Response |
|---|---|
| Missing/blank `wardId` | `400` |
| Ward not found | `404` |
| Invalid downstream payload or non-200 response | `502` |
| Downstream service unavailable/timeout | `503` |

A broker publish failure is logged but does not fail the schedule request; the synchronous
calculation and storage have already succeeded. The topic is a non-persistent,
fire-and-forget broadcast.

## Configuration

| Variable | Default |
|---|---|
| `WARD_SERVICE_URL` | `http://localhost:7031` |
| `ALERT_LEVEL_SERVICE_URL` | `http://localhost:7032` |
| `HEALTHSAFE_BROKER_URL` | `tcp://localhost:61616` |

## Build and run

```
mvn package
java -jar target/staffing-service.jar
```

Listens on port `7033`.

## Test

```
mvn test
```
