# Fresh MyTix demonstration data

The Java loader generates this dataset from code and the current UTC date. It does not read or reuse the former SQL seed. All people, credentials, cards, artists, teams, events, sales and reviews are fictional. Real venue names and approximate map positions make geographic searches understandable; the small seating layouts and capacities are demonstration layouts, not real venue seating plans or current event schedules.

## Load or clear

Install the current `sql/schema.sql` in your selected MySQL database, configure `MYTIX_DB_HOST`, `MYTIX_DB_PORT`, `MYTIX_DB_NAME`, `MYTIX_DB_USER` and `MYTIX_DB_PASSWORD`, and install [the four NLP models](opennlp-models.md). Stop other application writers while running maintenance. The scripts use the Maven Wrapper and JDK 17+.

These commands intentionally replace **all data in the 22 MyTix tables** of the named database:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/load-demo.ps1 -Database mytix
```

```bash
bash scripts/load-demo.sh mytix
```

To clear project data without loading a new dataset:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/load-demo.ps1 -Database mytix -ClearOnly
```

```bash
bash scripts/load-demo.sh mytix --clear
```

The database argument must agree with `MYTIX_DB_NAME` when that variable is already set. No MySQL server or database is dropped. The service checks the connected schema and exact project table set, keeps all foreign-key constraints enabled, and uses ordered `DELETE` plus inserts in one InnoDB transaction. Generation, password hashing and NLP extraction finish before deletion starts; missing models therefore leave the old dataset intact. SQL failures roll back the data transaction. Auto-increment counters are not reset; the demo itself uses explicit stable IDs, while subsequent user-created IDs may continue above earlier counters.

`DemoDataService.resetAndLoad()` and `clear()` are reusable by a Web controller. The controller must acquire its exclusive application-wide database gate, authorize the operation, and invalidate old sessions after success. A MySQL advisory lock prevents simultaneous loaders. The service returns counts, the UTC anchor date, named scenario IDs and public demo login details.

The standalone `sql/load.sql` is generated from the same fresh model. Select the project database explicitly and run it with the MySQL **batch client without `--force`**. It replaces all project data transactionally; batch failure closes the connection and rolls the transaction back. Do not source it in a client configured to continue after errors. Its dates are relative to the UTC load day. Password hashes and actual NLP occurrences are already included, so SQL loading itself needs no Java/model installation. Stop other writers during SQL maintenance.

To regenerate the SQL after changing the demo model, compile and install the NLP models, then run:

```powershell
java -cp 'target/classes;target/dependency/*' mytix.database.demo.DemoSqlExport sql/load.sql
```

On Linux/macOS use `target/classes:target/dependency/*` as the classpath. Regeneration creates new random password salts and writes only the requested SQL file; it never connects to MySQL.

## Public synthetic logins

Every demo account uses password **`MyTixDemo!42`**. Each stored password has a different random PBKDF2 salt.

| Account | Email |
| --- | --- |
| Ordinary customer | `customer003@demo.mytix.test` |
| First organizer | `organizer01@demo.mytix.test` |
| Other organizers | `organizer02@demo.mytix.test` through `organizer05@demo.mytix.test` |
| Customers | `customer001@demo.mytix.test` through `customer100@demo.mytix.test` |
| Flagged scalper examples | Customers 001 and 002 |

All accounts are at least 20 years old at the load date. Account creation precedes the generated transaction history. Fictitious card identifiers begin `DEMO-CARD-`; order expiry values intentionally preserve a prior payment snapshot distinct from the current profile expiry.

## Dataset and examples

The plan contains 8 venues in Toronto, Vancouver, New York and Seattle; 3 segments and 6 genres; 18 fictional artists/teams; 24 events managed by 5 organizers; 72 performances, exactly 36 past and 36 future; 100 customers; 645 orders; and 1,903 tickets. There are 1,906 acquisition records, 20 listings and 65 reviews across all 24 events. Every performance has 3 tiers and assigns all 3 venue sections exactly once. Every venue combines reserved and standing capacity. Each event tours another venue and repeats at its first venue, with different tier mappings/prices on the repeated performance.

- Past sold-out and below-25% performances span several cities and months. Sales timestamps precede attendance and remain within the preceding year.
- Reserved Main Floor rows A/B have consecutive seat numbers. Balcony row C has only 1, 3, 5, 7, 9, 11, while row D is consecutive. Future performances retain reserved and standing availability. Some performance-specific seats are blocked.
- One show starts in three days, closing the normal customer cancellation window. Other shows start more than seven days later. A completely unsold future performance allows tier updates; sold tiers retain their historical sales after refunds.
- Customer cancellations preserve actor/time, acquisition-specific refund amounts and primary sale facts. Two organizer-cancelled future performances preserve their refunded tickets and orders.
- One ticket has two completed resales and a third active listing. Other examples include withdrawn/relisted tickets, exact-cap prices and a resale buyer's cancellation refunded at the resale amount.
- Customers 001 and 002 each buy ten tickets to one Toronto performance and list six distinct acquisitions, placing both above the annual city scalper threshold. A withdrawn/relisted first ticket does not increase the distinct-acquisition numerator. They make no new acquisition after crossing the threshold.
- Every event has attendee reviews from its completed first performance, with recurring ordinary phrases such as “clear sound” and “friendly staff”. The loader saves actual OpenNLP phrase occurrences for SQL R9 ranking. It does not insert fabricated NLP output.

The CLI prints scenario IDs including `futureConsecutiveAndGA`, `futureUnsoldTier`, `soonCancellationClosed`, `pastSoldOut`, `pastLowSales`, `twiceResoldTicket`, `refundedResaleTicket` and `flaggedScalper1/2`. This list describes generated examples; live verification evidence is recorded separately during development.

## Classification and geography sources

Checked on 2026-10-02. The [official Ticketmaster Discovery API classification examples](https://developer.ticketmaster.com/products-and-docs/apis/discovery-api/v2/) supply the segment/genre hierarchy: Music → Rock; Sports → Hockey, Basketball and Tennis; Arts & Theatre → Comedy and Magic & Illusion. These are genres, rather than inferred artist labels or subgenres. Events and performers remain wholly fictional; no Ticketmaster API key or live event download is required to load them.

| Venue | Address/postal source |
| --- | --- |
| Scotiabank Arena | [Official venue information](https://www.scotiabankarena.com/venue-information): 40 Bay Street, Toronto, M5J 2X2 |
| Meridian Hall | [TO Live](https://www.tolive.com/): 1 Front Street East, Toronto, M5E 1B2 |
| Roy Thomson Hall | [Official visitor information](https://roythomsonhall.mhrth.com/plan-your-visit/): 60 Simcoe Street, Toronto, M5J 2H5 |
| Rogers Arena | [Official contact information](https://rogersarena.com/contact-us/): ticket centre at 800 Griffiths Way, Vancouver, V6B 0N8 |
| Madison Square Garden | [Official venue information](https://www.msg.com/venue-rentals-event-rentals/rent-madison-square-garden): 4 Pennsylvania Plaza, New York, 10001 |
| Radio City Music Hall | [Official venue information](https://www.msg.com/venue-rentals/radio-city-music-hall-roxy-suite): 1260 6th Avenue, New York, 10020 |
| Climate Pledge Arena | [Official directions](https://climatepledgearena.com/plan-your-trip/): 334 1st Avenue North, Seattle, 98109 |
| Paramount Theatre | [Official Seattle Theatre Group directions](https://www.stgpresents.org/theatres/directions-parking/): 911 Pine Street, Seattle, 98101 |

Coordinates are rounded venue/map approximations for distance demonstrations, not surveyed entrance positions. The three downtown Toronto venues are within roughly one kilometer of each other. Their three postal codes are connected by an explicit, symmetric **curated demo adjacency graph**. That graph is a declared demonstration search relationship, not a claim that Canada Post publishes or endorses those postal boundaries. Similar postal prefixes or numeric sequences are never used to infer adjacency at query time.
