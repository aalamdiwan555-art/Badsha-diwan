-- Autopilot backend schema.
-- Apply this in Supabase after connecting the project.
-- No service-role key or client credential belongs in this file.

create extension if not exists pgcrypto;

create table if not exists public.profiles (
  id uuid references auth.users on delete cascade primary key,
  email text unique not null,
  role text not null default 'user' check (role in ('admin', 'user')),
  subscription_status text not null default 'inactive'
    check (subscription_status in ('active', 'inactive', 'expired', 'trial', 'lifetime')),
  subscription_expires_at timestamptz,
  is_ad_free boolean not null default false,
  ads_watched_today integer not null default 0,
  total_ads_watched integer not null default 0,
  last_ad_watched_at timestamptz,
  selected_scenario_id uuid,
  selected_scenario_name text,
  device_info jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

-- Supabase Auth creates auth.users, but the Android app reads public.profiles.
-- Bootstrap the profile in the same transaction so registration can continue
-- without a privileged client-side insert.
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into public.profiles (id, email, role)
  values (
    new.id,
    lower(trim(coalesce(new.email, ''))),
    case
      when lower(trim(coalesce(new.email, ''))) = 'aalamdiwan555@gmail.com'
        then 'admin'
      else 'user'
    end
  )
  on conflict (id) do nothing;
  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
after insert on auth.users
for each row execute function public.handle_new_user();

alter table public.profiles add column if not exists is_banned boolean not null default false;
alter table public.profiles add column if not exists ban_reason text;

create table if not exists public.scenarios (
  id uuid primary key default gen_random_uuid(),
  admin_id uuid references public.profiles(id) not null,
  name text not null,
  description text,
  scenario_data jsonb not null,
  is_active boolean not null default true,
  is_global boolean not null default true,
  target_users uuid[],
  version integer not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

-- Keep the mode selector bounded and unambiguous even when an administrator
-- writes through the API instead of the Android dashboard.
create or replace function public.validate_scenario_limits()
returns trigger
language plpgsql
set search_path = public
as $$
begin
  if length(trim(new.name)) = 0 or length(new.name) > 80 then
    raise exception 'Scenario name must contain 1 to 80 characters';
  end if;
  if jsonb_typeof(new.scenario_data) <> 'object' then
    raise exception 'Scenario data must be a JSON object';
  end if;
  if new.is_active and (
    select count(*) from public.scenarios
    where is_active
      and id <> new.id
  ) >= 15 then
    raise exception 'A maximum of 15 active scenarios is supported';
  end if;
  if new.is_active and exists (
    select 1 from public.scenarios
    where is_active
      and id <> new.id
      and lower(trim(name)) = lower(trim(new.name))
  ) then
    raise exception 'Scenario names must be unique';
  end if;
  return new;
end;
$$;

drop trigger if exists scenarios_validate_limits on public.scenarios;
create trigger scenarios_validate_limits
before insert or update of name, scenario_data, is_active on public.scenarios
for each row execute function public.validate_scenario_limits();

alter table public.profiles
  drop constraint if exists profiles_selected_scenario_id_fkey;
alter table public.profiles
  add constraint profiles_selected_scenario_id_fkey
  foreign key (selected_scenario_id) references public.scenarios(id)
  on delete set null;

create table if not exists public.user_scenarios (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references public.profiles(id) on delete cascade not null,
  scenario_id uuid references public.scenarios(id) on delete cascade not null,
  is_downloaded boolean not null default false,
  is_enabled boolean not null default true,
  downloaded_at timestamptz,
  unique (user_id, scenario_id)
);

create table if not exists public.subscriptions (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references public.profiles(id) on delete cascade not null,
  granted_by uuid references public.profiles(id),
  plan_type text not null default 'reward_ad'
    check (plan_type in ('reward_ad', 'admin_granted')),
  duration_days integer,
  started_at timestamptz not null default now(),
  expires_at timestamptz,
  is_active boolean not null default true,
  note text,
  created_at timestamptz not null default now()
);

create table if not exists public.ad_events (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references public.profiles(id) on delete set null,
  ad_type text not null,
  event_type text not null,
  revenue_estimate numeric(10, 6),
  created_at timestamptz not null default now()
);

create table if not exists public.click_sessions (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references public.profiles(id) on delete set null,
  scenario_id uuid references public.scenarios(id) on delete set null,
  started_at timestamptz not null default now(),
  ended_at timestamptz,
  total_clicks integer not null default 0,
  duration_seconds integer,
  device_info jsonb
);

create table if not exists public.app_settings (
  id integer primary key default 1,
  interstitial_interval_minutes integer not null default 2,
  reward_ads_for_one_day integer not null default 20,
  trial_days integer not null default 3,
  updated_at timestamptz not null default now()
);

create table if not exists public.admin_logs (
  id uuid primary key default gen_random_uuid(),
  admin_id uuid references public.profiles(id) on delete set null,
  action text not null,
  target_user_id uuid references public.profiles(id) on delete set null,
  details jsonb,
  created_at timestamptz not null default now()
);

insert into public.app_settings (id) values (1)
on conflict (id) do nothing;

create or replace function public.is_admin()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.profiles
    where id = auth.uid()
      and (role = 'admin' or email = 'aalamdiwan555@gmail.com')
  );
$$;

alter table public.profiles enable row level security;
alter table public.scenarios enable row level security;
alter table public.user_scenarios enable row level security;
alter table public.subscriptions enable row level security;
alter table public.ad_events enable row level security;
alter table public.click_sessions enable row level security;
alter table public.app_settings enable row level security;
alter table public.admin_logs enable row level security;

create policy "profiles_self_read" on public.profiles
  for select using (id = auth.uid() or public.is_admin());
create policy "profiles_self_update" on public.profiles
  for update using (id = auth.uid() or public.is_admin())
  with check (id = auth.uid() or public.is_admin());

create policy "scenarios_visible_to_users" on public.scenarios
  for select using (
    public.is_admin()
    or (is_active and (is_global or auth.uid() = any(target_users)))
  );
create policy "scenarios_admin_write" on public.scenarios
  for all using (public.is_admin()) with check (public.is_admin());

create policy "user_scenarios_self_read" on public.user_scenarios
  for select using (user_id = auth.uid() or public.is_admin());
create policy "user_scenarios_admin_write" on public.user_scenarios
  for all using (public.is_admin()) with check (public.is_admin());

create policy "subscriptions_self_read" on public.subscriptions
  for select using (user_id = auth.uid() or public.is_admin());
create policy "subscriptions_admin_write" on public.subscriptions
  for all using (public.is_admin()) with check (public.is_admin());

create policy "ad_events_self_insert" on public.ad_events
  for insert with check (user_id = auth.uid());
create policy "ad_events_admin_read" on public.ad_events
  for select using (public.is_admin());

create policy "click_sessions_self_access" on public.click_sessions
  for all using (user_id = auth.uid() or public.is_admin())
  with check (user_id = auth.uid() or public.is_admin());

create policy "settings_user_read" on public.app_settings
  for select using (auth.uid() is not null);
create policy "settings_admin_write" on public.app_settings
  for all using (public.is_admin()) with check (public.is_admin());

create policy "admin_logs_admin_read" on public.admin_logs
  for select using (public.is_admin());

-- Privileged mutations are exposed as narrowly-scoped RPCs rather than
-- allowing the Android client to write subscription state directly.
create or replace function public.admin_grant_subscription(
  target_user_id uuid,
  grant_duration_days integer,
  grant_note text default null
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  expires_at_value timestamptz;
begin
  if not public.is_admin() then
    raise exception 'Administrator access required';
  end if;

  if grant_duration_days = 99999 then
    expires_at_value := '2099-12-31T00:00:00Z'::timestamptz;
  elsif grant_duration_days between 1 and 3650 then
    expires_at_value := now() + make_interval(days => grant_duration_days);
  else
    raise exception 'Unsupported subscription duration';
  end if;

  update public.profiles
  set subscription_status = case
        when grant_duration_days = 99999 then 'lifetime'
        else 'active'
      end,
      subscription_expires_at = expires_at_value,
      updated_at = now()
  where id = target_user_id;

  if not found then
    raise exception 'Target user not found';
  end if;

  insert into public.subscriptions (
    user_id, granted_by, plan_type, duration_days, expires_at, note
  ) values (
    target_user_id, auth.uid(), 'admin_granted', grant_duration_days,
    expires_at_value, grant_note
  );

  insert into public.admin_logs (admin_id, action, target_user_id, details)
  values (
    auth.uid(), 'grant_subscription', target_user_id,
    jsonb_build_object('duration_days', grant_duration_days, 'note', grant_note)
  );
end;
$$;

create or replace function public.admin_set_ad_free(
  target_user_id uuid,
  ad_free_value boolean
)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if not public.is_admin() then
    raise exception 'Administrator access required';
  end if;

  update public.profiles
  set is_ad_free = ad_free_value,
      updated_at = now()
  where id = target_user_id;

  if not found then
    raise exception 'Target user not found';
  end if;

  insert into public.admin_logs (admin_id, action, target_user_id, details)
  values (
    auth.uid(),
    case when ad_free_value then 'grant_ad_free' else 'remove_ad_free' end,
    target_user_id,
    jsonb_build_object('is_ad_free', ad_free_value)
  );
end;
$$;

create or replace function public.admin_set_banned(
  target_user_id uuid,
  banned_value boolean,
  reason text default null
)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if not public.is_admin() then
    raise exception 'Administrator access required';
  end if;
  if target_user_id = auth.uid() then
    raise exception 'Administrators cannot ban themselves';
  end if;

  update public.profiles
  set is_banned = banned_value,
      ban_reason = case when banned_value then nullif(trim(reason), '') else null end,
      updated_at = now()
  where id = target_user_id;
  if not found then
    raise exception 'Target user not found';
  end if;

  insert into public.admin_logs (admin_id, action, target_user_id, details)
  values (
    auth.uid(),
    case when banned_value then 'ban_user' else 'unban_user' end,
    target_user_id,
    jsonb_build_object('is_banned', banned_value, 'reason', reason)
  );
end;
$$;

-- These SECURITY DEFINER functions must not be callable anonymously. Their
-- internal admin check remains the authorization boundary for signed-in users.
revoke all on function public.admin_grant_subscription(uuid, integer, text) from public;
grant execute on function public.admin_grant_subscription(uuid, integer, text) to authenticated;
revoke all on function public.admin_set_ad_free(uuid, boolean) from public;
grant execute on function public.admin_set_ad_free(uuid, boolean) to authenticated;
revoke all on function public.admin_set_banned(uuid, boolean, text) from public;
grant execute on function public.admin_set_banned(uuid, boolean, text) to authenticated;

-- Reward completion is intentionally handled as one atomic server operation.
-- The Android client should call this only after the ad provider reports a
-- completed rewarded impression; it must never update profile counters locally.
create or replace function public.claim_reward_ad()
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  current_profile public.profiles%rowtype;
  watched_today_value integer;
  new_expiry timestamptz;
  unlocked boolean := false;
begin
  if auth.uid() is null then
    raise exception 'Authentication required';
  end if;

  select *
  into current_profile
  from public.profiles
  where id = auth.uid()
  for update;

  if not found then
    raise exception 'Profile not found';
  end if;
  if current_profile.is_banned then
    raise exception 'This account has been disabled';
  end if;
  if current_profile.is_ad_free then
    raise exception 'Reward ads are unavailable for ad-free accounts';
  end if;

  -- Counters are scoped to the UTC calendar day, matching the documented
  -- reset schedule and avoiding device-clock manipulation.
  if current_profile.last_ad_watched_at is null
     or (current_profile.last_ad_watched_at at time zone 'UTC')::date
        <> (now() at time zone 'UTC')::date then
    watched_today_value := 0;
  else
    watched_today_value := greatest(current_profile.ads_watched_today, 0);
  end if;

  if watched_today_value >= 20 then
    raise exception 'Daily reward limit reached';
  end if;

  watched_today_value := watched_today_value + 1;

  if watched_today_value = 20 then
    unlocked := true;
    new_expiry := greatest(
      coalesce(current_profile.subscription_expires_at, now()),
      now()
    ) + interval '1 day';

    update public.profiles
    set ads_watched_today = 0,
        total_ads_watched = greatest(total_ads_watched, 0) + 1,
        last_ad_watched_at = now(),
        subscription_status = 'active',
        subscription_expires_at = new_expiry,
        updated_at = now()
    where id = auth.uid();

    insert into public.subscriptions (
      user_id, plan_type, duration_days, expires_at, note
    ) values (
      auth.uid(), 'reward_ad', 1, new_expiry, 'Unlocked after 20 verified reward ads'
    );
  else
    update public.profiles
    set ads_watched_today = watched_today_value,
        total_ads_watched = greatest(total_ads_watched, 0) + 1,
        last_ad_watched_at = now(),
        updated_at = now()
    where id = auth.uid();
  end if;

  insert into public.ad_events (user_id, ad_type, event_type)
  values (auth.uid(), 'rewarded', case when unlocked then 'completed_unlock' else 'completed' end);

  return jsonb_build_object(
    'ads_watched_today', case when unlocked then 0 else watched_today_value end,
    'unlocked', unlocked,
    'subscription_expires_at', new_expiry
  );
end;
$$;

revoke all on function public.claim_reward_ad() from public;
grant execute on function public.claim_reward_ad() to authenticated;