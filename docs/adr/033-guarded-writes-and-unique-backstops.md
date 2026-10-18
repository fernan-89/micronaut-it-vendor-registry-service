# ADR-033: Guarded Writes and Unique Backstops

## Status
Accepted. Same mechanics as the other registries (alerting ADR-033).

## Context
Several people (and several instances of the service) can change the same vendor or agreement at once, and two people can register the same thing at once. Checking first and writing after is a race.

## Decision
- Every change to a vendor or an agreement is **one atomic update** guarded by the status that was loaded (`ACTIVE` or `INACTIVE`, `ACTIVE` or `PAUSED`) that also appends the audit entry. The writer that finds the state already changed gets a 409 (`ERR-VND-00409`); the audit trail never gets an entry for a change that did not happen. An `update` is guarded the same way, so two simultaneous edits cannot both succeed from one state.
- **Unique indexes are the atomic backstop**: `(organisationId, name)` for vendors and `(organisationId, vendorId, reference)` for agreements. A duplicate is a 409 whichever instance writes first; the use case does not look first. The same reference for another vendor, or in another tenant, is fine.
- Making an agreement checks that the vendor is the tenant's (404) and ACTIVE (409). A vendor deactivated between the check and the write can still get that one agreement, which is harmless (it is an agreement, not a payment) and visible in the audit trail.
- List indexes serve the real queries: vendors by status, agreements by last day.

## Consequences
- Positive: no lost update, no duplicate under concurrency, no distributed lock.
- Negative: a caller that loses a race must read again and retry (the message says so).
