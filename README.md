# HealthSafe
verification code: WTC-LS2Y9YF5

## Overview

HealthSafe is a small set of independently runnable Java services that clean a legacy
ward export, expose ward and emergency-status data over HTTP, compute on-call staffing
schedules, and deliver critical equipment alerts through ActiveMQ.

Every class lives in the flat package `co.wethinkcode.healthsafe`. Each service is an
independent Maven project with its own pom and shaded executable jar; there is no parent
pom and no shared artifact between services.

| Service | Folder | Port | Role |
|---|---|---|---|
| IngestionServiceApp | [`ingestion-service/`](ingestion-service) | 7030 | Parses and cleans `wards-outdated.csv` |
| WardServiceApp | [`ward-service/`](ward-service) | 7031 | Serves wards/departments, receives staffing events, publishes equipment failures |
| AlertLevelServiceApp | [`alert-level-service/`](alert-level-service) | 7032 | Tracks the hospital Emergency Status (0-8) |
| StaffingServiceApp | [`staffing-service/`](staffing-service) | 7033 | Computes on-call schedules from ward and emergency status |
| EquipmentAlertServiceApp | [`equipment-alert-service/`](equipment-alert-service) | 7034 | Consumes and serves persistent equipment-failure alerts |

The ActiveMQ broker and MQ configuration notes live in [`common/`](common).

## Architecture

1. `ingestion-service` reads the classpath resource `wards-outdated.csv`, cleans and
   deduplicates it, and exposes `GET /wards` plus an ingestion report.
2. `ward-service` loads those records at startup, can refresh them on demand, and serves
   ward/department lookups to `staffing-service`.
3. `staffing-service` calls `ward-service` for the ward and `alert-level-service` for the
   current status, computes a 12-hour on-call schedule, stores it, and publishes a
   `SCHEDULE_UPDATED` event to `staffing-events-topic`.
4. `ward-service` subscribes to that topic and exposes the latest event per ward, so the
   frontend can react without polling the staffing service.
5. `ward-service` publishes `EQUIPMENT_FAILURE` messages to the persistent
   `equipment-failure-queue`; `equipment-alert-service` consumes, stores, and acknowledges
   them so an alert survives a consumer restart once the broker has accepted it.

A topic is used for schedule broadcasts because every subscriber should see every event and
loss during downtime is acceptable. A queue is used for equipment failures because each
alert needs guaranteed, single-consumer delivery.

## Build

Requirements: Java 17+, Maven 3.8+, and Docker for the broker.

Build and test every module from the repository root:

```
./scripts/build-all.sh
```

Or build one module at a time:

```
cd ward-service
mvn package
```

## Run

Start the broker first:

```
cd common
docker compose up -d
```

Then start each service in its own terminal, in this order:

```
# terminal 1
cd ingestion-service && java -jar target/ingestion-service.jar

# terminal 2
cd ward-service && java -jar target/ward-service.jar

# terminal 3
cd alert-level-service && java -jar target/alert-level-service.jar

# terminal 4
cd staffing-service && java -jar target/staffing-service.jar

# terminal 5
cd equipment-alert-service && java -jar target/equipment-alert-service.jar
```

Environment overrides:

| Variable | Default | Used by |
|---|---|---|
| `INGESTION_URL` | `http://localhost:7030` | ward-service |
| `WARD_SERVICE_URL` | `http://localhost:7031` | staffing-service |
| `ALERT_LEVEL_SERVICE_URL` | `http://localhost:7032` | staffing-service |
| `HEALTHSAFE_BROKER_URL` | `tcp://localhost:61616` | ward, staffing, equipment-alert |

## Integration contracts

| From | To | Call | Purpose |
|---|---|---|---|
| ward-service | ingestion-service | `GET /wards` | Cleaned ward records |
| staffing-service | ward-service | `GET /wards/{id}` | Validate a ward; `404` when unknown |
| staffing-service | alert-level-service | `GET /alert-level` | Current `{ "level": 0-8 }` |
| staffing-service | staffing-events-topic | `SCHEDULE_UPDATED` | Broadcast a schedule change |
| ward-service | staffing-events-topic | subscribe | Keep the latest event per ward |
| ward-service | equipment-failure-queue | `EQUIPMENT_FAILURE` | Publish a failure alert |
| equipment-alert-service | equipment-failure-queue | subscribe | Store and acknowledge alerts |

Key endpoints:

| Method | Endpoint | Service |
|---|---|---|
| `GET` | `/wards` and `/ingestion-report` | ingestion |
| `GET` | `/wards`, `/wards/{id}`, `/departments` | ward |
| `POST` | `/wards/refresh` | ward |
| `GET` | `/staffing-updates`, `/staffing-updates/{wardId}` | ward |
| `POST` | `/wards/{id}/equipment-failures` | ward |
| `GET` | `/alert-level` | alert-level |
| `PUT`/`POST` | `/alert-level` with `{ "level": 0-8 }` | alert-level |
| `GET` | `/staffing?wardId=W-01` | staffing |
| `POST` | `/schedules` with `{ "wardId": "W-01" }` | staffing |
| `GET` | `/schedules/{wardId}` | staffing |
| `GET` | `/alerts`, `/alerts/{alertId}` | equipment-alert |

Every service exposes `GET /health` returning `OK`.

## Smoke test

With the broker and all five services running:

```
curl http://localhost:7030/health
curl http://localhost:7031/health
curl http://localhost:7032/health
curl http://localhost:7033/health
curl http://localhost:7034/health

curl http://localhost:7030/wards | jq '.[0]'
curl http://localhost:7031/wards/W-01

curl -X PUT http://localhost:7032/alert-level \
  -H 'Content-Type: application/json' -d '{"level":5}'

curl -X POST http://localhost:7033/schedules \
  -H 'Content-Type: application/json' -d '{"wardId":"W-01"}'
curl http://localhost:7033/schedules/W-01

sleep 1
curl http://localhost:7031/staffing-updates/W-01

curl -X POST http://localhost:7031/wards/W-01/equipment-failures \
  -H 'Content-Type: application/json' \
  -d '{"equipmentId":"EQ-17","description":"Ventilator stopped","severity":"CRITICAL"}'

sleep 1
curl http://localhost:7034/alerts
```

The schedule response should contain `requiredDoctors` equal to
`1 + emergencyLevel`, and the equipment alert should appear under `/alerts`.

If the Docker Compose plugin is unavailable, the equivalent broker container is:

```
docker run -d --name healthsafe-activemq \
  -p 61616:61616 -p 8161:8161 \
  -v healthsafe-activemq-data:/opt/apache-activemq/data \
  apache/activemq-classic:5.18.3
```

The web console is at http://localhost:8161 (default `admin`/`admin`).

## Test

Each module has JUnit 5 tests and the Surefire plugin. Run them individually:

```
cd ingestion-service && mvn test
cd alert-level-service && mvn test
cd ward-service && mvn test
cd staffing-service && mvn test
cd equipment-alert-service && mvn test
```

The full build script runs `mvn clean package` for every module, which also runs its tests.
