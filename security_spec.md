# Security Specification — Moment Zero-Knowledge Blind Relay

## 1. Data Invariants
1. **Authentication Gate**: Unauthenticated callers (`request.auth == null`) cannot read, list, create, update, or delete any document in `/rooms` or `/rooms/{roomId}/envelopes`.
2. **Room Ownership & Isolation**: A `BlindRoom` can only be created with `hostUid == request.auth.uid` and `status == 'waiting'`. Only waiting rooms or rooms where `request.auth.uid` is `hostUid` or `guestUid` can be read/listed.
3. **Terminal State Locking**: Once a `BlindRoom` transitions to `status == 'shredded'`, no further updates are permitted.
4. **Envelope Immutability & Participant Isolation**: `BlindEnvelope` documents are append-only (`update: if false`), require `request.auth.uid in participantUids`, and can only be read, listed, or deleted by participants in `participantUids`.

## 2. The Dirty Dozen Payloads
1. Unauthenticated read on `/rooms/room_1` -> REJECT
2. Unauthenticated write on `/rooms/room_1` -> REJECT
3. Creating room with spoofed `hostUid != request.auth.uid` -> REJECT
4. Creating room with ghost field `isAdmin: true` -> REJECT
5. Creating room with initial status `'active'` instead of `'waiting'` -> REJECT
6. Non-participant reading an `'active'` room -> REJECT
7. Updating a `'shredded'` terminal room -> REJECT
8. Mutating immutable `createdAt` or `hostUid` during room join -> REJECT
9. Creating envelope without `request.auth.uid` in `participantUids` -> REJECT
10. Updating an existing encrypted envelope (`allow update: if false`) -> REJECT
11. Cross-user reading or listing envelopes when not in `participantUids` -> REJECT
12. Value poisoning with oversized `ciphertext` (> 8192 chars) or invalid `ttlSeconds` -> REJECT
