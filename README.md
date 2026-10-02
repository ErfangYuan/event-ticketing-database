# Event Ticketing Database (MyTix)

![Real project demonstration](docs/media/schema.svg)

[Documentation](wiki/Home.md) · [GitHub Wiki](https://github.com/ErfangYuan/event-ticketing-database/wiki) · [Known issues and roadmap](https://github.com/ErfangYuan/event-ticketing-database/issues)


A Java terminal application for discovering events, checking inventory and following a ticket from purchase through ownership changes. The interesting part is keeping the data consistent when a seat is sold, a show is cancelled or a ticket is resold.

Designed and implemented by Erfang Yuan. This is a portfolio copy with fresh history; it does not include course reports, grades, real credentials or private database snapshots.

## What is here

- A normalized 20-table MySQL schema, synthetic demonstration records and a Java/JDBC terminal interface.
- Customer and organizer workflows for bookings, inventory, cancellation, resale and reviews.
- Seven search views, including nearby events, filtered availability and consecutive seats within a budget.
- Nine reports covering revenue, organizer/customer activity, cancellations, sell-through, resale and review noun phrases.

![All twenty tables and their relationships](docs/schema.svg)

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

Create a **new** database such as mytix_demo and a dedicated local database user; grant it access only to that database. In the MySQL client, explicitly select that new database before loading sql/schema.sql and then sql/load.sql. Never load these scripts into a database you want to preserve. The optional drop.sql is destructive.

Configure MYTIX_DB_HOST, MYTIX_DB_PORT, MYTIX_DB_NAME and MYTIX_DB_USER in your shell. For example, PowerShell uses `$env:MYTIX_DB_NAME='mytix_demo'`; Bash uses `export MYTIX_DB_NAME=mytix_demo`. The application reads process environment variables, **not .env files automatically**. .env.example is a list of names, not a loader.

Run `./run.ps1` on Windows or `bash run.sh` on Linux/macOS. The launcher builds through the wrapper, then asks for the MySQL password using masked local input if it is not already set. Passwords are not command-line arguments. Maven downloads JDBC 8.0.29 and OpenNLP 1.9.4 and compiles the program for Java 17. Configure your own database; an empty password is not a deployment assumption.

R9 additionally needs four compatible English OpenNLP models. From the repository root, run `java scripts/InstallNlpModels.java` with JDK 17+ to download the pinned models into ignored `src/lib/opennlp/` and verify their SHA-256 checksums. Repeat the command to verify or repair the installation. See [model setup, sources, and licenses](docs/opennlp-models.md) for custom directories and offline preparation. Other searches and reports can run independently of the models.

## A short tour

1. Start with Queries → Q6 and performance 1 to compare available, sold and blocked seats.
2. Try Q7, performance 1, quantity 2 and budget 500. The seed finds Balcony row A, seats 5–6, for 180.00.
3. Q1 near latitude 43.6426 / longitude -79.3871 finds demonstration venues around Toronto.
4. Reports → R1, city mode, dates 2026-01-01 through 2026-12-31 compares revenue. Date-dependent results naturally change with the system clock.

Sample names, cards and event records are synthetic. No payment processor is connected; this is a database/transaction study, not a production payment application. See [local verification and follow-up work](docs/verification.md).

## Real query results

![Seat availability](docs/media/q6.png)

![Consecutive seats](docs/media/q7.png)

The terminal tables preserve actual output from an isolated synthetic database. Styling only changes the terminal presentation.
