-- 하트깜빡이 schema, second step: one question a day, and what each car can sense.
-- Run once after 20261106000000_init.sql (SQL Editor or supabase db push). Safe to run again.

-- ---------------------------------------------------------------- one question a day

-- A day starts at 4 a.m. in Seoul, the same day as the app's 그날 첫 시동, so a drive after midnight
-- still belongs to the evening before.
alter table public.couples add column if not exists question_opened_at timestamptz not null default now();

create or replace function public.question_day(ts timestamptz)
returns date language sql stable set search_path = public as $$
  select ((ts at time zone 'Asia/Seoul') - interval '4 hours')::date
$$;

-- The next question opens only when both answered the current one and the current one opened on an earlier
-- day. A question one of us has not answered keeps waiting, and the days it waited are not made up: finishing
-- it late opens one new question at once, never more.
create or replace function public.advance_question(r public.couples)
returns public.couples language plpgsql security definer set search_path = public as $$
begin
  if both_answered(r.id, r.question_no)
     and question_day(r.question_opened_at) < question_day(now())
     and exists (select 1 from questions where no = r.question_no + 1) then
    update couples set question_no = question_no + 1, question_opened_at = now()
    where id = r.id returning * into r;
  end if;
  return r;
end $$;

-- 05: answer the current question (replaces the version that moved on as soon as both had answered)
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
  return advance_question(r);
end $$;

-- 04 and 05: opens today's question when both answered the last one. The car calls it when the app starts,
-- when it comes back to the front and at 4 a.m.; calling it twice the same day changes nothing.
create or replace function public.open_today_question()
returns public.couples language plpgsql security definer set search_path = public as $$
declare r couples;
begin
  select * into r from couples where id = couple_of(auth.uid()) and status = 'active' for update;
  if r.id is null then return null; end if;
  return advance_question(r);
end $$;

-- ---------------------------------------------------------------- what each car can sense

-- Message conditions this car cannot sense. A message opens in the receiver's car, so the sender's 06 hides
-- the ones the partner's car lists here.
alter table public.profiles add column if not exists unsupported text[] not null default '{}';
alter table public.profiles drop constraint if exists profiles_unsupported_values;
alter table public.profiles add constraint profiles_unsupported_values
  check (unsupported <@ array['wiper', 'headlight', 'seat_heater', 'aircon', 'window']::text[]);

create or replace function public.save_car_support(p_unsupported text[])
returns void language sql security definer set search_path = public as $$
  update profiles set unsupported = coalesce(p_unsupported, '{}') where id = auth.uid()
$$;

-- ---------------------------------------------------------------- grants

revoke all on function public.advance_question(public.couples), public.question_day(timestamptz)
  from public, anon, authenticated;
revoke all on function public.open_today_question(), public.save_car_support(text[]) from public, anon;
grant execute on function public.open_today_question(), public.save_car_support(text[]) to authenticated;
