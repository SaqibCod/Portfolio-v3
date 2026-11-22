-- Uptime probe results (phase 07).
--
-- One row per health probe of one monitored project. The probe schedule lives in
-- `app.status` configuration, not here: this table knows nothing about which services exist,
-- only about checks that happened. That is deliberate -- adding or removing a service is a
-- configuration change, and the history of a service that is no longer monitored stays
-- readable until retention sweeps it away.
--
-- `state` holds the enum constant names from ServiceState. UNKNOWN is absent from the check
-- constraint on purpose: it means "no probe covers this moment", which is the absence of a row
-- rather than a row with a value. StatusService invents it when a bucket turns out empty.
--
-- `response_time_ms` is null exactly when the probe never completed (timeout, connection
-- refused, a non-2xx answer), so an average over this column is an average over the probes
-- that actually measured something.
create table status_check (
  id bigint generated always as identity primary key,
  service_slug varchar(50) not null,
  checked_at timestamptz not null,
  state varchar(12) not null check (state in ('OPERATIONAL','DEGRADED','DOWN')),
  response_time_ms integer
);

-- Covers every read there is: one service, a time range, newest first. Both the overview
-- (latest row per service, a `distinct on` that walks this index backwards) and the history
-- queries (a range scan from the window start) are index-only ranges whatever the retention
-- window holds.
create index status_check_slug_time_idx on status_check (service_slug, checked_at desc);
