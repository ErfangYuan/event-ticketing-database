# MyTix domain contract

This document defines the behavior being verified during the database audit and Web GUI upgrade. It is an acceptance contract, not a claim that every rule is implemented. Open defects and remaining work are tracked in [GitHub Issues](https://github.com/ErfangYuan/event-ticketing-database/issues). Private requirement documents, test fixtures and execution evidence remain outside version control in `local/`.

## Time and money

Use UTC consistently for stored transaction instants and application comparisons. A performance's date and time are interpreted in the configured application timezone, initially UTC. An upcoming performance has a future start and is not cancelled. Attendance requires a completed, noncancelled performance and a ticket held at completion. Inclusive date filters mean midnight on the first date through, but excluding, midnight after the last date. Reject reversed ranges.

The default annual reporting window is one calendar year ending at the evaluation time. Explicit day-window inputs mean exactly that many days. Reviews are allowed for performances completed within the preceding 365 days; this is the chosen interpretation of recent attendance.

Store monetary amounts with decimal precision and two currency decimal places. Free tickets are valid; negative prices are not. A markup ratio for a free ticket is undefined and displayed as unavailable. The default resale cap is 120% of original face value. Compare cap prices after rounding to currency precision. Demo payment details are fictitious and have no card-network validation or external payment dependency.

## Inventory and ownership

- Reserved sections contain numbered seats within named rows. General admission sections have a standing capacity and no seats or rows.
- Every section of a performance's venue maps to exactly one of that performance's price tiers. The mapping can differ for another performance at the same venue.
- Sellable capacity is reserved seat count minus blocked seats, plus general admission capacity. Available inventory subtracts active sold tickets from that capacity.
- A reserved seat has at most one active ticket per performance; general admission never exceeds capacity. A mixed order succeeds entirely or leaves no changes.
- An order purchases one or more tickets to one performance. Primary ticket face value and the payment information used are historical snapshots.
- A resale transfers ownership of one existing ticket. It does not create inventory or another primary ticket sale. Preserve every ownership acquisition and its order, amount and timestamp.
- Only an organizer's own events and performances can be changed. A future tier may change price only if it has never sold any ticket, including tickets later cancelled or refunded.
- A sold seat cannot be blocked. Blocks apply only to the selected performance and can be removed later.

## Cancellation, reviews and accounts

Customers may cancel through exactly seven days before performance start. Cancellation authority follows the order by which the current owner acquired the ticket; a former owner cannot cancel another person's ticket. This is the chosen interpretation of the original order-cancellation requirement after resale. Refund the current acquisition amount, preserve historical sales, record the cancellation actor/time and release the inventory.

Only the event's organizer may cancel a performance. Refund all currently held tickets once, withdraw active listings and preserve ownership, sale and cancellation history. Repeated cancellation must not create a second refund. A cancelled performance cannot accept bookings or resales.

A customer may review a recently completed performance once if they held a noncancelled ticket at completion. Both ratings are integers from one through five. Earlier owners who transferred away their tickets are not attendees through those tickets.

Account creation requires the appropriate profile/payment fields and age of at least 18. Passwords use salted adaptive hashes. Email uniqueness and login use a consistent normalization policy. Deletion must disable authentication and remove personal information while retaining anonymized transaction/history facts needed by reports. Existing tickets or organizer events require an explicit lifecycle policy; history must not disappear through cascading deletes.

## Searches

Q1 measures great-circle distance in kilometers using the Haversine formula and a mean Earth radius of 6371.0088 km. Include the radius boundary. Rank by distance or cheapest available ticket price. Sold-out performances may remain visible with unavailable price and sort last in either price direction. Validate coordinates and reject non-finite values.

Q2 uses full normalized postal codes and an explicit symmetric adjacency relationship. Shared prefixes or sequential postal codes are not sufficient evidence of adjacency. The demo must document its geographic relationships. City and postal identities include country.

Q3 matches a complete address, allowing case/whitespace normalization. An existing venue remains visible even when it has no upcoming performance.

Q4 combines one of Q1–Q3's geographic predicates with an inclusive date range and minimum available quantity in SQL. Manually copying IDs into an unrelated date search does not fulfill that operation.

Q5 combines all supplied filters with AND. A reserved/general-admission filter limits both inventory and cheapest-price calculations to matching sections. The price range applies to the cheapest ticket with available, purchasable inventory; a higher-priced eligible tier does not conceal a cheaper ticket outside the requested range. Missing price is not zero.

Q6 reports every section's tier, price, available quantity, sold quantity and blocked quantity. For reserved seating, these quantities reconcile with physical capacity. General admission has no blocked seats. Cancelled-performance summaries must clearly distinguish physical capacity from purchasability.

Q7 finds the lowest-price run of the requested number of available seats with consecutive numeric seat numbers in one row and one section. The optional budget applies to the total. Return tied minimum-price runs in stable section/row/start-seat order. General admission does not form consecutive-seat runs.

## Reports

All report aggregation, filtering and ranking runs in MySQL through Java. R9 may use Java for noun phrase extraction.

| Report | Definition |
| --- | --- |
| R1 | Primary ticket sale count and gross primary revenue by country/city or venue, filtered by purchase timestamp. Include later-refunded original sales; exclude resale proceeds. Gross is not net after refunds. |
| R2 | Distinct event count and performance count by segment/genre at country, city or venue grain. City identity includes country; venue identity is venueID, so equal names do not merge venues. Include past, upcoming and cancelled performances. Count touring events distinctly within each group. |
| R3 | Organizer gross primary revenue under the same policy as R1, ranked overall, within country or within city. |
| R4 | Within each city and annual acquisition cohort, flag customers with at least ten ticket acquisitions who listed strictly more than half of those acquisitions for resale. Primary and resale acquisitions count. Repeated listings without a new acquisition count once. |
| R5 | Rank by orders placed, not ticket quantity, in the requested interval. For city mode, require at least two orders in that city during the annual window independently of the reporting interval. Include completed resale acquisition orders and orders later cancelled. |
| R6 | Count customer-initiated ticket cancellations and organizer-initiated performance cancellations by their actor and cancellation instant. An organizer refund does not become a customer cancellation. |
| R7 | Current active sold inventory divided by current sellable capacity, per performance or price tier. Month/city mode includes sold-out performances and those strictly below 25%; exactly 25% is not low sales. Zero capacity is undefined, and cancelled performances are excluded from these categories. |
| R8 | Completed resale count, mean relative markup over original face value, and fraction of all listings priced exactly at their stored cap snapshot. Active/withdrawn listings contribute to the cap fraction, not completed count/markup. Top ten uses completed resale count and completion timestamps in the requested period. |
| R9 | Per-event noun phrases accumulated over eligible review comments, normalized to lowercase, sorted by occurrence count descending and phrase ascending. Save each extracted occurrence in `review_noun_phrases` in the review's transaction; preserve duplicates. MySQL counts and ranks the stored occurrences. Show up to ten; no reviews gives an empty result. Missing models explicitly reject extraction/review writes; reading an existing projection needs no models and never writes data. |

For R4, the chosen enforcement policy records the threshold-crossing listing and flags the account immediately, then blocks further ticket acquisitions and new listings. Viewing history, withdrawing listings and lawful cancellation remain possible. Reacquiring a ticket creates a new acquisition; withdrawing and relisting without a transfer does not.

Ranks use competition ranking (1, 1, 3), with stable ID ordering for ties. Top-ten reports return at most ten entities with deterministic ties. Undefined ratios are unavailable, not zero or infinity. Reports phrased as per-event/per-performance retain zero-activity entities where meaningful.

## Organizer toolkit and demonstration data

The toolkit must suggest a tier structure, each tier's price and its share of venue capacity. Document the comparable-performance cohort, fallback, weights and rounding. Shares sum to 100%. An illustrative revenue response to price changes must state its assumptions and must not claim causal predictive accuracy.

The new demonstration dataset must independently cover at least eight venues in four cities/two countries; twenty events by five organizers in three segments/six genres, featuring fifteen artists; sixty past/upcoming performances; one hundred adult customers; three hundred orders; and eight hundred tickets. Include nearby venues, touring and repeated productions, varied per-performance tiers, reserved and general admission capacity, sold-out and low-sales performances, contiguous/gapped rows, cancellation windows, repeated ownership changes, cap-priced listings, qualifying scalper cohorts and reviews across at least ten events. Date-relative loading should keep these examples useful on later dates.

## Delivery boundaries

The Web GUI will retain Java/MySQL as the business layer and expose a project-specific API consumed through Next.js. Its graph and table browsing must cover the complete relational model, with stable indexed pagination and safe handling of private account/payment fields. Authentication uses local accounts without an external identity provider. Every privileged mutation enforces ownership on the server, even when a caller bypasses displayed controls.

Dataset loading, ordinary creation/deletion and clearing all project data are explicit demonstration features. Their transactions and responses must remain consistent with account/session isolation. This contract does not describe a production payment platform.
