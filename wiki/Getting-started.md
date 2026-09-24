# Local setup

Install JDK 17+, Maven and MySQL 8. Create a dedicated empty demonstration database and a user granted access only to it. Load sql/schema.sql and sql/load.sql after explicitly selecting that database. The optional drop.sql is destructive and is never part of an automatic start.

Set MYTIX_DB_HOST, MYTIX_DB_PORT, MYTIX_DB_NAME and MYTIX_DB_USER in your shell. The launchers prompt locally for MYTIX_DB_PASSWORD if absent. The .env.example file documents names; it is not automatically loaded.

Run ./run.ps1 on Windows or bash run.sh on Linux/macOS. Maven retrieves JDBC/OpenNLP dependencies. The R9 report additionally requires the four compatible English OpenNLP models documented in README; other views can run without the models. Use synthetic seed data only.
