-- MyTix schema.sql
-- Creates the complete schema (MySQL 8). Run on a clean database (or run drop.sql first).
-- JDBC note: default connection is root with empty password; document alternatives in the manual.

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE payment_cards (
  cardNumber  VARCHAR(32) NOT NULL,
  cardExpiry  CHAR(7) NOT NULL COMMENT 'YYYY-MM',
  PRIMARY KEY (cardNumber)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE postal_areas (
  postalCode  VARCHAR(16) NOT NULL,
  city        VARCHAR(64) NOT NULL,
  country     VARCHAR(64) NOT NULL,
  PRIMARY KEY (postalCode),
  CONSTRAINT chk_postal_country
    CHECK (country IN ('Canada', 'United States'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE segments (
  segmentID   INT AUTO_INCREMENT PRIMARY KEY,
  segmentName VARCHAR(64) NOT NULL,
  UNIQUE KEY uk_segments_name (segmentName)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE genres (
  genreID     INT AUTO_INCREMENT PRIMARY KEY,
  segmentID   INT NOT NULL,
  genreName   VARCHAR(64) NOT NULL,
  UNIQUE KEY uk_genres_segment_name (segmentID, genreName),
  CONSTRAINT fk_genres_segment
    FOREIGN KEY (segmentID) REFERENCES segments (segmentID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE users (
  userID            INT AUTO_INCREMENT PRIMARY KEY,
  email             VARCHAR(255) NOT NULL,
  passwordHash      VARCHAR(255) NOT NULL,
  name              VARCHAR(128) NOT NULL,
  address           VARCHAR(255) NOT NULL,
  birthday          DATE NOT NULL,
  userType          ENUM('CUSTOMER', 'ORGANIZER') NOT NULL,
  creditCardNumber  VARCHAR(32) NULL,
  createdAt         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_users_email (email),
  CONSTRAINT fk_users_card
    FOREIGN KEY (creditCardNumber) REFERENCES payment_cards (cardNumber),
  CONSTRAINT chk_users_customer_card
    CHECK (
      userType = 'ORGANIZER'
      OR creditCardNumber IS NOT NULL
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE venues (
  venueID     INT AUTO_INCREMENT PRIMARY KEY,
  venueName   VARCHAR(128) NOT NULL,
  latitude    DECIMAL(9,6) NOT NULL,
  longitude   DECIMAL(9,6) NOT NULL,
  address     VARCHAR(255) NOT NULL,
  postalCode  VARCHAR(16) NOT NULL,
  UNIQUE KEY uk_venues_lat_lng (latitude, longitude),
  CONSTRAINT fk_venues_postal
    FOREIGN KEY (postalCode) REFERENCES postal_areas (postalCode)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sections (
  sectionID             INT AUTO_INCREMENT PRIMARY KEY,
  venueID               INT NOT NULL,
  sectionName           VARCHAR(64) NOT NULL,
  isGeneralAdmission    TINYINT(1) NOT NULL DEFAULT 0,
  standingCapacity      INT NULL,
  UNIQUE KEY uk_sections_venue_name (venueID, sectionName),
  CONSTRAINT fk_sections_venue
    FOREIGN KEY (venueID) REFERENCES venues (venueID),
  CONSTRAINT chk_sections_ga_capacity
    CHECK (
      (isGeneralAdmission = 1 AND standingCapacity IS NOT NULL AND standingCapacity > 0)
      OR (isGeneralAdmission = 0 AND standingCapacity IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE seats (
  seatID      INT AUTO_INCREMENT PRIMARY KEY,
  sectionID   INT NOT NULL,
  rowName     VARCHAR(16) NOT NULL,
  seatNumber  INT NOT NULL,
  UNIQUE KEY uk_seats_section_row_num (sectionID, rowName, seatNumber),
  CONSTRAINT fk_seats_section
    FOREIGN KEY (sectionID) REFERENCES sections (sectionID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE artists (
  artistID    INT AUTO_INCREMENT PRIMARY KEY,
  artistName  VARCHAR(128) NOT NULL,
  biography   TEXT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE events (
  eventID         INT AUTO_INCREMENT PRIMARY KEY,
  organizerID     INT NOT NULL,
  genreID         INT NOT NULL,
  title           VARCHAR(255) NOT NULL,
  resaleCapRatio  DECIMAL(5,2) NOT NULL DEFAULT 2.00,
  CONSTRAINT fk_events_organizer
    FOREIGN KEY (organizerID) REFERENCES users (userID),
  CONSTRAINT fk_events_genre
    FOREIGN KEY (genreID) REFERENCES genres (genreID),
  CONSTRAINT chk_events_resale_cap
    CHECK (resaleCapRatio > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE event_artists (
  eventID       INT NOT NULL,
  artistID      INT NOT NULL,
  billingOrder  ENUM('HEADLINER', 'SPECIAL_GUEST', 'OPENING_ACT') NOT NULL,
  PRIMARY KEY (eventID, artistID),
  CONSTRAINT fk_event_artists_event
    FOREIGN KEY (eventID) REFERENCES events (eventID),
  CONSTRAINT fk_event_artists_artist
    FOREIGN KEY (artistID) REFERENCES artists (artistID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE performances (
  performanceID INT AUTO_INCREMENT PRIMARY KEY,
  eventID       INT NOT NULL,
  venueID       INT NOT NULL,
  date          DATE NOT NULL,
  startTime     TIME NOT NULL,
  endTime       TIME NOT NULL,
  status        ENUM('SCHEDULED', 'CANCELLED') NOT NULL DEFAULT 'SCHEDULED',
  cancelledAt   TIMESTAMP NULL,
  UNIQUE KEY uk_perf_venue_datetime (venueID, date, startTime),
  CONSTRAINT fk_perf_event
    FOREIGN KEY (eventID) REFERENCES events (eventID),
  CONSTRAINT fk_perf_venue
    FOREIGN KEY (venueID) REFERENCES venues (venueID),
  CONSTRAINT chk_perf_time
    CHECK (endTime > startTime),
  CONSTRAINT chk_perf_cancel
    CHECK (
      (status = 'SCHEDULED' AND cancelledAt IS NULL)
      OR (status = 'CANCELLED' AND cancelledAt IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE price_tiers (
  tierID         INT AUTO_INCREMENT PRIMARY KEY,
  performanceID  INT NOT NULL,
  tierName       VARCHAR(32) NOT NULL,
  price          DECIMAL(10,2) NOT NULL,
  UNIQUE KEY uk_tiers_perf_name (performanceID, tierName),
  CONSTRAINT fk_tiers_perf
    FOREIGN KEY (performanceID) REFERENCES performances (performanceID),
  CONSTRAINT chk_tiers_price
    CHECK (price >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE performance_section_tiers (
  performanceID  INT NOT NULL,
  sectionID      INT NOT NULL,
  tierID         INT NOT NULL,
  PRIMARY KEY (performanceID, sectionID),
  UNIQUE KEY uk_pst_section_tier (sectionID, tierID),
  CONSTRAINT fk_pst_perf
    FOREIGN KEY (performanceID) REFERENCES performances (performanceID),
  CONSTRAINT fk_pst_section
    FOREIGN KEY (sectionID) REFERENCES sections (sectionID),
  CONSTRAINT fk_pst_tier
    FOREIGN KEY (tierID) REFERENCES price_tiers (tierID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE blocked_seats (
  performanceID  INT NOT NULL,
  seatID         INT NOT NULL,
  reason         VARCHAR(255) NULL,
  PRIMARY KEY (performanceID, seatID),
  CONSTRAINT fk_blocked_perf
    FOREIGN KEY (performanceID) REFERENCES performances (performanceID),
  CONSTRAINT fk_blocked_seat
    FOREIGN KEY (seatID) REFERENCES seats (seatID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE orders (
  orderID          INT AUTO_INCREMENT PRIMARY KEY,
  customerID       INT NOT NULL,
  orderTimestamp   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  totalAmount      DECIMAL(12,2) NOT NULL,
  cardNumber       VARCHAR(32) NOT NULL,
  status           ENUM('COMPLETED', 'CANCELLED') NOT NULL DEFAULT 'COMPLETED',
  CONSTRAINT fk_orders_customer
    FOREIGN KEY (customerID) REFERENCES users (userID),
  CONSTRAINT fk_orders_card
    FOREIGN KEY (cardNumber) REFERENCES payment_cards (cardNumber),
  CONSTRAINT chk_orders_amount
    CHECK (totalAmount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE tickets (
  ticketID         INT AUTO_INCREMENT PRIMARY KEY,
  orderID          INT NOT NULL,
  performanceID    INT NOT NULL,
  sectionID        INT NOT NULL,
  seatID           INT NULL,
  currentOwnerID   INT NOT NULL,
  faceValue        DECIMAL(10,2) NOT NULL,
  status           ENUM('ACTIVE', 'CANCELLED', 'REFUNDED') NOT NULL DEFAULT 'ACTIVE',
  cancelledAt      TIMESTAMP NULL,
  activeSeatID     INT GENERATED ALWAYS AS (
                     CASE WHEN status = 'ACTIVE' THEN seatID ELSE NULL END
                   ) STORED,
  CONSTRAINT fk_tickets_order
    FOREIGN KEY (orderID) REFERENCES orders (orderID),
  CONSTRAINT fk_tickets_perf
    FOREIGN KEY (performanceID) REFERENCES performances (performanceID),
  CONSTRAINT fk_tickets_section
    FOREIGN KEY (sectionID) REFERENCES sections (sectionID),
  CONSTRAINT fk_tickets_seat
    FOREIGN KEY (seatID) REFERENCES seats (seatID),
  CONSTRAINT fk_tickets_owner
    FOREIGN KEY (currentOwnerID) REFERENCES users (userID),
  CONSTRAINT chk_tickets_face
    CHECK (faceValue >= 0),
  CONSTRAINT chk_tickets_cancel
    CHECK (
      (status = 'ACTIVE' AND cancelledAt IS NULL)
      OR (status IN ('CANCELLED', 'REFUNDED') AND cancelledAt IS NOT NULL)
    ),
  UNIQUE KEY uk_tickets_perf_active_seat (performanceID, activeSeatID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ticket_ownership (
  ownershipID  INT AUTO_INCREMENT PRIMARY KEY,
  ticketID     INT NOT NULL,
  ownerID      INT NOT NULL,
  acquiredAt   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  source       ENUM('PURCHASE', 'RESALE') NOT NULL,
  CONSTRAINT fk_own_ticket
    FOREIGN KEY (ticketID) REFERENCES tickets (ticketID),
  CONSTRAINT fk_own_owner
    FOREIGN KEY (ownerID) REFERENCES users (userID),
  KEY idx_own_ticket_time (ticketID, acquiredAt)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE resale_listings (
  listingID      INT AUTO_INCREMENT PRIMARY KEY,
  ticketID       INT NOT NULL,
  sellerID       INT NOT NULL,
  buyerID        INT NULL,
  listingPrice   DECIMAL(10,2) NOT NULL,
  status         ENUM('ACTIVE', 'SOLD', 'WITHDRAWN') NOT NULL DEFAULT 'ACTIVE',
  createdAt      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  soldAt         TIMESTAMP NULL,
  CONSTRAINT fk_listing_ticket
    FOREIGN KEY (ticketID) REFERENCES tickets (ticketID),
  CONSTRAINT fk_listing_seller
    FOREIGN KEY (sellerID) REFERENCES users (userID),
  CONSTRAINT fk_listing_buyer
    FOREIGN KEY (buyerID) REFERENCES users (userID),
  CONSTRAINT chk_listing_price
    CHECK (listingPrice >= 0),
  CONSTRAINT chk_listing_sold
    CHECK (
      (status = 'SOLD' AND buyerID IS NOT NULL AND soldAt IS NOT NULL)
      OR (status <> 'SOLD' AND buyerID IS NULL AND soldAt IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE reviews (
  reviewID        INT AUTO_INCREMENT PRIMARY KEY,
  customerID      INT NOT NULL,
  performanceID   INT NOT NULL,
  eventRating     TINYINT NOT NULL,
  venueRating     TINYINT NOT NULL,
  commentText     TEXT NOT NULL,
  createdAt       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_reviews_customer_perf (customerID, performanceID),
  CONSTRAINT fk_reviews_customer
    FOREIGN KEY (customerID) REFERENCES users (userID),
  CONSTRAINT fk_reviews_perf
    FOREIGN KEY (performanceID) REFERENCES performances (performanceID),
  CONSTRAINT chk_reviews_event_rating
    CHECK (eventRating BETWEEN 1 AND 5),
  CONSTRAINT chk_reviews_venue_rating
    CHECK (venueRating BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET FOREIGN_KEY_CHECKS = 1;
