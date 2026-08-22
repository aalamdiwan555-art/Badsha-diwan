-- Autopilot backend schema.
-- Apply this in Supabase after connecting the project.
-- No service-role key or client credential belongs in this file.

create extension if not exists pgcrypto;

create table if not exists public.profiles (
  id uuid references auth.users on delete cascade primary key,
  email text unique not null,
  role text not null default 'user' check (role in ('admin', 'user')),
  subscription_status text not null default 'inactive'
    check (subscription_status in ('active', 'inactive', 'expired', 'trial')),
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