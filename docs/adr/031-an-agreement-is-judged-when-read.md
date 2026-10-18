# ADR-031: An Agreement Is Judged When Read

## Status
Accepted. Same principle as the backup, patch and capacity registries (backup ADR-028, patch ADR-029, capacity ADR-031).

## Context
Whether an agreement is about to end is a fact about the calendar, not about the agreement. Storing the verdict ("expiring") means somebody has to keep it true every day, and a missed run would leave a lapsed warranty looking fine.

## Decision
- Nothing about the verdict is stored. `Agreement.assess(today)` returns a **standing** and the **days to the end** each time it is read:
  - `PAUSED`: the agreement is not judged (no days either);
  - `NOT_STARTED`: today is before the first day;
  - `EXPIRED`: today is after the last day (the days to the end are negative);
  - `EXPIRING`: the last day is `noticeDays` away or nearer (**the last day itself is still in force**, with 0 days left);
  - `IN_FORCE`: otherwise.
- "Today" is the registry's clock in UTC. The days are calendar days, stored as ISO text (`2026-12-31`), so no time zone can move them.
- **`autoRenew` does not extend anything.** It records what the contract does; a renewal is a person moving the last day (`update`), and until then the agreement reads `EXPIRING` and then `EXPIRED`, which is the honest answer for a registry that does not read the contract.
- The list filters by `status`, `vendorId`, `assetId`, `kind` and `standing`; the standing is judged when read, so it is filtered in memory over the tenant's agreements, oldest first.

## Consequences
- Positive: it cannot go stale; a later alerting rule can ask "what is expiring" without a job to keep a flag true.
- Negative: no history of past verdicts (only the audit trail of what people did), and the list reads the tenant's agreements to filter by standing.
