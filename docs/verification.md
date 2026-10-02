# Verification, 2026-10-02

The current database/TUI audit was rebuilt from the original requirements and public terminal entrypoints. Previous seeds, screenshots and results were not acceptance oracles. Newly written tests, private requirements, credentials and detailed transcripts remain in ignored `local/` and are not distributed. These are observed local results, not a claim of automated CI coverage.

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

## Remaining delivery gate

The Java API and Next.js GUI, their integration/security/browser acceptance, final run instructions, publication and Issue closure are the next phase. Database-stage completion does not imply those gates are complete.
