# Implementation Notes

## Architecture choices

- The repository is a set of independent Maven modules, one shaded jar per service, with no
  parent pom or shared artifact. Shared classes such as
  `co.wethinkcode.healthsafe.mq.MqConfig` are duplicated deliberately to preserve that
  independence.
- Services communicate over HTTP where a synchronous answer is required: staffing needs a
  ward and the current emergency level before it can compute anything.
- ActiveMQ is used in two different ways:
  - `staffing-events-topic` is non-persistent and fire-and-forget. Every subscriber should
    receive the update, and a missed event while ward-service is down is acceptable.
  - `equipment-failure-queue` is persistent with one logical consumer group. Each alert
    should be delivered even if equipment-alert-service is temporarily unavailable.
- Service state is intentionally in memory. This keeps the exercise focused on parsing,
  HTTP integration, and MQ semantics; a database is out of scope.

## Ingestion decisions

- Canonical ward IDs are `W-NN`. Values such as `w-5`, `W-05`, and `ward_05` merge to the
  same ward.
- Rows with missing or unusable ward IDs are rejected and reported, but do not stop the
  rest of the import.
- Text is trimmed and internal whitespace collapsed. Known placeholders become null and
  are flagged in `notes`.
- Duplicate rows merge by first-non-null-wins. Conflicts are recorded rather than silently
  overwritten. This is conservative: the first value may not always be the most correct,
  but it makes every merge auditable.
- Bed counts are accepted only as integers in `0..200`. The upper bound is a demo sanity
  check, not a clinical capacity rule. Ambiguous values such as `five` or `full` become
  null rather than being guessed.
- Dates are accepted in ISO, `M/d/yyyy`, and `d-M-yyyy` forms and normalized to ISO.
  Invalid calendar dates are nulled and flagged.
- Boolean variants are normalized; unrecognized flag values are nulled and flagged.
- Optional and unrecognized CSV columns are ignored so an evolving export does not break
  ingestion.

## Staffing and REST decisions

- The scheduling rule is deliberately simple: `requiredDoctors = 1 + emergencyLevel`,
  named `DOC-001` onward, with a 12-hour on-call window. It is a demonstration rule, not a
  clinical staffing model.
- Downstream failures are mapped explicitly: ward not found is `404`, invalid/unexpected
  downstream data is `502`, and unavailable/timeout is `503`.
- The schedule is stored before the topic event is published. A publish failure is logged
  but does not roll back a successful schedule calculation.

## Messaging decisions

- Topic events are non-persistent because they are state broadcasts, not work items.
  Ward-service keeps only the latest event per ward.
- Equipment-failure messages use persistent delivery because losing an alert is not
  acceptable.
- The equipment consumer stores before acknowledging, uses `CLIENT_ACKNOWLEDGE`, and calls
  `session.recover()` when storage fails. Redelivery is safe because the store deduplicates
  by `alertId`.
- Alert storage is in memory. The queue guarantees delivery to a running/restarted consumer
  before acknowledgement, but the service does not rebuild acknowledged alerts after its
  own restart.
- A malformed message that can never be parsed would be redelivered repeatedly. A
  production system should add a retry limit and dead-letter queue; that was left out to
  keep the exercise focused on the happy-path guarantee.

## Limitations

- No authentication, authorization, persistence database, or production observability.
- No distributed transactions between HTTP calls, schedule storage, and MQ publication.
- Ward and alert-level are assumed to be reachable at the configured URLs.
- The Docker Compose plugin is the documented broker workflow; an equivalent direct
  `docker run` command is included for environments without Compose.
