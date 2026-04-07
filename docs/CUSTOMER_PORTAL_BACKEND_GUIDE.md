# Customer Portal — Backend Implementation Guide

This document describes what the **customer-portal** backend does today, in language that works for **non-technical stakeholders**, **frontend developers**, and **backend engineers**.  
Base URL in local development is typically: `http://localhost:8080`.

---

## 1. Plain-language summary (for everyone)

### What is this system?

A **web API** (JSON over HTTP) that:

1. Lets **customers** and **internal 3SC staff** **register**, **log in**, and receive a **secure token** for later requests.
2. Stores **organizations** (companies), **users**, **teams called “pods”** (e.g. EDM, DPAI), and **support issues** that are usually **linked to Jira tickets**.
3. Connects to **Jira Cloud** (server-side, using configured credentials) to **import** or **refresh** issue data. Manual **sync-from-Jira** can refresh a full row; optional **background sync** only updates **progress** fields (status, etc.) so portal-filled metadata is not wiped.
4. **Restricts who sees what**: not every user sees every issue — rules depend on **role**, **organization**, **pod**, and whether the user is the **Jira reporter** on a ticket.
5. Exposes **dashboard / analytics APIs** (aggregates + slicers) that respect the **same visibility** as issue list, support **cross-filtering** (Power BI–style), and allow **per-chart access control** to be plugged in later.

### Main actors (roles)

| Role | Who they are (conceptually) |
|------|-----------------------------|
| **SC_ADMIN** | Full internal admin: manage users, pods, see all issues. |
| **SC_LEAD** | Internal lead; **SC_ADMIN** assigns **one or more pods**. They see issues whose **pod** is in that assigned set (no org/pod on the user otherwise, same as admin). |
| **SC_AGENT** | Internal agent; **visibility** is still by **portal reporter** or **Jira reporter email** match. **Pod assignments** (one or more) control which pods they may set on issues and how the pod field is resolved on import when Jira does not map cleanly. |
| **CUSTOMER_ADMIN** | Customer org admin; sees all issues for **their organization**. |
| **CUSTOMER_USER** | End customer user; sees issues in **their org** if they **created/imported** the row, are **assignee**, or their **email matches Jira reporter** on that issue. |

New users who use **public registration** get **CUSTOMER_USER** by default. Internal roles are assigned by an **SC_ADMIN** via the admin API.

---

## 2. Technology stack (technical)

- **Java 21**, **Spring Boot 4**, **Maven**
- **PostgreSQL** (JPA/Hibernate; schema can auto-update in dev via `spring.jpa.hibernate.ddl-auto`)
- **JWT** (Bearer token) for API authentication — **not** Jira tokens in the browser
- **Spring Security** + OAuth2 Resource Server style JWT validation
- **Jira Cloud REST API v3** for import/sync (Basic auth: Atlassian account email + **API token**)

---

## 3. Authentication (for frontend)

### How it works

1. **Login** or **register** returns (login) or follows (register) user creation.
2. **Login** response includes `accessToken`, `tokenType` (`Bearer`), `expiresInSeconds`.
3. For **all protected APIs**, send:

```http
Authorization: Bearer <accessToken>
```

Token lifetime is configured by `jwt.expiration-seconds` (e.g. `172800` = 48 hours in default `application.properties`). After expiry, call **login** again (there is no refresh-token flow in this project today).

### CORS

Browser apps on `http://localhost:*` and `http://127.0.0.1:*` are allowed by server CORS settings. Adjust in production as needed.

---

## 4. API reference (for frontend)

All paths below are under the server root unless noted.  
**Request/response bodies are JSON.**  
Fields marked *optional* can be omitted from JSON; omitting means “no change” for PATCH-style semantics where applicable.

### 4.1 Public / discovery

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/` | No | Short JSON map of useful routes (not a HTML UI). |
| GET | `/actuator/health` | No | Health check for ops/monitoring. |

---

### 4.2 Auth — `/api/auth`

#### POST `/api/auth/register` — create account (customer or internal self-signup)

**Auth:** not required.

**Body:**

```json
{
  "email": "user@example.com",
  "password": "min8chars",
  "displayName": "Full Name",
  "organizationName": "Jockey"
}
```

For **internal 3SC users**, omit `organizationName` or send `null` / `""` — no organization is linked until an admin assigns one via the admin API if needed.

**Validation (high level):**

- `email`: required, valid email
- `password`: required, 8–128 characters
- `displayName`: required, max 200 chars
- `organizationName`: optional; if present and non-blank, max 200 chars — find-or-create **Organization** by name (case-insensitive)

**Response:** `201 Created` — `UserResponse` (see §4.6).

**Note:** Default role is **CUSTOMER_USER**. Customers should set **organizationName** to their company so issue visibility matches Jira **Customer** values.

---

#### POST `/api/auth/login`

**Auth:** not required.

**Body:**

```json
{
  "email": "user@example.com",
  "password": "secret"
}
```

**Response:** `200 OK`

```json
{
  "accessToken": "<jwt>",
  "tokenType": "Bearer",
  "expiresInSeconds": 172800
}
```

---

#### GET `/api/auth/me`

**Auth:** required (Bearer).

**Response:** `200 OK` — current user as `UserResponse`.

---

### 4.3 Issues — `/api/issues`

**Auth:** required for all operations below.

#### GET `/api/issues`

Returns a **filtered list** of issues the current user is allowed to see (not necessarily all issues in the database).

**Response:** `200 OK` — JSON array of `IssueResponse` (see §4.8).

---

#### POST `/api/issues/import-jira`

Pulls one issue from **Jira** using server config (`jira.base-url`, `jira.email`, `jira.api-token`) and **creates or updates** the portal row by **Jira key**.

**Body:**

```json
{
  "jiraKey": "EDM-3617"
}
```

**Alias:** `jiraKeyOrUrl` is accepted (e.g. pasted URL); server extracts the key.

**Deprecated:** `organizationId` in body is **ignored** for internal import — organization comes from Jira **Customer** field (or default internal org **3SC**). Customer users importing always attach to **their** organization.

**Response:** `200 OK` — single `IssueResponse`.

**Who may call:** SC_ADMIN, SC_LEAD, SC_AGENT, CUSTOMER_ADMIN, CUSTOMER_USER (subject to product rules in code).

---

#### POST `/api/issues/{id}/sync-from-jira`

Same data refresh as re-import, but uses the **portal issue UUID** and the stored **`jiraIssueKey`** (no key in body).

**Path:** `id` = issue UUID.

**Body:** none.

**Response:** `200 OK` — `IssueResponse`.

**Who may call:** same import roles **and** user must already be allowed to **view** that issue.

---

#### PATCH `/api/issues/{id}`

Gap-fill update only: the body supports **these optional fields** and nothing else. Each field is applied **only if** the issue still has an **empty** value there, so you do not overwrite data that already came from Jira (or a prior edit). Omitted keys are left unchanged.

```json
{
  "closingDate": "2026-04-30",
  "module": "EDM",
  "environment": "Prod",
  "category": "Bug",
  "severity": 2,
  "rcaDescription": "Root cause text",
  "organizationName": "Jockey",
  "podName": "EDM"
}
```

**Semantics (empty = null or blank string on the issue today):**

- `closingDate` — set only if **`closingDate` is still null**.
- `module`, `environment`, `category`, `rcaDescription` — set only if that field is still empty; **empty string** in JSON clears the slot when it was still empty.
- `severity` — **1** = high, **2** = moderate, **3** = low; applied only if **`severity` is still null**.
- `organizationName` — trimmed, case-insensitive; find-or-create organization by name. Applied only if the issue has **no organization** or it is still the internal default **3SC** (typical when Jira had no Customer). If the issue is already tied to a real customer org, this field is **ignored**.
- `podName` — trimmed, case-insensitive pod name; applied only if the issue **has no pod yet**. **Only** SC_ADMIN, SC_LEAD, SC_AGENT may set pod (same pod-assignment rules as before). Ignored if a pod is already set.

To **re-align the whole issue with Jira** (status, description, assignee, etc.), use **`POST /api/issues/{id}/sync-from-jira`** (see §4.3), not this PATCH.

**Who may patch (summary):**

- **SC_ADMIN:** all issues.
- **SC_LEAD:** issues they can view (issue **pod** must be in their **assigned pods**).
- **SC_AGENT:** issues they can view (reporter match / email match).
- **CUSTOMER_ADMIN:** issues in their organization.
- **CUSTOMER_USER:** generally **cannot** PATCH (view-only paths for reporter matching).

---

### 4.4 Admin — users `/api/admin/users`

**Auth:** Bearer + **SC_ADMIN** only (`hasRole("SC_ADMIN")`).

#### GET `/api/admin/users`

**Response:** `200 OK` — array of `UserResponse`.

---

#### PATCH `/api/admin/users/{id}`

**Path:** `id` = user UUID.

**Body:**

```json
{
  "roles": ["SC_AGENT"],
  "organizationName": "Acme Corp",
  "podNames": ["Delivery Pod 1", "EDM"],
  "enabled": true
}
```

(UUIDs still work — see rules below.)

**Rules:**

- `roles`: **required** (non-empty set). Valid values: `SC_ADMIN`, `SC_LEAD`, `SC_AGENT`, `CUSTOMER_ADMIN`, `CUSTOMER_USER`.
- **Organization** (optional; **omit both** `organizationId` and `organizationName` to leave unchanged):
  - `organizationId`: if set, that organization is applied (takes precedence over `organizationName`).
  - `organizationName`: trimmed, **case-insensitive** lookup; **`""`** (empty string) **clears** organization.
- **Pods** (for **SC_LEAD** / **SC_AGENT**; others usually have none):
  - `podNames`: if present (`null` = don’t change), list of pod **names** (trimmed, case-insensitive). **`[]`** clears all; non-empty **replaces** the set. When both `podNames` and `podIds` are sent, **`podNames` wins**.
  - `podIds`: same semantics as `podNames` but by UUID, used only when `podNames` is omitted or **`null`**.
- `enabled`: optional; if omitted, enabled flag unchanged.

**Response:** `200 OK` — `UserResponse`.

---

### 4.5 Admin — pods `/api/admin/pods`

**Auth:** Bearer + **SC_ADMIN** only.

#### GET `/api/admin/pods`

**Response:** `200 OK` — array of `PodResponse`.

---

#### POST `/api/admin/pods`

**Body:**

```json
{
  "name": "EDM"
}
```

**Response:** `201 Created` — `PodResponse` `{ "id": "uuid", "name": "EDM" }`.

Pod **names** should align with Jira when using pod mapping (optional `jira.pod-field-id` or module name match).

---

### 4.6 JSON types — `UserResponse`

```json
{
  "id": "uuid",
  "email": "user@example.com",
  "displayName": "Name",
  "enabled": true,
  "organizationId": "uuid",
  "organizationName": "Jockey",
  "pods": [
    { "id": "uuid", "name": "Delivery Pod 1" },
    { "id": "uuid", "name": "EDM" }
  ],
  "roles": ["CUSTOMER_USER"]
}
```

`organizationId` / `organizationName` may be `null` if not set. `pods` is an array (often **empty** for customers and **SC_ADMIN**); each entry has `id` and `name`.

**JWT (optional):** when the user has at least one assigned pod, the access token may include a **`podIds`** claim (array of UUID strings) for clients that need it; otherwise the claim is omitted.

---

### 4.7 JSON types — `IssueResponse`

```json
{
  "id": "uuid",
  "jiraIssueKey": "EDM-3617",
  "jiraIssueId": "12345",
  "title": "Summary from Jira",
  "description": "Plain or extracted text",
  "issueDate": "2026-04-01",
  "closingDate": null,
  "module": "Component or custom field",
  "environment": "Prod",
  "category": "Bug",
  "severity": 2,
  "rcaDescription": null,
  "jiraStatus": "In Progress",
  "portalStatus": "IN_PROGRESS",
  "organizationId": "uuid",
  "organizationName": "Jockey",
  "podId": "uuid",
  "podName": "EDM",
  "assigneeId": null,
  "assigneeEmail": null,
  "portalReporterId": null,
  "portalReporterEmail": null,
  "jiraReporterEmail": "reporter@company.com",
  "jiraReporterDisplayName": "Reporter Name",
  "importedById": "uuid",
  "importedByEmail": "admin@3sc.local",
  "lastSyncedAt": "2026-04-04T12:00:00Z"
}
```

- Dates are ISO-8601 `LocalDate` (`YYYY-MM-DD`) where used.
- `severity`: `1` = high, `2` = moderate, `3` = low (derived from Jira priority).
- `lastSyncedAt`: instant of last successful Jira-backed write to that row.

---

### 4.8 Enum — `IssueStatus` (`portalStatus`)

Allowed strings in JSON:

`OPEN`, `ACKNOWLEDGED`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`

---

### 4.9 Analytics dashboard — `/api/dashboard`

**Auth:** Bearer required for all routes below.  
**Visibility:** Every aggregate counts only **issues the current user is allowed to see** (same rules as `GET /api/issues`).  
**Performance:** Queries use `COUNT(DISTINCT issue.id)` with **DB indexes** on `organization_id`, `issue_date`, `environment`, `module`, `category`, `severity`, `assignee_id`, `jira_issue_key`, `pod_id`. For very large datasets, consider read replicas or materialized views later.

#### Cross-filtering (frontend pattern — Power BI style)

1. Call **`GET /api/dashboard/filters`** with the **current** query string whenever **any** slicer changes **or** after **chart drill** (bar/segment click). Every slicer list is computed using **all other** active filters, so e.g. selecting a client narrows months, environments, modules, etc.
2. Call each **chart** and **`/aggregate`** with the **identical** query string (same params as step 1).
3. On chart drill, append the bar’s dimension to the query string, then **refetch `/filters` and every chart** with that full string.  
   - Use **`key`** from chart points (UUID for client, `yyyy-MM` for month, `__BLANK__` for empty env/category, severity as `1`/`2`/`3`, etc.).

**Slicer behaviour:** For each dropdown, the backend **omits only that facet’s filter** when building its options, but applies **every other** filter (including chart-driven ones). So all lists stay consistent with the current slice of data.

#### Shared query parameters (all dashboard GETs)

Repeat a parameter for **multi-select** (OR within that dimension), e.g. `organizationId=a&organizationId=b`.

| Query param | Meaning |
|-------------|---------|
| `organizationId` | Client (portal organization UUID). Repeat for several. |
| `assigneeId` | **Delivery SPOC** (`assignee`). Repeat for several. |
| `severity` | `1`, `2`, or `3`. Repeat for several. |
| `environment` | Exact string, or `__BLANK__`. Repeat for several. |
| `month` | `YYYY-MM` — **`issueDate`** in that month. Repeat for several months (OR). |
| `rca` | `HAS`, `EMPTY`, `NO` (repeat to OR; omit/`ALL` → no RCA filter). |
| `category` | Category or `__BLANK__`. Repeat for several. |
| `module` | Module or `__BLANK__`. Repeat for several. |
| `jiraKey` | Exact Jira key. Repeat for several. |
| `portalStatus` | `OPEN`, `ACKNOWLEDGED`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`. Repeat for several. |

---

#### GET `/api/dashboard/meta`

**Response (shape):**

- `charts`: list of `{ id, pathSegment, title, description }` for each built-in chart (`ISSUES_BY_MONTH`, …).
- `allowedChartIds`: enum names the user may call (today: all charts via default policy — see **Per-chart access** below).

Use this to render tiles and to hide disabled charts in the future.

---

#### GET `/api/dashboard/filters`

Returns distinct values for slicers under current cross-filters (with per-facet omission as above).

**Response fields:**

| Field | Content |
|-------|---------|
| `clients` | `{ id, name }[]` organizations |
| `deliverySpocs` | `{ id, email, displayName }[]` from **assignees** present on visible issues |
| `severities` | `[1,2,3]` subset that exists |
| `environments` | strings + `__BLANK__` if empty env exists |
| `months` | `YYYY-MM` from `issueDate` |
| `rcaOptions` | `["ALL","HAS","EMPTY","NO"]` (static hints) |
| `ticketCategories` | distinct `category` (+ optional `__BLANK__`) |
| `modules` | distinct `module` (+ optional `__BLANK__`) |
| `jiraTickets` | up to **200** `{ jiraKey, title }` (distinct) |
| `issueStatuses` | Distinct portal statuses present under current filters (`OPEN`, …) |

---

#### GET `/api/dashboard/charts/{chartPath}`

`chartPath` is the URL segment, e.g.:

| Path segment | Chart |
|--------------|--------|
| `issues-by-month` | Trend by month of `issueDate` |
| `issues-by-client` | By organization (Jira Customer → org) |
| `issues-by-environment` | By `environment` |
| `issues-by-severity` | By `severity` (labelled High / Moderate / Minor) |
| `issues-by-module` | By `module` |
| `issues-by-ticket-category` | By `category` |
| `issues-by-rca` | Buckets **Yes** / **No** / **Blank** (from RCA text rules) |

**Response:**

```json
{
  "chartId": "ISSUES_BY_CLIENT",
  "points": [
    { "key": "uuid-of-org", "label": "Jockey", "count": 93 }
  ]
}
```

- **`key`**: append to the shared query string when drilling — e.g. client → `organizationId=key`; month → `month=key`; severity → `severity=key`; environment blank → `environment=__BLANK__`; RCA bucket → `rca=HAS` / `rca=NO` / `rca=EMPTY`; if you add a chart by status later, use `portalStatus=key`.

---

#### GET `/api/dashboard/aggregate?groupBy=...`

Same data as the matching chart, for **decomposition tree** / arbitrary drill UI.

**`groupBy`** (case-insensitive): `MONTH`, `CLIENT`, `ENVIRONMENT`, `SEVERITY`, `MODULE`, `CATEGORY`, `RCA`.

Example drill: `groupBy=CLIENT` → set `organizationId` from the bar’s **key** → `groupBy=MODULE` → set `module=DPAI` from the next bar’s **key** → continue with `groupBy=ENVIRONMENT`, etc. All selected dimensions are passed as the shared query params above.

---

#### Per-chart access (extensibility)

- Interface `DashboardChartAccessPolicy` with default bean `PermissiveDashboardChartAccessPolicy` (**all charts allowed**).
- Replace with your own `@Primary` bean (e.g. load entitlements from DB) to restrict `allowedChartIds` and to throw **403** from `requireChart`.
- Chart-specific calls check the chart id; **`/aggregate`** checks `AGGREGATE` in the same policy.

---

#### End-to-end flow (non-technical)

1. User opens the dashboard → UI loads **`/meta`** and **`/filters`** (no slicers yet).  
2. UI draws empty slicers and fetches each **`/charts/...`** without extra params.  
3. User picks **Month = 2025-11** → UI stores that and refetches **all** charts and **filters** with `month=2025-11`.  
4. User clicks **Jockey** in the client chart → UI sets `organizationId` to that bar’s **key** and refetches everything again.  
5. Only issues this user may see are ever counted, so customers never see another client’s totals.

---

## 5. Business rules (who sees what)

This matches the implemented visibility rules (`IssueVisibilitySpecification` + `IssueService`):

| Role | Visibility rule (simplified) |
|------|------------------------------|
| **SC_ADMIN** | All issues. |
| **CUSTOMER_ADMIN** | All issues whose **organization** equals the admin’s organization. |
| **CUSTOMER_USER** | Issues in **same organization** AND (**created the import row** OR **assignee** OR **email matches `jiraReporterEmail`**). |
| **SC_LEAD** | Issues whose **pod** is **in** the lead’s **assigned pods** (must have at least one pod to see issues via this rule). |
| **SC_AGENT** | Issues where **`portalReporter`** is that user **OR** **`jiraReporterEmail`** matches login email (case-insensitive). Pod assignments do **not** further filter the list; they apply to **pod field** behaviour on import/patch. |

**Important for demos:** Customer registration must use an **organization name** that matches the Jira **Customer** value for that ticket (e.g. **Jockey**), otherwise the customer user will not pass the “same organization” check.

---

## 6. Jira integration (backend / DevOps)

### Server-side only

Jira credentials in `application.properties` (or environment) are used **only on the server** when calling Jira. End users never send Jira tokens from the browser for these calls.

### Recommended configuration

- `jira.base-url` — site root, e.g. `https://your-domain.atlassian.net`
- `jira.email` — Atlassian account email that owns the API token
- `jira.api-token` — prefer **environment variable** `JIRA_API_TOKEN` in real deployments; **do not commit** real tokens

### Auto-detected Jira fields (no custom field id needed)

- **Customer** — display name `Customer` → maps to portal **Organization** (find-or-create by name) for internal imports; default org **3SC** if empty.
- **Env** / **Environment** — auto-detected for **`Issue.environment`**; optional override `jira.environment-field-id`.

### Optional property overrides (custom field ids)

- `jira.module-field-id` — else module may fall back to Jira **components**
- `jira.environment-field-id` — overrides Env auto-detect
- `jira.category-field-id` — else issue type / labels
- `jira.pod-field-id` — value should match a portal **Pod** `name`
- `jira.rca-field-id` — when set, RCA in portal **mirrors** Jira (empty in Jira clears portal RCA on sync)

### Background sync (optional)

- `jira.background-sync-enabled` — `true` to run a scheduled job
- `jira.background-sync-cron` — Spring 6-field cron (e.g. `0 */15 * * * *` every 15 minutes for testing; use `0 0 */4 * * *` for every 4 hours in production). If unset, the job’s code default is every 4 hours.

When enabled, the job re-fetches **all** issues that have a `jiraIssueKey` and updates **only progress-oriented** fields from Jira: **status** (and derived portal status), **summary** (if non-empty in Jira), **resolution / closing date** (only when Jira has a resolution date), **severity** (only when Jira has a priority name), plus **`lastSyncedAt`** / snapshot. It does **not** overwrite **organization**, **pod**, **environment**, **module**, **category**, **RCA**, **reporter**, **assignee**, **description**, **issue date**, etc., so values you fixed in the portal are not cleared when Jira leaves those fields empty. Does not change **who imported** the issue (`createdBy`). For a **full** row refresh, use **`POST /api/issues/{id}/sync-from-jira`** (or import by key again).

### Atlassian API tokens

API tokens from id.atlassian.com are **long-lived** until revoked. They are **not** the same as the portal JWT (which expires per `jwt.expiration-seconds`).

---

## 7. Bootstrap / demo users (first run)

When the database has **no users** yet (non-test profile), seed data may create sample users (see `DataSeed` and docs in `application.properties` comments). Typical examples mentioned in properties:

- Internal admin / lead / customer admin accounts with documented **temporary passwords** for **local development only**.

Change all passwords before any real deployment.

---

## 8. Error handling (frontend hint)

Validation and business errors are returned as JSON (see `ApiExceptionHandler`). Common cases:

- `401` / `403` — missing/invalid token or insufficient role
- `400` — validation or bad request (e.g. illegal Jira key)
- `503` — Jira not configured when calling import

Exact body shape: refer to `ApiError` in the codebase.

---

## 9. File map (for backend devs)

| Area | Main packages / classes |
|------|-------------------------|
| REST controllers | `com.scai.customer_portal.api.*` |
| DTOs | `com.scai.customer_portal.api.dto.*`, `api.dto.dashboard.*` |
| Security | `com.scai.customer_portal.security.*` |
| Jira client & field discovery | `JiraRemoteService`, `JiraIssueKeyParser` |
| Mapping Jira JSON → Issue | `JiraIssueMapper`, `JiraFieldValueTexts`, `JiraDateParsing` |
| Issue visibility (list + dashboard) | `IssueVisibilitySpecification`, `IssueService`, `DashboardCrossFilterSpecification` |
| Dashboard aggregates | `DashboardService`, `DashboardController` |
| Chart ACL (pluggable) | `DashboardChartAccessPolicy`, `PermissiveDashboardChartAccessPolicy` |
| Issue rules / sync | `IssueService`, `JiraIssueBackgroundSyncJob` |
| Admin | `AdminUserService`, `AdminPodService` |
| Config | `JiraProperties`, `JwtProperties`, `SecurityConfig`, `DataSeed`, `DefaultInternalOrganizationBootstrap` |

---

## 10. Document history

- Written to reflect the **Hackathon / customer-portal** backend as implemented in-repo (auth, roles, issues, Jira import/sync, admin APIs, visibility rules).

For questions or changes, update this file when behavior changes so frontend and stakeholders stay aligned.
