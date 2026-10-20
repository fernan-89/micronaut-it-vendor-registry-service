# ADR-034: The Promises Contract

## Status
Accepted. Implements platform ADR-001 (`thinklab-platform/docs/adr/001-the-promises-contract.md`).

## Context
The alerting service used to know this registry's fields and verdicts (which states are a broken promise, and the code of each). A rule that lived in a consumer was untested with the domain and had to be kept in step by hand.

## Decision
- The registry answers `GET /it-vendor-registry/v1/promises/retrieve?changedWithinDays=&limit=` as the platform contract says: every broken promise and every one that changed within the window (30 days unless asked, 1 to 365), at most `limit` of each (500 unless asked, 1 to 1000), broken first.
- **The judgement lives here:** an agreement is **broken** when it is about to end (`AGREEMENT_EXPIRING`) or has ended (`AGREEMENT_EXPIRED`) (somebody must renew or replace it), **kept** while in force, and **not judged** before it starts or when paused. The name is the contract's own reference; the asset, if any, is the agreement's; the status is the agreement's.
- **`changedAt`:** the agreement's last update (a renewal is an update).
- Staff only, tenant-scoped, read-only.

## Consequences
- Positive: the rule is judged and tested with the domain; the consumer needs no knowledge of this registry.
- Negative: an agreement that becomes about to end or ended by time passing is returned as broken whether or not it changed; the vendors themselves hold no promise and are not listed.
