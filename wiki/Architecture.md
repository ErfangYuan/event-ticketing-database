# Architecture

Twenty MySQL tables separate users, venues, performances, inventory, prices, orders and ownership history. Java services use JDBC and prepared statements; the terminal menu is a thin interactive entry point to queries and transactions.

Search offers seven views; reporting offers nine. Reports combine the same transactional data for organizer and customer questions. R9 extracts noun phrases using OpenNLP; it is not a semantic topic-clustering model. See the schema diagram in docs/schema.svg and the review notes in docs/verification.md.
