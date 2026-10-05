# TAPS UI foundation

The UI shell is modelled on nr-lexis: 48px blue header, theme switch, profile panel, navigation
rail, page spacing and tables. It uses Carbon React, IBM Products detail drawers, Carbon icons and
pictograms, the B.C. Government logo and BC Sans. TanStack Router handles routing. Screens that
aren't built yet say so.

## Development preview

With Node 24:

```sh
cd frontend
npm ci --ignore-scripts
npm run dev:ui
```

Open <http://127.0.0.1:3000/ui-preview.html> and expand the synthetic-data banner for the session
and page selectors. The preview renders the real `Layout`, pages, `RequireAccess` and
`ThemeProvider` with an in-memory router and a fake `AuthContext`. It makes no API or OIDC calls.
Reloading resets it.

- **Sessions:** signed out, loading, error, no role, ECAS and GAS staff, ECAS only, and GAS
  client-report only (can open the GAS overview but not appraisal screens).
- **Synthetic reusable controls:** sample table with draft filters, loading/empty/error states and
  a detail drawer.
- **Synthetic ECAS and GAS workflow:** uses the backend contract fixture. ECAS rows include several
  marks and permits for one submission. **View related GAS worksheets** follows the fixture link.
  In GAS search, licence `X99998` shows the mark chooser and mark `ZZ9996` shows FTA information
  with no worksheets.

`ui-preview.html` is a dev-only Vite entry guarded by `import.meta.env.DEV`. Production code never
imports it and `npm run build` only uses `index.html`.

### Review checklist

1. Switch sessions and open ECAS and GAS routes as allowed, forbidden and no-role users.
2. Filters only apply on Search or Enter. Reset restores all rows.
3. Try each results state and the retry button.
4. Open a row drawer at wide and narrow widths. Check initial focus, Escape, Tab and that focus
   returns to the row button.
5. At narrow width, check the menu, wrapped page actions, table keyboard scrolling, and light and
   dark themes. With the drawer open, the header and page behind it must be inert.
6. In the workflow preview, check every ECAS mark/permit row, both reference methods, both GAS mark
   rows opening the same worksheet, and the FTA-only mark.

## Shared components

Page content renders inside the shell's `#main-content` landmark, so don't nest another `main`.
Components are in `frontend/src/components` (appraisal screens under `appraisal/`), except
`ThemeProvider` in `frontend/src/context/theme`.

| Component | Props and notes |
| --- | --- |
| `Layout` | `children`. Needs auth, theme and router contexts. Header, navigation, content landmark and footer. |
| `ThemeProvider` | `children`. Carbon `white` and `g100`. `useTheme()` gives `theme` and `toggleTheme`, saved as `taps.ui.theme`. |
| `PageHeader` | `title`; optional `subtitle`, `actions`, `backLink`. |
| `SearchFilters` | `children`, `onSearch`; optional `title`, `onReset`, `loading`, `disabled`. A form, so Enter submits. The caller owns draft and applied state. |
| `TableFrame` | `ariaLabel`, `children`; optional `busy`. Labelled scroll region, focusable only when it overflows. |
| `SearchResultsTableFrame` | `children`; optional `ariaLabel`, `loading`, `loadingDescription`, `totalItems`, `columnCount`, `error`, `onRetry`. Count, skeleton, empty or error state. `columnCount` must match the table. |
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

## Carbon versions

| Package | Version |
| --- | --- |
| `@carbon/react` | `1.108.0` |
| `@carbon/ibm-products` | `2.90.0` |
| `@carbon/icons-react` | `11.81.0` |
| `@carbon/pictograms-react` | `11.109.0` |
| `@carbon/ibm-products-styles` | `2.89.0`, via the IBM Products override |

The pin and override match nr-lexis. Styles load Carbon, then IBM Products SidePanel, then TAPS
overrides. The drawer depends on that order, so recheck it when upgrading either IBM package.

After changing shared components:

```sh
npm run typecheck
npm run lint
npm run format:check
npm run test:unit -- --run
npm run build
```
