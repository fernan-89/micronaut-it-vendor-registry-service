# ADR-032: The Vendor Registry Is for IT Staff

## Status
Accepted

## Context
Vendors and agreements say what the organisation buys and until when. A requester (end user) has no business reading them.

## Decision
- Every route refuses a `REQUESTER` with 403 `ERR-VND-00403`; any other role, or none, is staff (the same rule as the monitor, the alerting service and the other registries).
- Every lookup is scoped by `X-Tenant-Id`: another tenant's vendor or agreement is a 404, never a 403. An agreement for a vendor of another tenant is a 404 on the vendor.
- **There is no delete.** A vendor is deactivated and an agreement is paused (their history stays).
- Audit is kept on each aggregate for real events (initiated, updated, deactivated or paused, activated or resumed), with the executor id.
- Nothing free-form is kept: names are the company's, references are identifiers, the rest are fixed lists, numbers and dates.

## Consequences
- Positive: one access rule across the operations services.
- Negative: no read-only view for auditors outside IT; a role for that would be a platform decision.
