# Event Ticketing Database (MyTix)

![Real project demonstration](docs/media/schema.svg)

[Documentation](wiki/Home.md) · [GitHub Wiki](https://github.com/ErfangYuan/event-ticketing-database/wiki) · [Known issues and roadmap](https://github.com/ErfangYuan/event-ticketing-database/issues)


A Java terminal application for discovering events, checking inventory and following a ticket from purchase through ownership changes. The interesting part is keeping the data consistent when a seat is sold, a show is cancelled or a ticket is resold.

Designed and implemented by Erfang Yuan. This is a portfolio copy with fresh history; it does not include course reports, grades, real credentials or private database snapshots.

## What is here

- A normalized 22-table MySQL schema, freshly generated synthetic demonstration records and a Java/JDBC terminal interface.
- Customer and organizer workflows for bookings, inventory, cancellation, resale and reviews.
- Seven search views, including nearby events, filtered availability and consecutive seats within a budget.
- Nine reports covering revenue, organizer/customer activity, cancellations, sell-through, resale and review noun phrases.

The current schema includes explicit postal adjacency and review noun-phrase occurrences. [Domain contract](docs/domain-contract.md) · [Fresh demonstration data](docs/demo-data.md).

## Run locally

Install JDK 17+ and MySQL 8 (or a compatible server). A separate Maven installation is unnecessary: the checked-in [Apache Maven Wrapper](https://maven.apache.org/tools/wrapper/) downloads Maven 3.10.0 and checks its pinned SHA-256 checksum. The first build needs internet access. Set `JAVA_HOME` to the JDK directory that contains `bin/java` and `bin/javac` (on Windows, `java.exe` and `javac.exe`), not a parent such as `C:\Program Files\Java`. Ensure Java is on `PATH` when `JAVA_HOME` is unset.

Verify the build without connecting to MySQL:

```powershell
# Windows; replace the path with your installed JDK.
$env:JAVA_HOME='C:\Program Files\Java\jdk-24'
.\mvnw.cmd -B -ntp clean compile dependency:copy-dependencies
```

```bash
# Linux/macOS; JAVA_HOME may be omitted when the JDK is on PATH.
sh ./mvnw -B -ntp clean compile dependency:copy-dependencies
```

Create a **new** database such as mytix_demo and a dedicated local database user; grant it access only to that database. Explicitly select that database when running `sql/schema.sql` and then `sql/load.sql` using the MySQL batch client without `--force`. The loader replaces all project data. The optional `drop.sql` removes the project tables. See [load/clear commands, generated data and public synthetic logins](docs/demo-data.md).

Configure MYTIX_DB_HOST, MYTIX_DB_PORT, MYTIX_DB_NAME and MYTIX_DB_USER in your shell. For example, PowerShell uses `$env:MYTIX_DB_NAME='mytix_demo'`; Bash uses `export MYTIX_DB_NAME=mytix_demo`. The application reads process environment variables, **not .env files automatically**. .env.example is a list of names, not a loader.

Run `./run.ps1` on Windows or `bash run.sh` on Linux/macOS. The launcher builds through the wrapper, then asks for the MySQL password using masked local input if it is not already set. Passwords are not command-line arguments. Maven downloads JDBC 8.0.29 and OpenNLP 1.9.4 and compiles the program for Java 17. Configure your own database; an empty password is not a deployment assumption.

Creating reviews or loading their noun-phrase projections needs four compatible English OpenNLP models. From the repository root, run `java scripts/InstallNlpModels.java` with JDK 17+ to download the pinned models into ignored `src/lib/opennlp/` and verify their SHA-256 checksums. Repeat the command to verify or repair the installation. See [model setup, sources, and licenses](docs/opennlp-models.md) for custom directories and offline preparation. Review text and extracted phrase occurrences are saved in one transaction; missing models abort that write. R9 reads the stored occurrences and performs frequency aggregation and top-ten ranking in SQL, so report reads need no model files and do not change data.

See [account security and input behavior](docs/account-security.md) for salted password storage, legacy-account migration, password whitespace, interrupted commands and account deletion.

## A short tour

1. Run the demo loader and use its `futureConsecutiveAndGA` performance ID in Queries → Q6 to compare available, sold and blocked inventory.
2. Try Q7 for that performance with quantity 2 and a budget to find consecutive reserved seats.
3. Q1 near latitude 43.6426 / longitude -79.3871 finds demonstration venues around Toronto.
4. Reports → R1, city mode, over the preceding year compares revenue. Dates are generated relative to the UTC loading date.

Sample names, cards and event records are synthetic. No payment processor is connected; this is a database/transaction study, not a production payment application. See [local verification and follow-up work](docs/verification.md).

## Earlier interface illustrations

![Seat availability](docs/media/q6.png)

![Consecutive seats](docs/media/q7.png)

These illustrations preserve output from the earlier dataset and are not acceptance evidence for the current model. Current validation is described in [verification](docs/verification.md).
