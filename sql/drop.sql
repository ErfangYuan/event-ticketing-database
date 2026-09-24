-- MyTix drop.sql
-- Drops the complete schema in dependency-safe order.

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS reviews;
DROP TABLE IF EXISTS resale_listings;
DROP TABLE IF EXISTS ticket_ownership;
DROP TABLE IF EXISTS tickets;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS blocked_seats;
DROP TABLE IF EXISTS performance_section_tiers;
DROP TABLE IF EXISTS price_tiers;
DROP TABLE IF EXISTS performances;
DROP TABLE IF EXISTS event_artists;
DROP TABLE IF EXISTS events;
DROP TABLE IF EXISTS seats;
DROP TABLE IF EXISTS sections;
DROP TABLE IF EXISTS artists;
DROP TABLE IF EXISTS venues;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS genres;
DROP TABLE IF EXISTS segments;
DROP TABLE IF EXISTS postal_areas;
DROP TABLE IF EXISTS payment_cards;

SET FOREIGN_KEY_CHECKS = 1;
