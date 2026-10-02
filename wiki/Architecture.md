# Architecture

Twenty-two MySQL tables separate users, venues, performances, inventory, prices, orders, ownership, explicit postal adjacency and review phrase occurrences. Java services use JDBC and prepared statements. Both the original terminal menus and the project-specific HTTP API call these services; Next.js supplies the browser workspace and HttpOnly session boundary.

Search offers seven views; reporting offers nine with all original modes. OpenNLP extracts phrase occurrences when reviews are saved; R9 aggregates and ranks them in SQL. The Web schema graph reads all 22 entities and 39 foreign keys from actual metadata. See the [Web/API guide](../docs/web-api.md), [domain contract](../docs/domain-contract.md) and [verification](../docs/verification.md). Earlier SVG diagrams describe the historical model.
