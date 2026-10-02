# Event Ticketing Database (MyTix)

[Web/API guide](docs/web-api.md) · [Domain contract](docs/domain-contract.md) · [Verification](docs/verification.md) · [Issues](https://github.com/ErfangYuan/event-ticketing-database/issues)

A Java/MySQL ticketing application with a Next.js workspace and the original terminal interface. Discover shows, book and resell tickets, manage performances, explore every database relationship, and inspect the reports behind the activity.

Designed and implemented by Erfang Yuan. This portfolio repository contains synthetic demonstration records, not course reports, grades, credentials or private database snapshots.

## Explore the workspace

- An interactive graph of **22 tables and 39 foreign-key relationships**, with entity details and navigation to related records.
- Complete tables, indexed cursor pagination, account-scoped personal data, CSV export and interactive charts.
- All **7 searches and 9 reports**, including every geographic, ranking and aggregation mode.
- Customer booking, cancellation, resale and reviews; organizer event creation, performance scheduling, tier pricing, seat holds and cancellation.
- Local customer/organizer accounts, salted passwords, simulated payments, reference-data creation/deletion and one-click demo generation or clear-all.

## Start locally

Install **JDK 17+**, **Node.js 22+ with npm**, and **MySQL 8+**. The lockfile pins Next.js 16.3.8 and React 19.3.0. The checked-in [Maven Wrapper](https://maven.apache.org/tools/wrapper/) downloads checksum-pinned Maven 3.10.0; no global Maven installation is needed. Initial dependency/model downloads require internet access.

1. Create a dedicated empty database and local user, granting access only to that database. Install `sql/schema.sql` in the explicitly selected database. An older 20-table installation needs a fresh schema; startup does not migrate or clear it.
2. Configure the database environment variables below. The launchers prompt for the MySQL password with masked local input. They do not automatically read `.env` files.
3. From the repository root, run `java scripts/InstallNlpModels.java` to install the four pinned English models needed for new reviews and demo generation. Custom model directories and offline setup are described in [model setup](docs/opennlp-models.md).
4. Run the launcher for your platform. Open **http://127.0.0.1:3000** and select **Demo controls → Generate fresh demo**.

```powershell
# Windows PowerShell, from the repository root. Use your installed JDK.
$env:JAVA_HOME='C:\Program Files\Java\jdk-24'
$env:MYTIX_DB_HOST='127.0.0.1'
$env:MYTIX_DB_PORT='3306'
$env:MYTIX_DB_NAME='mytix_demo'
$env:MYTIX_DB_USER='mytix_app'
.\run-web.ps1
```

```bash
# Linux/macOS, from the repository root
export MYTIX_DB_HOST=127.0.0.1 MYTIX_DB_PORT=3306
export MYTIX_DB_NAME=mytix_demo MYTIX_DB_USER=mytix_app
bash run-web.sh
```

For example, after creating `mytix_demo`, a Windows PowerShell session can install its schema through the MySQL batch client:

```powershell
cmd /c 'mysql -h 127.0.0.1 -u mytix_app -p mytix_demo < sql/schema.sql'
```

On Bash, use the same `mysql` command with ordinary input redirection. The client prompts for the database password. The optional `sql/load.sql` can be loaded the same way **without `--force`**; it replaces all project records with the same new demonstration model. See [dataset and maintenance details](docs/demo-data.md).

The launchers compile Java, install locked npm dependencies, build Next.js, generate a private API key when absent, and start both services on loopback. **Ctrl+C stops the services.** Windows development mode is `./run-web.ps1 -Dev`; `-SkipBuild` reuses an existing build, and `-Check` verifies startup then stops both services. Both interfaces use the same database: stop other writers during maintenance.

The API/Web ports default to 8081/3000. Override `MYTIX_API_PORT`, `MYTIX_WEB_PORT` and `MYTIX_WEB_ORIGIN` when needed. The Node server receives the project API key, not the database environment variables. [Configuration and security](docs/web-api.md).

The isolated local demo enables load/clear controls for visitors. Set `MYTIX_DEMO_MODE=false` to disable them. Both actions replace or delete **all project data** and invalidate every session. No payment processor or external identity provider is connected.

| Demo account | Email | Password |
| --- | --- | --- |
| Customer | `customer003@demo.mytix.test` | `MyTixDemo!42` |
| Organizer | `organizer01@demo.mytix.test` | `MyTixDemo!42` |

The fresh dataset contains 24 events, 72 performances, 645 orders and 1,903 tickets across Toronto, Vancouver, New York and Seattle. All accounts and transactions are synthetic; dates are relative to the UTC loading day. Do not use real account or payment information.

## Build or use the terminal

Set `JAVA_HOME` to the actual JDK directory containing `bin/java` and `bin/javac` (or their Windows `.exe` versions). Alternatively, put the JDK on `PATH` with `JAVA_HOME` unset.

```powershell
.\mvnw.cmd -B -ntp compile dependency:copy-dependencies
.\run.ps1
```

```bash
sh ./mvnw -B -ntp compile dependency:copy-dependencies
bash run.sh
```

Build commands need no database connection. The TUI launcher uses the same `MYTIX_DB_*` configuration and prompts for the password if absent. Java dependencies are Connector/J 8.0.29, OpenNLP 1.9.4 and Jackson 2.22.3; compilation targets Java 17. SQL R9 reads persisted noun-phrase occurrences and needs no model files at report time.

## Try a few paths

- Queries → Nearby events: latitude 43.6426, longitude -79.3871, radius 15 km.
- Events & tickets → Available seats / Section inventory: performance 5 in the fresh demo. Select actual seat or section IDs before booking.
- Reports → Sales revenue: compare cities over the preceding year. Switch the chart metric or inspect every result row.
- Schema explorer: click any entity or relationship to browse its records; use keyboard selection to inspect a node's columns.
- Data browser: sign in as an organizer to add/remove unreferenced catalog data. Transaction history changes through the dedicated booking/cancellation workflows.

[Verification results and platform limits](docs/verification.md) · [Account lifecycle](docs/account-security.md) · [Pricing toolkit](docs/pricing-toolkit.md)
