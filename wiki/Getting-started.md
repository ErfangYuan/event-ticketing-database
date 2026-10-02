# Local setup

Install JDK 17+ and MySQL 8. The checked-in Maven Wrapper downloads checksum-pinned Maven 3.10.0; a global Maven installation is unnecessary. Set JAVA_HOME to the JDK directory containing bin/java and bin/javac (or their .exe versions on Windows), or put the JDK on PATH with JAVA_HOME unset. The first build requires internet access. Build without a database connection using `./mvnw.cmd -B -ntp clean compile dependency:copy-dependencies` on Windows or `sh ./mvnw -B -ntp clean compile dependency:copy-dependencies` on Linux/macOS.

Create a dedicated empty demonstration database and a user granted access only to it. Load sql/schema.sql and sql/load.sql after explicitly selecting that database. The optional drop.sql is destructive and is never part of an automatic start.

Set MYTIX_DB_HOST, MYTIX_DB_PORT, MYTIX_DB_NAME and MYTIX_DB_USER in your shell. The launchers prompt locally for MYTIX_DB_PASSWORD if absent. The .env.example file documents names; it is not automatically loaded.

Run ./run.ps1 on Windows or bash run.sh on Linux/macOS. Maven retrieves JDBC/OpenNLP dependencies. For R9, run `java scripts/InstallNlpModels.java` from the repository root with JDK 17+ to install four checksum-verified English models. Model sources and custom directory configuration are documented in docs/opennlp-models.md; other views can run without the models. Use synthetic seed data only.
