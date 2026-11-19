-- Contact form submissions (phase 04).
--
-- Every row is a message a visitor sent through POST /api/v1/contact. Delivery is attempted
-- asynchronously, so `status` records the outcome: QUEUED on insert, then SENT or FAILED once
-- the mail provider has answered. A FAILED row is the only record of a message that never
-- arrived, which is why submissions are stored rather than only forwarded.
--
-- Column widths mirror the ContactRequest schema in docs/api/portfolio-api.yaml exactly, so a
-- payload the API accepts can never overflow a column.
create table contact_message (
  id uuid primary key default gen_random_uuid(),
  name varchar(100) not null,
  email varchar(254) not null,
  subject varchar(150),
  message varchar(5000) not null,
  -- SHA-256(salt + ip) as lowercase hex. Raw IP addresses are never stored.
  ip_hash char(64) not null,
  status varchar(10) not null check (status in ('QUEUED','SENT','FAILED')),
  created_at timestamptz not null default now()
);

-- The owner reads the newest messages first; also covers the "did anything fail today" scan.
create index contact_message_created_at_idx on contact_message (created_at desc);
