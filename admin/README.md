# Mapmory Admin

React + TypeScript web prototype for Mapmory operations.

```bash
cd admin
npm install
npm run dev
```

The dashboard summary is connected to the Java backend. Member management and service feedback still use local demo data. The admin UI uses the backend session login endpoints and sends the session cookie with API requests. Write requests first load a CSRF token and send it in the response-provided header.

The admin UI connects only to the production API at `https://api.map-mory.com/api/v1`. It checks `GET /admin/auth/me` on startup, logs in through `POST /admin/auth/login`, and logs out through `POST /admin/auth/logout`. The dashboard requests `GET /admin/dashboard` with `from` and `to` date parameters.

## Screens

- Dashboard: member, travel-record, and attached-photo totals; guest/Kakao/Google login distribution; selected-period counts (Java API)
- Members: search, provider filter, and member detail drawer
- Service feedback: status filter and internal status updates

Feedback states and UI labels:

| UI label | API code |
| --- | --- |
| 미확인 | `UNCONFIRMED` |
| 반영 중 | `IN_PROGRESS` |
| 반영 완료 | `COMPLETED` |
| 보류 | `ON_HOLD` |
| 미반영 | `NOT_IMPLEMENTED` |
