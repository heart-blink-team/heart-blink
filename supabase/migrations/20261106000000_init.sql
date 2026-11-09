-- 하트깜빡이 schema. Every car signs in anonymously; a profile is that car's driver.
-- Two profiles become a couple. Everything else (messages, answers, drive status) belongs to a couple
-- and only its two members can see it.

create extension if not exists pgcrypto;

-- ---------------------------------------------------------------- profiles

create table public.profiles (
  id uuid primary key references auth.users on delete cascade,
  name text not null check (char_length(name) between 1 and 20),
  -- plate without spaces, e.g. 12가3456
  plate text not null unique check (plate ~ '^[0-9]{2,3}[가-힣][0-9]{4}$'),
  created_at timestamptz not null default now()
);

-- ---------------------------------------------------------------- couples

create table public.couples (
  id uuid primary key default gen_random_uuid(),
  requester uuid not null references public.profiles (id) on delete cascade,
  partner uuid not null references public.profiles (id) on delete cascade,
  status text not null default 'pending' check (status in ('pending', 'active', 'ended')),
  -- each nickname is written by the other person and shown on the owner's plate
  requester_nickname text check (char_length(requester_nickname) <= 5),
  partner_nickname text check (char_length(partner_nickname) <= 5),
  first_met date,
  -- the question both are on now; it moves on once both have answered
  question_no int not null default 1,
  created_at timestamptz not null default now(),
  accepted_at timestamptz,
  ended_at timestamptz,
  check (requester <> partner)
);

create index couples_requester on public.couples (requester) where status <> 'ended';
create index couples_partner on public.couples (partner) where status <> 'ended';

-- the couple a user is in (pending or active), at most one
create or replace function public.couple_of(uid uuid)
returns uuid language sql stable security definer set search_path = public as $$
  select id from couples
  where status in ('pending', 'active') and (requester = uid or partner = uid)
  order by created_at desc limit 1
$$;

create or replace function public.is_member(c uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from couples where id = c and auth.uid() in (requester, partner))
$$;

create or replace function public.is_active_member(c uuid)
returns boolean language sql stable security definer set search_path = public as $$
  select exists (select 1 from couples where id = c and status = 'active' and auth.uid() in (requester, partner))
$$;

create or replace function public.partner_of(uid uuid)
returns uuid language sql stable security definer set search_path = public as $$
  select case when requester = uid then partner else requester end
  from couples where status = 'active' and (requester = uid or partner = uid)
  limit 1
$$;

-- ---------------------------------------------------------------- messages

create table public.messages (
  id uuid primary key default gen_random_uuid(),
  couple_id uuid not null references public.couples (id) on delete cascade,
  sender uuid not null references public.profiles (id) on delete cascade,
  receiver uuid not null references public.profiles (id) on delete cascade,
  -- one main condition; the receiver's car decides when it happens
  condition_group text not null check (condition_group in ('place', 'vehicle', 'first_start', 'arrival', 'time')),
  -- vehicle: wiper, headlight, seat_heater, aircon, window
  -- time: dawn, morning, noon, afternoon, evening, night
  -- arrival: destination, parked; first_start: first_start; place: place
  condition_value text not null,
  place_name text,
  place_lat double precision,
  place_lng double precision,
  place_radius_m int check (place_radius_m between 50 and 2000),
  opens_from timestamptz not null default now(),
  body text not null default '' check (char_length(body) <= 200),
  audio_path text,
  audio_ms int,
  created_at timestamptz not null default now(),
  opened_at timestamptz,
  heard_at timestamptz,
  check (sender <> receiver),
  check (condition_group <> 'place' or (place_lat is not null and place_lng is not null))
);

create index messages_receiver on public.messages (receiver, opened_at);
create index messages_sender on public.messages (sender, created_at desc);

-- ---------------------------------------------------------------- questions and answers

create table public.questions (
  no int primary key,
  body text not null
);

create table public.answers (
  couple_id uuid not null references public.couples (id) on delete cascade,
  question_no int not null references public.questions (no),
  user_id uuid not null references public.profiles (id) on delete cascade,
  body text not null default '' check (char_length(body) <= 200),
  audio_path text,
  audio_ms int,
  created_at timestamptz not null default now(),
  primary key (couple_id, question_no, user_id)
);

create or replace function public.both_answered(c uuid, q int)
returns boolean language sql stable security definer set search_path = public as $$
  select count(*) = 2 from answers where couple_id = c and question_no = q
$$;

-- ---------------------------------------------------------------- driving and distance

-- only "driving or not"; no location ever leaves the car
create table public.drive_status (
  user_id uuid primary key references public.profiles (id) on delete cascade,
  driving boolean not null default false,
  updated_at timestamptz not null default now()
);

create table public.drive_days (
  user_id uuid not null references public.profiles (id) on delete cascade,
  day date not null,
  km numeric(8, 1) not null default 0 check (km >= 0),
  primary key (user_id, day)
);

-- ---------------------------------------------------------------- row level security

alter table public.profiles enable row level security;
alter table public.couples enable row level security;
alter table public.messages enable row level security;
alter table public.questions enable row level security;
alter table public.answers enable row level security;
alter table public.drive_status enable row level security;
alter table public.drive_days enable row level security;

create policy "own profile" on public.profiles for all
  using (id = auth.uid()) with check (id = auth.uid());
create policy "partner profile" on public.profiles for select
  using (exists (
    select 1 from couples c
    where c.status in ('pending', 'active')
      and ((c.requester = auth.uid() and c.partner = profiles.id) or (c.partner = auth.uid() and c.requester = profiles.id))
  ));

-- couples change only through the functions below
create policy "my couple" on public.couples for select
  using (auth.uid() in (requester, partner));

create policy "my messages" on public.messages for select
  using (auth.uid() in (sender, receiver));
create policy "send to partner" on public.messages for insert
  with check (sender = auth.uid() and is_active_member(couple_id) and receiver = partner_of(auth.uid()));

create policy "questions are public" on public.questions for select using (true);

-- my own answer always; my partner's only after both answered
create policy "answers" on public.answers for select
  using (is_member(couple_id) and (user_id = auth.uid() or both_answered(couple_id, question_no)));

create policy "own drive status" on public.drive_status for all
  using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy "partner drive status" on public.drive_status for select
  using (user_id = partner_of(auth.uid()));

create policy "own distance" on public.drive_days for all
  using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy "partner distance" on public.drive_days for select
  using (user_id = partner_of(auth.uid()));

-- ---------------------------------------------------------------- functions the app calls

-- 01: register or update my name and plate
create or replace function public.save_profile(p_name text, p_plate text)
returns public.profiles language plpgsql security definer set search_path = public as $$
declare r profiles;
begin
  insert into profiles (id, name, plate) values (auth.uid(), trim(p_name), replace(p_plate, ' ', ''))
  on conflict (id) do update set name = excluded.name, plate = excluded.plate
  returning * into r;
  insert into drive_status (user_id) values (auth.uid()) on conflict do nothing;
  return r;
end $$;

-- ask the owner of a plate to connect
create or replace function public.request_connection(p_partner_plate text)
returns public.couples language plpgsql security definer set search_path = public as $$
declare other uuid; r couples;
begin
  if couple_of(auth.uid()) is not null then raise exception 'already_connected'; end if;
  select id into other from profiles where plate = replace(p_partner_plate, ' ', '');
  if other is null then raise exception 'plate_not_found'; end if;
  if other = auth.uid() then raise exception 'own_plate'; end if;
  if couple_of(other) is not null then raise exception 'partner_busy'; end if;
  insert into couples (requester, partner) values (auth.uid(), other) returning * into r;
  return r;
end $$;

-- 02: the partner accepts (naming the requester's plate) or declines
create or replace function public.respond_connection(p_accept boolean, p_nickname_for_requester text)
returns public.couples language plpgsql security definer set search_path = public as $$
declare r couples;
begin
  select * into r from couples where partner = auth.uid() and status = 'pending' order by created_at desc limit 1;
  if r.id is null then raise exception 'no_request'; end if;
  if p_accept then
    update couples set status = 'active', accepted_at = now(), requester_nickname = nullif(trim(p_nickname_for_requester), '')
    where id = r.id returning * into r;
  else
    update couples set status = 'ended', ended_at = now() where id = r.id returning * into r;
  end if;
  return r;
end $$;

-- 03 and 20: the nickname I give my partner's plate, and the day we first met
create or replace function public.update_couple(p_partner_nickname text, p_first_met date)
returns public.couples language plpgsql security definer set search_path = public as $$
declare r couples;
begin
  update couples set
    requester_nickname = case when partner = auth.uid() then coalesce(nullif(trim(p_partner_nickname), ''), requester_nickname) else requester_nickname end,
    partner_nickname = case when requester = auth.uid() then coalesce(nullif(trim(p_partner_nickname), ''), partner_nickname) else partner_nickname end,
    first_met = coalesce(p_first_met, first_met)
  where id = couple_of(auth.uid()) and status = 'active'
  returning * into r;
  if r.id is null then raise exception 'not_connected'; end if;
  return r;
end $$;

-- 20: disconnect; the records stay unless p_delete_records
create or replace function public.end_couple(p_delete_records boolean)
returns void language plpgsql security definer set search_path = public as $$
declare c uuid := couple_of(auth.uid());
begin
  if c is null then return; end if;
  if p_delete_records then
    delete from couples where id = c;
  else
    update couples set status = 'ended', ended_at = now() where id = c;
  end if;
end $$;

-- 05: answer the current question; the next one opens once both have answered
create or replace function public.answer_question(p_body text, p_audio_path text, p_audio_ms int)
returns public.couples language plpgsql security definer set search_path = public as $$
declare r couples;
begin
  select * into r from couples where id = couple_of(auth.uid()) and status = 'active' for update;
  if r.id is null then raise exception 'not_connected'; end if;
  insert into answers (couple_id, question_no, user_id, body, audio_path, audio_ms)
  values (r.id, r.question_no, auth.uid(), coalesce(p_body, ''), p_audio_path, p_audio_ms)
  on conflict (couple_id, question_no, user_id) do update
    set body = excluded.body, audio_path = excluded.audio_path, audio_ms = excluded.audio_ms, created_at = now();
  if both_answered(r.id, r.question_no) and exists (select 1 from questions where no = r.question_no + 1) then
    update couples set question_no = question_no + 1 where id = r.id returning * into r;
  end if;
  return r;
end $$;

-- 09 to 17: the receiver's car reports that a message opened, or was heard
create or replace function public.mark_message(p_id uuid, p_opened boolean, p_heard boolean)
returns void language sql security definer set search_path = public as $$
  update messages set
    opened_at = case when p_opened then coalesce(opened_at, now()) else opened_at end,
    heard_at = case when p_heard then coalesce(heard_at, now()) else heard_at end
  where id = p_id and receiver = auth.uid()
$$;

-- 04: driving or not
create or replace function public.set_driving(p_driving boolean)
returns void language sql security definer set search_path = public as $$
  insert into drive_status (user_id, driving, updated_at) values (auth.uid(), p_driving, now())
  on conflict (user_id) do update set driving = excluded.driving, updated_at = now()
$$;

-- distance driven today, added up by the car as it goes
create or replace function public.add_distance(p_km numeric)
returns void language sql security definer set search_path = public as $$
  insert into drive_days (user_id, day, km) values (auth.uid(), (now() at time zone 'Asia/Seoul')::date, greatest(p_km, 0))
  on conflict (user_id, day) do update set km = drive_days.km + greatest(excluded.km, 0)
$$;

-- 15 to 18: the distance both have driven since connecting
create or replace function public.couple_distance()
returns numeric language sql stable security definer set search_path = public as $$
  select coalesce(sum(d.km), 0) from couples c
  join drive_days d on d.user_id in (c.requester, c.partner) and d.day >= (c.accepted_at at time zone 'Asia/Seoul')::date
  where c.id = couple_of(auth.uid()) and c.status = 'active'
$$;

revoke all on function public.couple_of(uuid), public.is_member(uuid), public.is_active_member(uuid),
  public.partner_of(uuid), public.both_answered(uuid, int) from public, anon;
grant execute on function public.couple_of(uuid), public.is_member(uuid), public.is_active_member(uuid),
  public.partner_of(uuid), public.both_answered(uuid, int) to authenticated;
grant execute on function public.save_profile(text, text), public.request_connection(text),
  public.respond_connection(boolean, text), public.update_couple(text, date), public.end_couple(boolean),
  public.answer_question(text, text, int), public.mark_message(uuid, boolean, boolean),
  public.set_driving(boolean), public.add_distance(numeric), public.couple_distance()
  to authenticated;

-- ---------------------------------------------------------------- realtime

alter publication supabase_realtime add table public.couples, public.messages, public.answers, public.drive_status;

-- ---------------------------------------------------------------- voice files

-- private bucket; files live under <couple id>/
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('voice', 'voice', false, 2097152, array['audio/mp4', 'audio/aac', 'audio/m4a'])
on conflict (id) do nothing;

create policy "couple voice read" on storage.objects for select
  using (bucket_id = 'voice' and public.is_member(((storage.foldername(name))[1])::uuid));
create policy "couple voice write" on storage.objects for insert
  with check (bucket_id = 'voice' and public.is_active_member(((storage.foldername(name))[1])::uuid));
