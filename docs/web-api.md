# MyTix Web workspace and API

The browser application in `web/` uses Next.js App Router and React. Its same-origin route handler forwards project-specific JSON requests to a Java 17 HTTP service on loopback. The Java service reuses the verified JDBC query, report, booking, account and organizer services. It does not invoke the TUI or accept arbitrary SQL.

## Configuration

| Variable | Purpose |
| --- | --- |
| `MYTIX_DB_HOST`, `MYTIX_DB_PORT`, `MYTIX_DB_NAME`, `MYTIX_DB_USER`, `MYTIX_DB_PASSWORD` | Selected MySQL connection; the Web launcher requires the database name and username explicitly |
| `MYTIX_NLP_MODELS_DIR` | Optional directory with the four pinned OpenNLP models |
| `MYTIX_API_SECRET` | Random server-to-server key, at least 32 characters; launchers generate one if absent |
| `MYTIX_API_PORT` | Java loopback port, default 8081 |
| `MYTIX_WEB_PORT` | Next.js loopback port, default 3000 |
| `MYTIX_API_URL` | Next.js-only backend address; launcher derives it from the API port |
| `MYTIX_WEB_ORIGIN` | Exact allowed browser origin, normally `http://127.0.0.1:3000` |
| `MYTIX_DEMO_MODE` | `true` permits deliberately public load/clear in this isolated demo; `false` disables both |

Use one Web instance per project database. Sessions are in memory and expire after six hours or an API restart. The launchers bind both services to `127.0.0.1`; do not expose this demonstration configuration as a real payment service. A different reverse-proxy deployment would need its own TLS, capacity and operational review. HTTPS origins receive Secure cookies automatically.

Windows `run-web.ps1 -Check` starts both services, verifies the browser-facing API against all 22 tables, then stops its own child processes. Add `-SkipBuild` only when both artifacts are already built. Logs go to ignored `local/web-runtime/`. The Bash launcher has the same production startup sequence; see verification for tested platforms.

## Session and data boundaries

- Passwords use independently salted PBKDF2. Web registration requires at least ten characters and creates an isolated fake payment alias; no real card number is requested.
- Browser sessions use HttpOnly, SameSite=Strict cookies. Login tokens are removed from response JSON, never stored in localStorage, and rotated on each login.
- The Next.js endpoint requires the configured Origin and JSON content type for every request, including login and maintenance. It bounds request bodies at 64 KiB and forwards only the permitted path shape.
- Java requires the private service key and derives the actor from the server session on every call. Request-body customer/organizer IDs never grant authority. Deleted accounts lose access; role and ownership rules remain in the domain services.
- Full schema metadata is visible, but account/order/ticket/acquisition/listing history rows are scoped to the signed-in account. Payment identifiers are masked and password hashes are never selected into API results. Shared catalog data, public review text and aggregate reports are available to visitors. These are fictional demonstration accounts.
- SQL values use prepared statements. Table and column identifiers come from an exact 22-table allowlist and inspected metadata. Browse filters accept indexed visible columns only; keyset pagination follows all primary-key components, with a maximum page size of 100.
- Browser text uses React's escaped rendering. CSV export prefixes potentially executable spreadsheet formulas while preserving their text. External assets and frames are blocked by the content security policy.
- Authentication attempts and session storage are bounded. A fair application-wide gate serializes writes and prevents reads observing a partial maintenance replacement. Stop TUI/SQL/external writers during maintenance; they are outside this HTTP gate.
- Demo load and clear retain all foreign keys, run in one transaction, and invalidate every existing session after success. Model preparation happens before deletion. Missing models or SQL failure leave the previous data intact.

## Project endpoints

All routes use `POST /api/<path>` with a JSON object. Browser clients use only the same-origin Next.js route. Direct Java callers additionally require `X-Mytix-Key`; authenticated direct clients supply `X-Mytix-Session`. Never put either key in a public URL or client bundle.

| Path family | Behavior |
| --- | --- |
| `status`, `schema` | Counts, UTC date, complete entity/column/foreign-key metadata |
| `table` | `{table, limit, cursor, filterColumn, filterValue}`; returns columns, rows, permitted total and next cursor |
| `auth/login`, `auth/register`, `auth/me`, `auth/logout`, `auth/delete` | Local account/session lifecycle |
| `query/q1` … `query/q7` | Typed query parameters from the corresponding UI forms |
| `report/r1` … `report/r9` | `{params: [...]}` matching the original report modes |
| `catalog/*` | Upcoming events/performances, seats, personal tickets/history, resale offers, review eligibility and organizer views |
| `customer/book`, `cancel`, `list`, `withdraw`, `buy`, `review` | Existing booking, resale, refund and review transactions |
| `organizer/event`, `performance`, `price`, `block`, `unblock`, `cancel`, `suggest`, `estimate` | Existing organizer workflows and pricing toolkit |
| `reference/create`, `reference/delete` | Organizer-only creation/deletion of reference entities; owned events can be removed only before any performance exists |
| `demo/load`, `demo/clear` | Explicit whole-project demo maintenance when enabled |

Queries/reports return complete result arrays; their table widget paginates the loaded result and exports those rows. Entity browsing fetches one indexed database page at a time; its local text filter and CSV export apply to that loaded page. Row details navigate through real foreign keys. Password/card columns cannot be used as browse filters or relationship links.

Physical venue sections/seats can be edited only before a venue has performances. Foreign-key constraints prevent deleting used reference data. Historical transaction entities are changed through dedicated workflows, rather than arbitrary row editing, so acquisitions and refunds remain auditable.

## Dependencies and verification

Next.js 16.3.8, React 19.3.0, React Flow 12.12.0 and Lucide React 1.50.0 are locked by `web/package-lock.json`; the application uses Node 22+ (tested with 24.15.0). See [official Next.js setup](https://nextjs.org/docs/app/getting-started/installation) and [React Flow documentation](https://reactflow.dev/learn). Java uses the JDK HTTP server and Jackson 2.22.3 for bounded JSON decoding, alongside the existing JDBC/OpenNLP dependencies.

Run `npm ci`, `npm run typecheck`, `npm run build`, and `npm audit` inside `web/`. New private audit suites and browser evidence live only in ignored `local/`; the public repository does not pretend those files are shipped CI tests. Observed results and limitations are recorded in [verification](verification.md).
