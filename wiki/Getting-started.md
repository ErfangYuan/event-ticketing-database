# Local setup

The project now includes a Next.js workspace backed by the Java API. Follow the current [README startup sequence](../README.md#start-locally) for JDK 17+, Node.js 22+, MySQL schema installation, model setup and `run-web.ps1` / `run-web.sh`. [API configuration and access boundaries](../docs/web-api.md).

Install JDK 17+ and MySQL 8. The checked-in Maven Wrapper downloads checksum-pinned Maven 3.10.0; a global Maven installation is unnecessary. Set JAVA_HOME to the JDK directory containing bin/java and bin/javac (or their .exe versions on Windows), or put the JDK on PATH with JAVA_HOME unset. The first build requires internet access. Build without a database connection using `./mvnw.cmd -B -ntp clean compile dependency:copy-dependencies` on Windows or `sh ./mvnw -B -ntp clean compile dependency:copy-dependencies` on Linux/macOS.

Create a dedicated empty demonstration database and a user granted access only to it. Install the current 22-table `sql/schema.sql`, then use Web demo generation or load `sql/load.sql` with the MySQL batch client without `--force`. Explicitly select the project database. The optional drop.sql is destructive and is never part of an automatic start.

Set MYTIX_DB_HOST, MYTIX_DB_PORT, MYTIX_DB_NAME and MYTIX_DB_USER in your shell. The launchers prompt locally for MYTIX_DB_PASSWORD if absent. The .env.example file documents names; it is not automatically loaded.

For the terminal interface, run ./run.ps1 on Windows or bash run.sh on Linux/macOS. Creating reviews and generating the demo need the four checksum-verified English models installed by `java scripts/InstallNlpModels.java`. R9 reads saved phrase occurrences and needs no models at report time. Use synthetic data only.
