# Account credentials and interrupted commands

New accounts use PBKDF2-HMAC-SHA-256 with 600,000 iterations, a new random 128-bit salt for every account, and a 256-bit derived key. The database stores a versioned string containing the algorithm, version, work factor, salt and derived key. Password comparisons use constant-time byte comparison. This uses the JDK cryptography provider and follows the [OWASP password storage guidance](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html) for PBKDF2 without adding an external authentication provider.

Passwords retain leading/trailing spaces and Unicode characters; they are never trimmed or silently truncated. They must be nonblank and at most 1,024 Java characters. The TUI hides password input on an interactive terminal. When input is redirected, passwords are read from the input stream without being echoed by the application. Other form fields still trim surrounding whitespace. Email lookup and creation normalize surrounding whitespace and case; no external email or payment-card validation occurs.

## Existing accounts

For migration, existing 64-character hexadecimal SHA-256 hashes remain verifiable. After a successful login using the correct account role, the application replaces that hash with a fresh PBKDF2 hash using a conditional update. Failed passwords and wrong-role logins do not upgrade or modify the account. A concurrent login/password change is rechecked instead of overwriting newer credentials. Accounts not yet used retain their old hash until successful authentication, so migration is incomplete until those accounts have signed in or had their passwords reset.

The historical literal `hash_password` placeholder is no longer accepted. Such demonstration accounts must be recreated or reset; knowing the old shared sample password does not authenticate them. Unknown versions, malformed encodings, invalid salt/key sizes and excessive verification work factors fail authentication. Account deletion verifies credentials without upgrading a hash, so a refused deletion leaves the account unchanged.

## Input completion and payment data

End-of-input aborts an incomplete command immediately. It does not supply blank required fields, default quantities, a default account role, or affirmative confirmation. Explicitly pressing Enter can still choose a displayed default. The application exits cleanly after end-of-input. Redirected input/output uses UTF-8; interactive terminals use their console encoding.

Customer registration requires a fictitious card identifier and a complete expiry in `YYYY-MM` format. No card-brand, Luhn, real-account or expiry-in-the-future check is performed. Required profile data and expiry syntax are checked before any database writes.

Deleting an unused account also removes its payment-card row if no other user or order references that card, in the same transaction. A card used by another account or historical order is retained.

Accounts with history are deactivated in place: the database keeps their user ID and role for transaction/report references, replaces the email with a unique tombstone, replaces name/address/birthday with nonpersonal values, disables the password, clears the profile card and records `deletedAt`. Orders, payment snapshots, acquisitions, ticket ownership, cancellations, resales, reviews and organizer events remain intact. Deleted profiles cannot be looked up for authentication, sign in, or continue authorized mutations through an old session. The old email can be used for a new account with a different ID; this does not transfer the old account's history or privileges.

Before deletion, customers must cancel or transfer their active tickets for future performances; organizers must cancel future scheduled performances. The TUI explains these outstanding actions and leaves the database unchanged when deletion is refused. Past/history-only accounts can be deactivated without erasing sales or reviews. Normal cancellation deadlines still apply; deletion does not bypass them.

Implementation references: [JDK PBEKeySpec](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/spec/PBEKeySpec.html), [JDK Console and terminal detection](https://docs.oracle.com/en/java/javase/24/docs/api/java.base/java/io/Console.html).
