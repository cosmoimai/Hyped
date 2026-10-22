# Android onboarding and legal consent

## Experience

The signed-out countdown demo uses the existing Manrope theme, soft blue, a one-second live sample countdown and three selectable preview themes. It creates no room and makes no authenticated calls. It scrolls at large text sizes, provides a spoken countdown label, cancels its timer on disposal and disables the theme transition when Reduce Motion is requested.

An incoming invitation opens its public preview directly, including while authentication initializes. The recipient can choose the demo and then **Skip to invite**. That action preserves the exact pending invite in memory; it does not persist an invite token/code to preferences or log it. Sign-in continues to preserve that intent. Explicit logout/account change/deletion still clears it under the existing cleanup rules.

After sign-in, `/policy-acceptance` checks server status before Home/Create. Three separate checkboxes start unchecked: Terms of Service and Content Rules, Privacy Policy, and 18+. A submission requires all three. A signed-in public preview remains available, but Join first remembers the invite and opens consent if needed. Acceptance returns to that preview; it does not automatically join. A directly requested create route resumes after acceptance. Legal document routes and account Settings (including sign-out/deletion) remain available without accepting new policies.

No accepted bit is persisted locally. The app rechecks after authentication and foreground return, fences results against the session/generation, and discards state on logout. First-load status errors fail closed with Retry; an already server-confirmed account stays on its screen during foreground rechecks, and all mutations remain server-guarded. Acceptance failures retain the choices for retry. A changed required version clears all checkbox selections before another submission. Version-conflict and required-consent API responses recheck status without treating them as authentication expiry. Public preview continues to use the existing interceptor-free Dio.

`/legal/terms` and `/legal/privacy` fetch the current public document. Consent links include the exact version being accepted. Documents are selectable, scrollable text accessible before sign-in, during initialization, and from Settings. Document failures show a safe retry action. They contain no session or invite credentials. There is no WebView, external SDK or new dependency.

## Versions and publication

The generated initial documents are in `backend/src/main/resources/legal/{terms,privacy}/2026-10-22.txt`. Terms include the content rules; their versions must move together. The initial configured Terms, Privacy and content-rules versions are all **2026-10-22**.

These are generated initial drafts, not evidence of legal approval or a legal-compliance guarantee. Review their substantive terms against the actual deployed service, operator, applicable law, provider regions, retention, public deletion/support availability and store disclosures before public release. No real operator identity, contact address or jurisdiction has been invented.

Deployment configuration:

- `LEGAL_OPERATOR`: the real responsible operator/legal identity.
- `LEGAL_CONTACT_EMAIL`: a monitored privacy/support/grievance contact.
- `LEGAL_PUBLICATION_READY=true`: enable only after the documents and contact details are reviewed. Defaults false; draft reads work, but acceptance and create/join fail closed. Setting true without valid contact/operator or bundled required versions prevents startup.
- `TERMS_VERSION`, `PRIVACY_VERSION`, `CONTENT_RULES_VERSION`: default `2026-10-22`. Terms/content rules must match because they are one document.

`{{operator}}` and `{{contact}}` are filled only from operator configuration. Draft reads with missing values explicitly show pending publication details. Freeze the operator/contact substitution and document text for a published version. To make a material change, add new versioned files, change the corresponding required version(s), retain old files, review and deploy all serving instances with the same version configuration. Do not change the content under an accepted version. Required versions are server-controlled; installing a new app or signing in does not automatically renew consent.

Publish matching public web copies for the store listing/support/deletion site before release; the app currently reads the backend's public JSON documents, which is not a substitute for a store-required human-readable web privacy page. This task does not provision hosting or certify store/legal readiness. Review against [Google Play user-data requirements](https://support.google.com/googleplay/android-developer/answer/10144311) and the applicable [official Indian data-protection framework](https://www.meity.gov.in/documents/act-and-policies/digital-personal-data-protection-rules-2025-gDOxUjMtQWa?pageTitle=Digit).

## Backend and retention

Flyway V13 adds `app.user_policy_acceptance`. Rows identify the authenticated user and exact Terms/Privacy/content-rules version tuple, record an explicit true adult affirmation, and use backend Clock timestamps. No birth date, Firebase proof, IP address, user-agent or marketing preferences are stored in the consent record. The unique tuple makes a retry (even with a new idempotency key) return the original acceptance and timestamp.

`GET /public/policies` and `GET /public/legal/{terms|privacy}/{version}` are public no-store reads. `GET /me/policy-status` and `POST /me/policy-acceptances` require the verified JWT subject/session/installation and a live session. POST accepts only the version fields and the three explicit booleans; client user IDs/timestamps and unknown fields are rejected. A key is required but no response replay containing identity proof is created; tuple uniqueness supplies durable acceptance idempotence.

The acceptance transaction locks the account before writing. Room create/internal add-member and invitation join check current consent after the existing account lock and before room/membership/replay mutations. Even successful join replays cannot bypass current consent after a required version change. Public invitation preview and existing room reads/lifecycle/account cleanup are not gated. No upload contract is added because this app has no implemented upload flow; any future upload service must apply the same guard.

A new version leaves historical records intact while the account is active. Accepted account deletion purges these records with other personal rows; there is no new post-deletion legal-record retention exception. Existing immediate-deletion, external-key destruction and seven-day encrypted-backup rules remain unchanged.

## Validation

Backend tests cover new/existing/version-changed accounts, explicit choices, timestamp provenance, tuple/retry/concurrent acceptance, public documents, verified ownership/session security, forged user/time fields, create/internal join/public invitation join gates, join replays and account deletion cleanup. Flutter tests cover controller failure/retry and session fencing, unchecked/reset checkboxes, public legal reads/retry, invite return, route gates/expiry, and interactive demo/skip behavior. Production operator identity, legal review, publication and real-device accessibility/store review remain deployment responsibilities.
