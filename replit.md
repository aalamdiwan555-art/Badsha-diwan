# Badsha Diwan

Badsha Diwan contains the Autopilot Android client and its account, scenario, and administrator API.

## Run & Operate

- `pnpm --filter @workspace/api-server run dev` — run the API server (port 5000)
- `pnpm run typecheck` — full typecheck across all packages
- `pnpm run build` — typecheck + build all packages
- `pnpm --filter @workspace/api-spec run codegen` — regenerate API hooks and Zod schemas from the OpenAPI spec
- `pnpm --filter @workspace/db run push` — push DB schema changes (dev only)
- Required env: `DATABASE_URL` — Postgres connection string

## Stack

- pnpm workspaces, Node.js 24, TypeScript 5.9
- API: Express 5
- DB: PostgreSQL + Drizzle ORM
- Validation: Zod (`zod/v4`), `drizzle-zod`
- API codegen: Orval (from OpenAPI spec)
- Build: esbuild (CJS bundle)

## Where things live

- `android-app/` — Android client source and flavor-specific resources.
- `lib/api-spec/openapi.yaml` — source of truth for API contracts.
- `lib/db/src/schema/index.ts` — source of truth for PostgreSQL tables and types.
- `artifacts/api-server/` — Express API routes and authentication middleware.
- `artifacts/mockup-sandbox/` — component preview server.

## Architecture decisions

- API response and database identifiers use UUIDs consistently.
- Generated API clients are regenerated from the OpenAPI document; do not edit generated files manually.
- Android release signing and Firebase configuration are supplied by the build environment, never committed.

## Product

The project supports account sign-up/sign-in, published automation modes, user profiles, administrator scenario management, user access controls, and subscription/ad-free management.

## User preferences

No additional preferences recorded.

## Gotchas

- Run `pnpm --filter @workspace/api-spec run codegen` after changing `lib/api-spec/openapi.yaml`.
- Provide real `DATABASE_URL`, Supabase JWT, Firebase, and release signing configuration only through secure build/deployment configuration.

## Pointers

- See the `pnpm-workspace` skill for workspace structure, TypeScript setup, and package details
