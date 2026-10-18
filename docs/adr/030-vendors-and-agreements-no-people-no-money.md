# ADR-030: Vendors and Agreements, With No People and No Money

## Status
Accepted

## Context
Journey 15 (breadth) starts with who the organisation buys from and what each vendor owes: contracts, warranties, support plans and licence terms. The temptation is to model a procurement system (contacts, prices, invoices, purchase orders). That would hold personal data (a vendor's account manager) and financial data, and nothing in the platform needs either yet.

## Decision
- Two aggregates. A **`Vendor`** (the Control Record) is a name and a category (`HARDWARE`, `SOFTWARE`, `CLOUD`, `SERVICES`, `OTHER`), `ACTIVE` or `INACTIVE`. An **`Agreement`** is what one vendor owes: a `kind` (`CONTRACT`, `WARRANTY`, `SUPPORT`, `LICENSE`), the contract's own `reference`, optionally the asset it covers, the first and last day it is in force, how many days ahead of the end somebody must be told (`noticeDays`, 0 to 365), and whether it renews itself (`autoRenew`).
- **No personal data**: no contact person, e-mail, phone, address or tax number. A name is the company's. This keeps the registry outside the personal-data duties (LGPD) and out of any access review.
- **No money**: no price, currency, payment terms or invoice. It is an obvious next slice, but it needs decisions (currency, taxes, allocation) that nobody has asked for.
- **The reference is an identifier**, `[A-Za-z0-9._/-]{1,40}`, never a sentence: it is what a person finds on the paper, and nothing free-form from a vendor is kept.
- An agreement names its vendor and reference once; they identify it and **cannot be changed** (`update` takes everything else). A vendor is never deleted: an unused one is deactivated, and no new agreement can be made with an inactive vendor (409); the agreements it already has stay.
- The registry **does not read contracts** and does not call any other service: the person who knows the dates types them.

## Consequences
- Positive: a small model that answers "what expires soon, and who owes it" without personal or financial data.
- Negative: no cost view, no contact to call, no document attached; the asset id is not checked against the asset registry (the same choice as the other registries).
