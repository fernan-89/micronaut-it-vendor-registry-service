# micronaut-it-vendor-registry-service

BIAN-aligned Service Domain **it-vendor-registry** (Control Record: `Vendor`), port `8108`.

The first slice of Journey 15 (breadth): a **registry of vendors and what they owe**. A `Vendor` is a company the organisation buys from or is
supported by. An `Agreement` is what one vendor owes: a contract, a warranty, a support plan or a licence term, with the days it is in force and how
many days ahead of the end somebody must be told. The registry says, **when you ask**, whether each agreement has not started, is in force, is about
to end or has ended.

## What it guarantees, and what it does not

- **No people, no money** (ADR-030): a vendor is a name and a category (`HARDWARE`, `SOFTWARE`, `CLOUD`, `SERVICES`, `OTHER`); no contact person,
  e-mail, phone, address, tax number, price or invoice is kept. The contract `reference` is an identifier (`[A-Za-z0-9._/-]{1,40}`), never a sentence.
- **An agreement is judged when read** (ADR-031), never stored: `standing` is `PAUSED` (not judged), `NOT_STARTED`, `IN_FORCE`, `EXPIRING` (the last
  day is `noticeDays` away or nearer; the last day itself is still in force) or `EXPIRED`, with `daysToEnd` (negative once it ended). It cannot go stale.
- **It does not renew anything**: `autoRenew` records what the contract does; a renewal is a person moving the last day with `update`.
- **A vendor is never deleted**: it is deactivated, and no new agreement can be made with an inactive vendor (409). An agreement is paused, never deleted.
  The vendor and the reference identify an agreement and cannot be changed.
- **Staff only** (ADR-032): every route refuses a `REQUESTER` with 403 `ERR-VND-00403`; another tenant's vendor or agreement answers 404.
- **Safe with several instances** (ADR-033): a vendor name is unique per organisation, an agreement reference is unique per vendor, every change is
  guarded by the status loaded and appends its audit entry in the same update.
- **Not here yet:** telling someone when an agreement is about to end (a later pairing with alerting), cost, documents, contacts, reading contracts,
  checking the asset against the asset registry.

## BIAN Behavior Qualifier Contract

`X-Tenant-Id` is mandatory on every call; `X-Executor` is mandatory on the actions; `X-Role` is optional and, with platform security on, comes from
the verified token.

| Behavior Qualifier | Route |
|---|---|
| initiate | `POST /it-vendor-registry/v1/initiate` `{"name":"Dell","category":"HARDWARE"}` |
| retrieve | `GET /it-vendor-registry/v1/{id}/retrieve` |
| retrieve (collection) | `GET /it-vendor-registry/v1/retrieve?status=&category=` (oldest first) |
| update | `PUT /it-vendor-registry/v1/{id}/update` (the whole definition again, in any status) |
| control/deactivate | `PUT /it-vendor-registry/v1/{id}/control/deactivate` (ACTIVE -> INACTIVE: no new agreement) |
| control/activate | `PUT /it-vendor-registry/v1/{id}/control/activate` (INACTIVE -> ACTIVE) |
| audit-log/retrieve | `GET /it-vendor-registry/v1/{id}/audit-log/retrieve` |
| agreement/initiate | `POST /it-vendor-registry/v1/agreement/initiate` `{"vendorId":"<uuid>","kind":"WARRANTY","reference":"WTY-2026/01","assetId":"<uuid, optional>","startsOn":"2026-01-01","endsOn":"2028-12-31","noticeDays":60,"autoRenew":false}` (`kind`: CONTRACT, WARRANTY, SUPPORT, LICENSE) |
| agreement/retrieve | `GET /it-vendor-registry/v1/agreement/{id}/retrieve` (judged today) |
| agreement/retrieve (collection) | `GET /it-vendor-registry/v1/agreement/retrieve?status=&vendorId=&assetId=&kind=&standing=` (oldest first) |
| agreement/update | `PUT /it-vendor-registry/v1/agreement/{id}/update` `{"kind":"SUPPORT","assetId":null,"startsOn":"2026-01-01","endsOn":"2029-12-31","noticeDays":90,"autoRenew":true}` (a renewal is a new last day) |
| agreement/control/pause | `PUT /it-vendor-registry/v1/agreement/{id}/control/pause` (ACTIVE -> PAUSED: no longer judged) |
| agreement/control/resume | `PUT /it-vendor-registry/v1/agreement/{id}/control/resume` (PAUSED -> ACTIVE) |
| agreement/audit-log/retrieve | `GET /it-vendor-registry/v1/agreement/{id}/audit-log/retrieve` |

```text
vendor:    ACTIVE <-> INACTIVE
agreement: ACTIVE <-> PAUSED ; judged when read: NOT_STARTED / IN_FORCE / EXPIRING / EXPIRED
```

```bash
curl -X POST "http://localhost:8108/it-vendor-registry/v1/agreement/initiate" -H "X-Tenant-Id: <organisationId>" -H "X-Executor: <userId>" \
  -H "Content-Type: application/json" \
  -d '{"vendorId":"<vendorId>","kind":"WARRANTY","reference":"WTY-2026/01","startsOn":"2026-01-01","endsOn":"2028-12-31","noticeDays":60,"autoRenew":false}'
```

## Error catalog

| Code | HTTP | Meaning |
|---|---|---|
| `ERR-VND-00403` | 403 | A REQUESTER used the vendor registry (ADR-032) |
| `ERR-VND-00404` | 404 | Vendor or agreement not found (another tenant's answers the same) |
| `ERR-VND-00409` | 409 | Duplicate vendor name or agreement reference, illegal transition (deactivate an inactive vendor), an agreement for an inactive vendor, or it changed while the write was applied (retry) |
| `ERR-VALIDATION-00400` | 400 | Payload/header/identifier validation failure, an agreement that ends before it starts or lasts over 3660 days, a notice outside 0..365, a reference that is not an identifier, a malformed date |
| `ERR-INTERNAL-00500` | 500 | Unexpected technical failure |

## Configuration

`HASH_SERVICE_URL` (default `http://localhost:8080`, the sovereign ids), `MICRONAUT_SERVER_PORT` (default 8108), `mongodb.uri`,
`thinklab.mongo.create-indexes` (default on). The service calls no other service and has no scheduler.

Do not put personal data in a vendor name: it is stored with the vendor.

## Architecture decisions

001 hexagonal architecture · 005 UUID identity sovereignty and audit tracing · 013 BIAN conventions · 019 HTTP 409 for state conflicts ·
030 vendors and agreements, with no people and no money · 031 an agreement is judged when read · 032 staff only · 033 guarded writes and unique backstops.

## License

Licensed under the [PolyForm Strict License 1.0.0](LICENSE): you may read and use this software for noncommercial purposes only. Modifying it, creating derivative works, redistributing it and any commercial use are not permitted without a separate written license. This software is not open source.
