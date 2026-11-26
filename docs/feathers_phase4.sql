-- Phase 4: Feathers hybrid mirror + leaderboard (theGarden Supabase).
-- Run once in the SQL editor. Additive and non-breaking (existing Flock features untouched).

-- 1) Add the mirrored columns to the existing profiles table.
alter table public.ff_profiles add column if not exists feathers    bigint not null default 0;
alter table public.ff_profiles add column if not exists flock_name  text;
alter table public.ff_profiles add column if not exists title       text;

-- 2) Global Feathers leaderboard (top 100). SECURITY DEFINER so anon can read the board
--    without exposing the whole profiles table via RLS.
create or replace function public.ff_feathers_board()
returns table(rank bigint, user_id uuid, handle text, feathers bigint)
language sql
security definer
set search_path = public
as $$
  select row_number() over (order by feathers desc, id) as rank,
         id as user_id,
         coalesce(nullif(handle, ''), 'Unnamed wing') as handle,
         feathers
  from public.ff_profiles
  where feathers > 0
  order by feathers desc, id
  limit 100;
$$;

grant execute on function public.ff_feathers_board() to anon, authenticated;

-- Note: the app upserts its own ff_profiles row (id = auth.uid()) with the feathers/flock_name/
-- title fields, so the existing INSERT/UPDATE RLS policy on ff_profiles already covers the write.
