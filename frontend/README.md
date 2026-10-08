# TAPS Frontend

React 19, TypeScript, Vite and TanStack Router with Carbon/IBM Products and BC Sans. Signed-out visitors get a login page; signed-in users get a shell with a 48px blue header, theme switch, profile panel and navigation rail. Use Carbon icons and pictograms unless Carbon has nothing suitable, and keep the official B.C. Government branding. Exact dependency versions and overrides are in [package.json](package.json).

## Running locally

Use the [root local development steps](../README.md#local-development) for Vite or Docker Compose. The default browser URL is `http://localhost:3000`.

## Configuration

Local Vite reads [.env.example](.env.example) values at dev/build time; restart Vite after changing `.env`. Deployed containers write `/config.js` at startup, so environment changes require a rollout rather than a new build. Never put database credentials or a client secret in frontend configuration.

| Variable | Default | Purpose |
| --- | --- | --- |
| `VITE_OIDC_ISSUER_URI` | None | BC Gov SSO issuer; must match the backend. |
| `VITE_OIDC_CLIENT_ID` | None | Public browser client ID; must match the backend. |
| `VITE_OIDC_IDIR_HINT` | `azureidir` | IDIR MFA provider alias. |
| `VITE_OIDC_BCEID_HINT` | `bceidbusiness` | Business BCeID provider alias. |
| `VITE_OIDC_SITEMINDER_LOGOUT_URL` | Test SiteMinder URL from `.env.example` | Ends the Business BCeID session before Keycloak logout. |
| `VITE_DEV_HOST` | `localhost` | Vite bind address. |
| `VITE_DEV_PORT` | `3000` | Vite port. |
| `VITE_DEV_BACKEND_TARGET` | `http://localhost:8080` | Vite `/api` proxy target. |

Register `<origin>/authCallback` and `<origin>` as the callback and post-logout URL. The [deployment guide](../docs/deployment-configuration.md#redirects-and-origins) covers shared-environment hosts.

## Testing

From `frontend`:

```sh
npm run typecheck
npm run lint
npm run format:check
npm run test:cov            # fails below 80% statements, 75% branches, 80% functions and lines
npm run build
```

`npm run test:security-config` checks the Caddy/Coraza config with synthetic requests. It needs a Coraza-enabled Caddy, such as the one `frontend/Dockerfile` builds; set `CADDY_BIN` if it isn't on the path.

## Local synthetic UI preview

With Node 24:

```sh
cd frontend
npm ci --ignore-scripts
npm run preview:local-synthetic
```

Open <http://127.0.0.1:3000/local-synthetic.html> and expand the **Local synthetic UI preview** banner for the session
and page selectors. The preview renders the real `Layout`, pages, `RequireAccess` and
`ThemeProvider` with an in-memory router and a fake `AuthContext`. It makes no API or OIDC calls.
Reloading resets it.

- **Sessions:** signed out (the login page, with the preview controls above it), loading, error,
  no role, ECAS and GAS staff, ECAS only, and GAS client-report only (can open the GAS overview but
  not appraisal screens).
- **Synthetic reusable controls:** sample table with draft filters, loading/empty/error states and
  a detail drawer.
- **Synthetic ECAS and GAS workflow:** uses the backend contract fixture. ECAS rows include several
  marks and permits for one submission. **View related GAS worksheets** follows the fixture link.
  In GAS search, licence `X99998` shows the mark chooser and mark `ZZ9996` shows FTA information
  with no worksheets.

The preview source lives in `src/local-synthetic/`, with `LocalSyntheticPreview.tsx` as its UI.
`local-synthetic.html` is guarded by Vite's built-in `import.meta.env.DEV` flag, which enables local
serving. OpenShift DEV uses the release application with its own DEV Oracle database and configured
SSO. All OpenShift environments use `npm run build` output from `index.html`, which excludes this
local synthetic preview. See the
[environment map](../README.md#environments).

### Review checklist

1. Switch sessions and open ECAS and GAS routes as allowed, forbidden and no-role users.
2. Filters only apply on Search or Enter. Clear all empties the filters and removes the results
   without searching.
3. Try each results state and the retry button.
4. Open a row drawer at wide and narrow widths. Check initial focus, Escape, Tab and that focus
   returns to the row button.
5. At narrow width, check the menu, wrapped page actions, table keyboard scrolling, and light and
   dark themes. With the drawer open, the header and page behind it must be inert.
6. In the workflow preview, check every ECAS mark/permit row, both reference methods, both GAS mark
   rows opening the same worksheet, and the FTA-only mark.

## UI standards

The team's UX/UI standards build on Carbon; anything they don't mention is Carbon as shipped.
TAPS applies them as follows:

- **Colour:** two blues replace Carbon's blue. Fill blue `#0073E6` sits behind white content (the
  header, primary buttons) in both themes. Text blue (`#005CB8` light, `#5CADFF` dark) is used for
  links, icons, focus, selection and tertiary buttons. Don't add other colours.
- **Header and navigation:** "TAPS **Timber Appraisal and Pricing System**", 12px after the menu
  toggle. The toggle names its next action ("Open menu"/"Close menu") on hover and focus. The side
  navigation opens expanded, collapses to an icon rail, remembers that choice and opens only from
  the toggle. Collapsed items have tooltips; a collapsed group holding the current page shows
  "Group: Page" and is marked current. Icons are text blue whether or not they're selected.
- **Typography:** field labels use `label-02`, field and read-only values `body-02`/`body-compact-02`,
  and DataTable keeps Carbon's sizes. Headings: page title `heading-05`, sections and card titles
  `heading-03`, sub-sections `heading-compact-02`. Inside a side panel, each level moves down one step.
- **Buttons:** always `size="md"` (a test checks this). A button with an icon keeps it at the right edge.
- **Search pages:** add `taps-fullbleed-page` to the page. Filters sit four to a row, with
  **Clear all** (tertiary) and **Search** (primary) at the right. Results fill the content width,
  with the count, table and pagination on one grey panel. Rows that open a panel use
  `taps-link-button`.
- **Return navigation:** pages reachable from the side navigation have no return link. A page one
  level below its parent shows "Back to <parent>" above the title, with an arrow icon, and returns
  to the parent with its filters intact. Deeper pages use a Carbon breadcrumb. Never use both.
- **Login page:** the government logo, the acronym as the only heading, the full name, one sentence,
  and log-in buttons that name their method. One decorative image sits at the right, and moves below
  the content on narrow screens.

## Shared components

Page content renders inside the shell's `#main-content` landmark, so don't nest another `main`.
Components are in `frontend/src/components` (appraisal screens under `appraisal/`), except
`ThemeProvider` in `frontend/src/context/theme`.

| Component | Props and notes |
| --- | --- |
| `Layout` | `children`. Needs auth, theme and router contexts. Header, navigation and content landmark; the `Landing` login page instead when signed out. |
| `ThemeProvider` | `children`. Carbon `white` and `g100`. `useTheme()` gives `theme` and `toggleTheme`, saved as `taps.ui.theme`. |
| `PageHeader` | `title`; optional `subtitle`, `status` (shown beside the title), `actions`. |
| `CardTitle` | `children`; optional `icon`, `id`. A card's `h2` in `heading-03` with a 24px icon. Put it in a `Tile` with `className="taps-card"`. |
| `SearchFilters` | `children`, `onSearch`; optional `title` (the form's accessible name), `onReset` (shows **Clear all**), `loading`, `disabled`. A form, so Enter submits. The caller owns draft and applied state. |
| `TableFrame` | `ariaLabel`, `children`; optional `busy`. Labelled scroll region, focusable only when it overflows. |
| `SearchResultsTableFrame` | `children`; optional `ariaLabel`, `loading`, `loadingDescription`, `totalItems`, `columnCount`, `error`, `onRetry`, `pagination`. Count, skeleton, empty or error state, with the pagination on the same panel. `columnCount` must match the table. |
| `AppNotification` | Carbon `kind`, `title`, `subtitle`, optional `role` (default `alert` for errors, else `status`). |
| `EmptyState` | `title`, `description`; optional `action`, `role="status"`. |
| `DetailSidePanel` | `open`, `title`, `children`, `onClose`, `launcherRef`, `initialFocusSelector`; optional `contentSelector`, `fallbackFocusSelector` (default `#main-content`). |

`DetailSidePanel` slides in at 1312px and wider and is an overlay below that. It focuses the
initial target on open and returns focus to the launcher (or the fallback) on close. It renders in
a body portal. In overlay mode it makes the rest of the page inert and restores it on close, resize
or unmount.

Store the clicked button before opening:

```tsx
const launcherRef = useRef<HTMLElement | null>(null)
const [open, setOpen] = useState(false)

<Button
  onClick={(event) => {
    launcherRef.current = event.currentTarget
    setOpen(true)
  }}
>
  Open details
</Button>

<DetailSidePanel
  open={open}
  title="Details"
  launcherRef={launcherRef}
  initialFocusSelector="#detail-heading"
  onClose={() => setOpen(false)}
>
  <h2 id="detail-heading" tabIndex={-1}>Details</h2>
  {/* fields */}
</DetailSidePanel>
```

## Dependencies and validation

Carbon versions and the IBM Products styles override are pinned in [package.json](package.json). Styles load Carbon, then IBM Products SidePanel, then TAPS overrides; recheck that order when upgrading either IBM package.

After shared-component changes, run the [frontend checks](#testing) and the preview review checklist above.

## Coast appraisal date draft

Users with `ECAS_SUBMISSION_EDIT` see an appraisal-dates section on the Coast reference.
**Check dates** runs the rules below and **Reset draft** restores the stored values. Nothing is
saved, and leaving or reloading the record discards the draft.

Category and revision are read-only. Invalid input is
kept, the first invalid field gets focus, and the inputs stay inside the mobile drawer's focus trap.

### Field rules

| Field | Rule |
| --- | --- |
| Effective and expiry | Valid `yyyy-MM-dd` dates, no UTC conversion. Blank stays blank. |
| Effective | On or after 2002-04-01. Required for reappraisal category `R`. |
| Expiry | Optional. Needs an effective date and must be on or after it. |

Code: [validator](src/contracts/coast-reference-draft.ts) and
[component](src/components/appraisal/CoastAppraisalDatesDraft.tsx).

### Why it doesn't save

Legacy Save Dates does more than update two fields. It checks the dates against FTA and other
appraisals for the mark, warns about overlaps, refreshes FTA defaults, can update cutting-authority
rows, sets audit context, checks the revision and depends on workflow state.

Writes like this stay in the legacy stored procedure, which TAPS doesn't call yet. A save path will
need the user's identity, an edit capability and state check, record scope, revision conflict
handling and audit context.

Interior dates also depend on appraisal-manual data and policy dates, so don't reuse these rules
for Interior.
