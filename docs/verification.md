# Local verification and follow-up work

2026-09-24: original Java sources compiled with JDBC and OpenNLP dependencies. The original schema and synthetic seed loaded into a new isolated database (20 tables), with existing databases untouched. Q1–Q7 and R1–R9 all executed through the real TUI without runtime exceptions. Five exact result tables were typeset for portfolio illustrations.

This is execution coverage, not proof of complete business correctness. Follow-up issues to validate before a production release:

- Add explicit date-boundary and empty-result assertions to all searches and reports; seed-relative dates differ from the current system date.
- Review Q5's missing-price behavior when applying price constraints.
- Review R4's time-window and purchase/resale interpretation against its intended definition.
- Review whether R7 month/city mode should include every performance or only sold-out/low-sales categories.
- Verify concurrent booking, cancellation and resale invariants using isolated transactional fixtures.
- Replace demonstration password handling and payment-card records before any real service deployment.

These are repository follow-up notes, not visitor-facing portfolio copy. The Maven distribution launcher still needs a clean-machine dependency/model download check, tracked in GitHub issue #4; the original dependency set was used for the recorded runs. This source publication does not claim that check passed.
