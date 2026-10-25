# Mapmory report MVP

Static report layout preview in the existing Firebase project `mapmory-analytics-b6a50`, on dedicated Hosting site `mapmory-reports-b6a50`.

All activity data in `public/app.js` is fictional and labeled as example data. Analytics is unconnected and displays unavailable values, never zero. No credentials or actual user data are included. This is a public layout prototype, not an authenticated report service. The existing chat automation does not publish to this website.

Supported interactions: date selection, team member filter, section navigation, responsive mobile layout.

Next: authentication and authorization before adding private reports, approved data collection integration, timestamped report storage and scheduled publishing.

Deploy only this Hosting site using the Firebase MCP from this directory, or `npx -y firebase-tools@latest deploy --only hosting --project mapmory-analytics-b6a50`.

## Draft review scope

This is the pre-existing static mockup isolated from the mobile working tree. Its authorship and continued use have not been established. It is separate from the later admin reports application; decide whether to keep, integrate or retire it during review. No deployment is performed by this PR.
