-- Guestbook entries (phase 05).
--
-- Every row is a note a visitor left through POST /api/v1/guestbook. Nothing is public on
-- arrival: an entry is stored as PENDING and only appears on the site once the owner approves
-- it from /admin, which is what makes an anonymous, account-free guestbook safe to run.
-- `moderated_at` records when that decision was made and stays null until it is.
--
-- Column widths mirror the CreateGuestbookEntry schema in docs/api/portfolio-api.yaml exactly,
-- so a payload the API accepts can never overflow a column. The API spells the statuses in
-- lowercase (`pending`); the uppercase values below are the enum constant names, and
-- GuestbookStatus is the only place that maps between the two.
create table guestbook_entry (
  id uuid primary key default gen_random_uuid(),
  name varchar(50) not null,
  message varchar(500) not null,
  status varchar(10) not null default 'PENDING' check (status in ('PENDING','APPROVED','REJECTED')),
  -- SHA-256(salt + ip) as lowercase hex. Raw IP addresses are never stored.
  ip_hash char(64) not null,
  created_at timestamptz not null default now(),
  moderated_at timestamptz
);

-- Covers the only read shape there is: one status, newest first, seeking past a cursor.
-- The column order matches the keyset predicate in GuestbookRepository — equality on `status`,
-- then the (created_at desc, id desc) tuple both the comparison and the sort use — so a page
-- is an index range scan whatever its depth.
create index guestbook_status_created_idx on guestbook_entry (status, created_at desc, id desc);
