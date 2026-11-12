# common — ActiveMQ broker and MQ contracts

## Overview

Part of the [HealthSafe](../README.md) project. This folder holds the shared ActiveMQ
broker setup and the messaging contracts used by the services. It is not a service itself
and has no application port.

- Topic: `staffing-events-topic` — non-persistent broadcast of `SCHEDULE_UPDATED`
  events from `staffing-service` to every `ward-service` subscriber.
- Queue: `equipment-failure-queue` — persistent, single-consumer delivery of
  `EQUIPMENT_FAILURE` alerts from `ward-service` to `equipment-alert-service`.

The broker URL and destination names live in a duplicated
`co.wethinkcode.healthsafe.mq.MqConfig` class inside each participating module. Each
service is an independent Maven project, so the class is copied rather than imported.

## Project structure

```
common/
├── docker-compose.yml
└── README.md
```

## Build

Nothing to build here. The services that use the broker are built from their own folders.

## Run

```
docker compose up -d
```

- Client broker URL: `tcp://localhost:61616`
- Web console: http://localhost:8161 (default `admin`/`admin`)

The named Docker volume `activemq-data` is mounted at
`/opt/apache-activemq/data`, so queued messages and broker state survive container
recreation.

If the Compose plugin is not installed, use the equivalent:

```
docker run -d --name healthsafe-activemq \
  -p 61616:61616 -p 8161:8161 \
  -v healthsafe-activemq-data:/opt/apache-activemq/data \
  apache/activemq-classic:5.18.3
```

## Test

Confirm the broker is running:

```
docker compose ps
```

Then use the [root smoke test](../README.md#smoke-test) to verify both paths:

1. Create a schedule and confirm `ward-service` receives a staffing update from the topic.
2. Report an equipment failure and confirm it appears in `equipment-alert-service`.

The equipment queue is persistent: an alert sent while the consumer is stopped should be
delivered after `equipment-alert-service` starts again.
