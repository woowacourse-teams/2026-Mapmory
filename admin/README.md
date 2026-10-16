# Mapmory Admin

React + TypeScript web prototype for Mapmory operations.

```bash
cd admin
npm install
npm run dev
```

The dashboard summary and member list/detail are connected to the Java backend. Service feedback list, detail, filters, and status updates are connected to the production Java API. The admin UI uses the backend session login endpoints and sends the session cookie with API requests. Write requests first load a CSRF token and send it in the response-provided header.

The admin UI connects only to the production API at `https://api.map-mory.com/api/v1`. It checks `GET /admin/auth/me` on startup, logs in through `POST /admin/auth/login`, and logs out through `POST /admin/auth/logout`. The dashboard requests `GET /admin/dashboard` with `from` and `to` date parameters.

## Screens

- Dashboard: member, travel-record, and attached-photo totals; guest/Kakao/Google login distribution; selected-period counts with 1/7/30/45/60-day presets and custom dates (Java API)
- Members: production member search, provider filter, and paginated detail with travel-record/photo counts
- Service feedback: all five statuses, category/OS/KST date filters, paginated list, detail view, and internal status updates. Content search applies to the currently loaded page.

Feedback states and UI labels:

| UI label | API code |
| --- | --- |
| 미확인 | `UNCONFIRMED` |
| 반영 중 | `IN_PROGRESS` |
| 반영 완료 | `COMPLETED` |
| 보류 | `ON_HOLD` |
| 미반영 | `NOT_IMPLEMENTED` |

## Checks

`npm run build` checks types and builds the admin site. `npm test` runs feedback API/date regression tests with Node.js 22.6 or newer.
