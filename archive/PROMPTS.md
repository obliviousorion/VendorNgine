# Vendor Engine — Prompts for Antigravity

Paste the **Kickoff prompt** once, at the start of the session. Then paste each phase
prompt **in order**, one at a time — wait for the response, run the app, check it
against the phase's acceptance criteria in `IMPLEMENTATION-CHECKLIST.md`, and only
then move to the next one. Don't paste two phases in the same message; that's the
easiest way for details to get dropped.

Every prompt ends the same way on purpose: a forced self-check against the checklist,
and an explicit stop instruction. Agentic tools left open-ended tend to either
gold-plate (touch files nobody asked about) or drift into the next phase early — the
stop instruction is there to prevent both.

---

## Kickoff prompt (paste once)

```
Before writing any code, read these files in this repo and confirm you understand them:
- README.md and Vendor_Engine_Architecture.md (the existing MVC / Producer-Consumer /
  Observer architecture — this is locked and must not change)
- DESIGN-NOTES.md (why the redesign looks the way it does)
- vendor-engine-ui-refresh.html (open it in a browser if you can — it's the visual/
  interaction reference for the new LoginView, KitchenView, and AdminView)
- IMPLEMENTATION-CHECKLIST.md (the phased task list we'll work through, one phase per
  message from here on)

Do not write or edit any code yet. Reply with:
1. A one-paragraph summary of the current view/ package structure and how
   OrderObserver notifications currently reach the Swing views.
2. Confirmation that you understand model/ and controller/ are out of scope for this
   whole project — only view/ (and UiTheme) should change.
3. Any ambiguity you already see between the HTML reference and the current codebase
   that you'd want resolved before Phase 0.
```

---

## Phase 0 — Foundation: `UiTheme`

```
Implement Phase 0 of IMPLEMENTATION-CHECKLIST.md only. Do not touch any view class yet.

Deliverables:
- Add FRESH, WARM, HOT color constants to UiTheme.java, alongside the existing OK/WARN.
  Use the hex values from DESIGN-NOTES.md's token list.
- Add UiTheme.urgencyColor(long elapsedMs, long maxMs): returns FRESH below 50% of
  max, WARM below 90%, HOT above 90%.
- Add a monospace Font constant to UiTheme for order IDs, elapsed timers, and currency.
- Find every place revenue/currency is currently formatted with "$" and switch it to "₹".

Constraints:
- Do not modify model/, controller/, or io/.
- Do not touch LoginView, KitchenView, or AdminView in this phase — foundation only.
- If you're unsure whether a "$" occurrence is currency vs. something else, ask rather
  than guessing.

Output format:
1. Plan — list the exact files you'll edit before editing them.
2. Implementation.
3. Self-check — go through this phase's checklist bullets one by one and state which
   file/method satisfies each.
4. Confirm the project still compiles (mvn clean package), then stop. Do not start
   Phase 1.
```

---

## Phase 1 — `LoginView`

```
Implement Phase 1 of IMPLEMENTATION-CHECKLIST.md only.

Deliverables:
- Replace the role JComboBox with two selectable tile panels (Kitchen Worker /
  Merchant Admin) — plain JPanels with a MouseListener toggling a selected-state
  border/background is enough; no new library.
- The stall picker is only visible/enabled when Kitchen Worker is selected.
- The stall picker lists "stallName (stallId)" — pull the name from stalls.json /
  StallDataSource, not just the bare ID.
- "Enter Dashboard" stays disabled until a valid role (+ stall, if kitchen worker) is
  chosen.
- Add a small status line reflecting NetworkMonitorDaemon's current online state.

Constraints:
- Only LoginView.java (and UiTheme if you need a new constant) should change.
- Never construct a second OrderController or StallDataSource — reuse what
  Main.java already passes in.
- Match the interaction in the Login tab of vendor-engine-ui-refresh.html: selecting
  a tile swaps the highlight, the stall list appears/disappears, the button's
  enabled state tracks a valid selection.

Output format:
1. Plan — files you'll edit.
2. Implementation.
3. Self-check — walk through this phase's checklist bullets and confirm each is met.
4. Stop. Do not start Phase 2.
```

---

## Phase 2a — `KitchenView`: board layout

```
Implement Phase 2a of IMPLEMENTATION-CHECKLIST.md only (board layout — not the card
component yet, that's 2b).

Deliverables:
- Replace the single JTable in KitchenView with a three-column layout: Pending /
  Accepted / Ready. A GridLayout(1,3) of panels, each holding a vertical Box of
  order-card placeholders, is a reasonable structure — use your judgment on the
  exact container.
- OrderTableModel's responsibility shifts from "table rows" to "which column each
  order belongs to." On onOrderUpdated, the order should move to the column matching
  its new OrderStatus, not just trigger fireTableDataChanged().
- It's fine if the cards themselves are still plain/unstyled placeholders in this
  phase — the column-routing logic is what this phase is actually about. Cards get
  built out in Phase 2b.

Constraints:
- Only KitchenView.java and OrderTableModel.java should change.
- Keep OrderTableModel implementing OrderObserver exactly as before — only what it
  does with an update changes, not how updates reach it.
- Every OrderObserver callback must still resolve through
  SwingUtilities.invokeLater() — do not introduce a new path that touches Swing
  components off the EDT.

Output format:
1. Plan.
2. Implementation.
3. Self-check against this phase's checklist bullets.
4. Stop. Do not start Phase 2b.
```

---

## Phase 2b — `KitchenView`: order card component

```
Implement Phase 2b of IMPLEMENTATION-CHECKLIST.md only.

Deliverables:
- New OrderCardPanel: renders order ID, item summary, a VIP ribbon when
  PriorityToken.VIP, a docket ring, and one contextual action button.
- New DocketRingComponent: a custom JComponent whose paintComponent draws an arc via
  Graphics2D/Arc2D.Double, sized by Order.getElapsedMs() against a max threshold you
  define, colored using UiTheme.urgencyColor from Phase 0.
- New TicketCardBorder (extend AbstractBorder): paints the two punch-notch circles
  and the dashed divider under the card header, matching the "docket" look in
  vendor-engine-ui-refresh.html.
- The action button's label and behavior come from the order's OrderStatus:
  PENDING → "Accept order", ACCEPTED → "Mark ready", READY → "Serve order", plus a
  small text-link-style Cancel (demoted visually — it's the rare/destructive path).

Constraints:
- New files go in view/. Nothing outside view/ changes.
- Button actions call the existing OrderController.transitionStatus(...) — do not
  add new mutation paths into the controller.
- DocketRingComponent should repaint on a timer or on observer notification, not
  block the EDT.

Output format:
1. Plan.
2. Implementation.
3. Self-check against this phase's checklist bullets.
4. Run the app, let PeakHourSimulator push orders, and describe what you observed
   (cards appearing in Pending, ring color changing over time, VIP ribbon showing).
5. Stop. Do not start Phase 2c.
```

---

## Phase 2c — `KitchenView`: completed strip

```
Implement Phase 2c of IMPLEMENTATION-CHECKLIST.md only.

Deliverables:
- A collapsible panel below the three-column board listing SERVED and CANCELLED
  orders for this stall, collapsed by default, expandable on click.

Constraints:
- Only KitchenView.java (and a new small panel class if you prefer) should change.
- Served/cancelled orders should leave the three-column board and appear here instead
  — don't show them in both places.

Output format:
1. Plan.
2. Implementation.
3. Self-check.
4. Stop. Do not start Phase 2d.
```

---

## Phase 2d — `KitchenView`: offline banner

```
Implement Phase 2d of IMPLEMENTATION-CHECKLIST.md only.

Deliverables:
- Restyle the existing offline indicator using the HOT color from UiTheme, bound to
  AppState.online, matching the banner copy/behavior in vendor-engine-ui-refresh.html
  ("Working offline — orders are queueing on this device").

Constraints:
- This should already exist in some form per the architecture doc's KitchenView
  spec — restyle it, don't duplicate it.

Output format:
1. Plan.
2. Implementation.
3. Self-check against all of Phase 2's checklist bullets (2a–2d together), since this
   closes out Phase 2 entirely.
4. Stop. Do not start Phase 3.
```

---

## Phase 3 — `AdminView`

```
Implement Phase 3 of IMPLEMENTATION-CHECKLIST.md only.

Deliverables:
- Custom cell renderer for the stall JList: status dot (fresh/warm/hot by queue-depth
  thresholds you define) + stall name + ID + queue-depth badge.
- Selected-stall detail panel shows that stall's live mini order list, replacing the
  four static labels currently shown.
- Revenue chart keeps the existing Graphics2D custom-paint approach — add a
  gridline, a pulsing "latest point" indicator, and ₹ formatting.
- Add a revenue-by-stall ranking (horizontal bars) below/beside the chart, sourced
  from StallDataSource.
- Restyle the four metric cards (Active Stalls / Total Queue Depth / Total Revenue /
  Network Status) with an icon, a monospace numeral, and a small delta/status caption.

Constraints:
- Only AdminView.java (and small new helper classes in view/ if needed) should change.
- Read all data through StallDataSource's read-only facade — do not add new
  mutation methods to reach into OrderController from AdminView.

Output format:
1. Plan.
2. Implementation.
3. Self-check against this phase's checklist bullets.
4. Stop. Do not start Phase 4.
```

---

## Phase 4 — Logout dialog

```
Implement Phase 4 of IMPLEMENTATION-CHECKLIST.md only.

Deliverables:
- Replace JOptionPane.showConfirmDialog with a themed JDialog (custom content pane,
  or setUndecorated(true) with your own title bar) matching UiTheme, used by both
  KitchenView and AdminView's logout button.

Constraints:
- One shared dialog/class used from both views — don't duplicate the implementation.

Output format:
1. Plan.
2. Implementation.
3. Self-check.
4. Stop. Do not start Phase 5.
```

---

## Phase 5 — QA pass (run this last, don't skip)

```
Run the Phase 5 QA pass from IMPLEMENTATION-CHECKLIST.md.

Do the following and report the results directly in your response, don't just say
you did them:
1. Run: grep -r "javax.swing" src/main/java/com/festival/vendorengine/model/
   Paste the output. It must be empty.
2. Review every OrderObserver callback path added or changed across Phases 0–4 and
   confirm each still resolves through SwingUtilities.invokeLater(). List any that
   don't.
3. Give a short per-view note (LoginView / KitchenView / AdminView) comparing the
   final result against its matching tab in vendor-engine-ui-refresh.html — call out
   anything you deliberately implemented differently and why.
4. State plainly whether this redesign stayed a view-layer-only change, per
   README.md and Vendor_Engine_Architecture.md's stated MVC rule.
```
