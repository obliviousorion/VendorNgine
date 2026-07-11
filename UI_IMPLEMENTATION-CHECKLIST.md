# Vendor Engine — Swing Implementation Checklist

Use this alongside `vendor-engine-ui-refresh.html` and `DESIGN-NOTES.md`. It breaks the
redesign into phases that match your existing package structure, so Antigravity gets
one scoped, checkable task at a time instead of "restyle everything."

## How to use this with Antigravity

- **Feed it one phase at a time**, as separate tasks/turns — not the whole list in one
  prompt. Scoped diffs are easier to review, and a mistake in Phase 2 doesn't bleed
  into Phase 3.
- **Before it writes code for a phase**, ask it to restate its plan — which classes
  it's adding or editing — and check that against the bullet list for that phase.
  Catching a missing item before code is written is much cheaper than after.
- **After each phase, run the app yourself**: `mvn clean package && java -jar
  target/vendor-engine-1.0.jar`. Check the boxes below against what's on screen, not
  just against the diff — Swing layout sometimes reads differently live than in code.
- **Don't skip Phase 5.** It's the one that confirms the redesign stayed a view-layer
  change and didn't quietly touch `model/` or `controller/`.

---

## Phase 0 — Foundation: `UiTheme`

- [ ] Add `FRESH`, `WARM`, `HOT` color constants alongside the existing `OK`/`WARN`
- [ ] Add `UiTheme.urgencyColor(long elapsedMs, long maxMs)` returning FRESH below 50%
      of max, WARM below 90%, HOT above — used by both the ring painter and the
      Admin sidebar's status dots, so the scale is defined once
- [ ] Add a monospace font constant (`new Font("Monospaced", Font.PLAIN, 12)` or
      similar) for order IDs, elapsed timers, and currency — everything numeric
- [ ] Switch currency rendering from `$` to `₹` wherever revenue is formatted

**Acceptance:** `UiTheme` compiles with these additions; no view touched yet.

---

## Phase 1 — `LoginView`

- [ ] Replace the role `JComboBox` with two selectable tiles (Kitchen Worker /
      Merchant Admin) — plain `JPanel`s with a `MouseListener` toggling a
      selected-state border/background is enough, no new library needed
- [ ] Stall picker only appears once "Kitchen Worker" is selected
- [ ] Stall picker lists `stallName (stallId)`, pulled from `stalls.json` /
      `StallDataSource` — not the bare ID
- [ ] "Enter Dashboard" stays disabled until a valid role (+ stall, if kitchen) is chosen
- [ ] Small status line reflecting `NetworkMonitorDaemon`'s current online state

**Acceptance:** interaction matches the Login tab in the HTML reference — selecting a
tile swaps the highlight, the stall list appears/disappears, the button's enabled
state tracks a valid selection.

---

## Phase 2 — `KitchenView` (largest phase — consider splitting further)

**2a. Board layout**
- [ ] Replace the single `JTable` with a three-column layout (Pending / Accepted /
      Ready), e.g. `GridLayout(1,3)` of panels, each a vertical `Box` of order cards
- [ ] `OrderTableModel`'s role shifts from "table rows" to "which column each order
      belongs to" — on `onOrderUpdated`, move the card to the right column instead
      of just firing `fireTableDataChanged()`

**2b. Order card component**
- [ ] New `OrderCardPanel`: renders order ID, item summary, a VIP ribbon when
      `PriorityToken.VIP`, the docket ring, and one contextual action button
- [ ] New `DocketRingComponent`: custom `JComponent`, `paintComponent` draws an arc
      via `Graphics2D`/`Arc2D.Double` sized by `Order.getElapsedMs()` against a
      max threshold, colored via `UiTheme.urgencyColor`
- [ ] New `TicketCardBorder` (`AbstractBorder`): paints the two punch-notch circles
      and the dashed divider under the header
- [ ] Action button label/behavior derived from `OrderStatus`: `PENDING` → "Accept
      order", `ACCEPTED` → "Mark ready", `READY` → "Serve order" (+ a small
      text-link-style Cancel, demoted since it's the rare/destructive path)

**2c. Completed strip**
- [ ] Collapsible panel below the board listing `SERVED`/`CANCELLED` orders,
      collapsed by default

**2d. Offline banner**
- [ ] Restyle the existing offline indicator per the fresh/warm/hot scale (hot/red),
      bound to `AppState.online`, matching the HTML reference's banner copy

**Acceptance:** run the app, let `PeakHourSimulator` push orders. Cards land in
Pending, move to Accepted/Ready/gone as you click the button on each card, the ring
visibly fills and changes color as time passes, VIP orders show the ribbon.

---

## Phase 3 — `AdminView`

- [ ] Sidebar `JList` gets a custom cell renderer: status dot (fresh/warm/hot by
      queue-depth thresholds you define) + stall name + ID + queue-depth badge
- [ ] Selected-stall detail panel shows that stall's live mini order list instead of
      four static labels in mostly empty space
- [ ] Revenue chart keeps the `Graphics2D` custom-paint approach already planned;
      add a gridline, a pulsing "latest point" dot, and `₹` formatting
- [ ] Add a revenue-by-stall ranking (horizontal bars) below/beside the chart,
      sourced from `StallDataSource`
- [ ] Metric cards (Active Stalls / Total Queue Depth / Total Revenue / Network
      Status) restyled with an icon, a monospace numeral, and a small delta/status
      caption

**Acceptance:** clicking a different stall in the sidebar updates the detail panel;
metric cards and chart reflect live `StallDataSource` aggregates.

---

## Phase 4 — Logout dialog

- [ ] Replace `JOptionPane.showConfirmDialog` with a themed `JDialog` (custom content
      pane, or `setUndecorated(true)` with your own title bar) matching `UiTheme`

**Acceptance:** the dialog no longer shows native light-grey OS chrome against the
dark app — this is the exact inconsistency visible in your original screenshots.

---

## Phase 5 — QA pass (don't skip)

- [ ] Side-by-side compare each Swing view against its matching tab in
      `vendor-engine-ui-refresh.html`
- [ ] Confirm `model/` is still untouched: `grep -r "javax.swing" model/` returns
      nothing, per your own architecture doc's rule
- [ ] Confirm EDT discipline is unchanged: no new direct Swing calls from consumer/
      daemon threads — everything still funnels through
      `OrderController.notifyObservers` → `invokeLater`
- [ ] Ask Antigravity to include the `grep` check above in its own final response as
      a self-check, not just something you run afterward
