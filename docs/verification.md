# Verification, 2026-10-02

The database/TUI audit was rebuilt from the original requirements and public terminal entrypoints, then extended through the Java API and Next.js browser application. Previous seeds, screenshots and results were not acceptance oracles. Newly written tests, private requirements, credentials and detailed transcripts remain in ignored `local/` and are not distributed. These are observed local results, not a claim of automated CI coverage.

## Database and terminal phase

The authorized local MySQL target was cleared before baseline testing. Small independently specified fixtures exposed query, reporting, account, inventory and history defects. Fixes were checked against exact result values and complete before/after logical database snapshots; temporary scenarios restored the fixture. Separate suites include the following (coverage overlaps; counts should not be added as distinct requirements):

| Area | Verified scope |
| --- | --- |
| Accounts | 80 offline assertions; 25 live regressions and 34 lifecycle cases |
| Geography | 29 postal/adjacency and geographic-intersection cases |
| Queries/reports | 133 suite entries, 137 event/case assertions; same-name identities, acquisition history, long noun phrases and cap snapshots |
| Transactions | 38 distinct scenarios; 24 independent-JVM races; seats, GA, listings, rollback, cancellation and round-trip ownership |
| Organizer toolkit | 26 offline mathematical checks and 12 live cases |
| Performance creation | 8 service/TUI cases including one tier, missing/foreign sections, wrong owner and role |
| UTC handling | 16 JDBC decoding assertions across four JVM zones; both sides of cancellation cutoff across Sydney DST |
| Empty database | 34 current query/report modes after clearing all 22 tables |
| Demo maintenance | 10 checks: target/model rejection, load, injected failure with exact rollback, clear, empty reads, reload and independent dataset acceptance |
| SQL loader | Real MySQL batch execution, injected failure with exact rollback, successful reload and independent dataset acceptance |
| Fresh demo TUI | All 31 query/report modes return their expected nonempty columns; complete database state unchanged |

All cases in these suites passed after fixes. Failed baseline evidence is retained separately. A demo smoke parser initially confused a clipped `venueID` header with the full `venueName` header; the parser now prefers an exact match, with the original failure retained.

The fresh demo contains 22 tables, 8 venues in 4 cities/2 countries, 24 events, 72 performances, 5 organizers/100 customers, 645 orders, 1,903 tickets, 1,906 acquisitions, 20 resale listings and 65 reviews across 24 events. Independent SQL assertions check the original minima, foreign keys, inventory, account ages, distinct password salts, complete tier mappings, chronology, refunds, resale history, scalper examples and stored NLP projection. All demonstration people and transactions are synthetic.

## Build and dependencies

Verified locally on Windows with JDK 24 compiling for Java 17, MySQL 26.7.0, Maven Wrapper 3.10.0, Connector/J 8.0.29 and OpenNLP 1.9.4. Fresh wrapper/model downloads, pinned checksums, model repair and failure preservation were exercised. Current Maven compile and dependency-copy passes. R9 reads the stored projection without model files; review creation and demo generation require the four pinned models.

The schema targets MySQL 8 features; this run is not a claim of execution on every MySQL 8 patch or operating system. SQL loading must use batch mode without `--force`; stop other writers for CLI/SQL maintenance. No payment processor or external identity provider is connected.

## Java API and Next.js phase

| Area | Observed result |
| --- | --- |
| API unit boundaries | 26 assertions passed for typed input and bounded JSON handling |
| HTTP integration | 360 assertions passed: all 22 table scopes and cursor pages, complete metadata, query/report modes, catalog shapes, service-key rejection, invalid inputs, actor/role isolation and reference/event mutations |
| Browser-facing security | 12 checks passed: exact Origin, JSON-only requests, HttpOnly/Strict cookies, login token removal, logout revocation and no service key in browser assets |
| HTTP business workflows | 38 assertions passed: organizer/customer creation, one-tier performances, price/seat holds, reserved and GA booking, two-account resale, correct acquisition refund, immutable primary sale, organizer cancellation and real NLP review persistence |
| Build/startup | Maven compile/dependency copy, locked npm install, strict TypeScript and production Next.js build passed; public Windows launcher started both services, checked all 22 tables through the browser endpoint, then cleaned up both child processes |
| Dependencies | `npm audit` reported zero vulnerabilities in the installed lockfile on the verification date |

Browser acceptance used the Chrome extension tools against the production build. It covered customer sign-in, booking/listing/withdrawal/cancellation, organizer reference creation/deletion, indexed browsing, all seven query and nine report forms, schema navigation, escaped review text and CSV formula protection. Database snapshots independently confirmed the expected ticket/refund history and no unrelated writes. Reference CRUD restored all 22 logical tables exactly. Failed test-oracle attempts and their corrections remain in the private evidence ledger.

The complete schema graph renders 22 entities and 39 foreign keys. A single click on an entity or relationship opens its table; 72 performances browse in indexed pages with the expected second-page IDs. At a 390-pixel mobile viewport the page fits without document overflow, while wide tables scroll inside their own container. Modal reverse-tab focus stays inside the dialog. No browser errors/warnings were observed in the final graph navigation check.

The browser clear-all action emptied every table; each query/report form then displayed an explicit empty result without an error. The browser regenerated the fresh demonstration dataset, which passed 64 independent requirement/invariant checks. Four pre-reset session tokens remained invalid after clearing and after reloading reused account IDs. Temporary workflow records were removed by this final reset; the clean demonstration dataset remains loaded.

After contrast corrections, Lighthouse snapshot audits of the desktop and mobile overview each scored 100 for accessibility, best practices, SEO and agentic browsing (29 passed audits, zero failures). These are observations of those overview states, not a performance benchmark or an accessibility guarantee for every interaction. The schema graph was additionally checked visually and by entity/edge navigation.

The Web environment used Node 24.15.0, Next.js 16.3.8, React 19.3.0, React Flow 12.12.0 and Jackson 2.22.3. Windows startup was executed end to end, including fresh dependency installation. Bash launchers passed syntax checks; Linux/macOS runtime execution was not performed. Authentication and payment behavior are local simulations. Concurrent application instances and external SQL/TUI writers are outside the single Java API maintenance gate.
