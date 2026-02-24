# Local Port Registry

Use a deterministic port range so multiple apps can run at the same time without collisions.

## Pattern

- Reserve `18xxx` for local development apps.
- Assign each app a 2-digit app ID: `01`, `02`, `03`, ...
- Use:
    - `18<ID>0` = primary web/frontend port
    - `18<ID>1` = primary backend/API port
    - `18<ID>2` = worker/admin/debug port (optional)

Examples:
- App `01`: `18010`, `18011`, `18012`
- App `02`: `18020`, `18021`, `18022`

## This Repo

- App name: `stockdash`
- App ID: `09`
- Assigned ports:
    - `18090` (primary)
    - `18091` (reserved)
    - `18092` (reserved)

## Notes

- Keep this file updated when adding/changing local services.
- For OAuth callbacks, always keep redirect URI and app port in sync.