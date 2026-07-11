# Vendor Engine — UI/UX Redesign Notes

Companion to `vendor-engine-ui-refresh.html`. That file is an interactive reference —
open it in a browser, flip between the three tabs (Login / Kitchen / Admin), click
around. This doc explains *why* it's built that way and how to translate it back into
your actual Java Swing app.

## Concept

The app runs on a phone-in-your-pocket, food-on-the-grill festival floor — Chaat
Corner, Momo Point, Dosa Junction. The visual language leans into that: a warm
marigold accent (kept from your existing `UiTheme`, since it already reads as
"festival" and works well on dark), ticket/docket-shaped order cards with punched
corner notches, and monospace numerals everywhere something is being counted, timed,
or paid — order IDs, elapsed seconds, revenue — so it reads like a kitchen ticket
printer, not generic dashboard chrome.

**Signature element:** the *docket ring* — a small radial timer around each order
card that fills as the order ages and shifts green → amber → red. Right now elapsed
time is a plain text column ("22s") a worker has to read one row at a time. A ring is
glanceable across an entire board in one look, which matters a lot in a loud,
fast, multi-order kitchen — this is the actual UX problem the redesign is solving,
not just a decoration.

## Design tokens

```css
/* Color */
--bg:            #121019;   /* app background */
--surface:       #1C1929;   /* cards, panels */
--surface-2:     #241F33;   /* raised / hover surface */
--border:        #3A3450;

--text:          #F5F1E8;   /* warm paper white, not pure white */
--text-muted:    #A79FC2;
--text-faint:    #6E6785;

--marigold:      #F6A93B;   /* primary accent — VIP, primary actions, highlights */
--tamarind:      #E8583F;   /* secondary accent — used sparingly (VIP ribbon gradient) */

--fresh:  #35C97F;  /* order just placed / stall healthy / online   */
--warm:   #F6A93B;  /* order aging / stall busy                     */
--hot:    #FF5C68;  /* order overdue / stall backlogged / offline   */

/* Type */
--font-display: system-ui, "Segoe UI", sans-serif;   /* headings, weight 800 */
--font-body:    system-ui, "Segoe UI", sans-serif;   /* everything else, weight 400–600 */
--font-mono:    ui-monospace, "JetBrains Mono", "Cascadia Mono", monospace; /* IDs, timers, ₹ */
```

The three-color urgency scale (fresh / warm / hot) is reused everywhere something
has a time or health dimension: docket rings, stall status dots in the Admin
sidebar, the online/offline pill. One scale, three meanings, learned once.

## Problems found in the current screenshots, and the fix

**Login**
- The Role and Stall fields are plain OS combo boxes with no visual hierarchy —
  a worker has to open a dropdown to even see what their choices are.
  → Role is now two large tappable tiles (icon + one-line description); the stall
  picker only appears once "Kitchen Worker" is chosen, and lists stall *name and*
  ID together ("Chaat Corner · S01"), not just the code, so staff recognize their
  own stall.
- No confirmation the system is healthy before you commit to logging in.
  → Added a small "All stalls online" status line at the bottom of the card.

**Kitchen View**
- The four action buttons (Accept / Mark Ready / Serve / Cancel) are always
  visible and grey regardless of which order is selected or what status it's in —
  there's no visual cue for which action is actually valid right now, and you have
  to select a row, then look away to a separate button bar to act on it.
  → Rebuilt as a 3-column board (Pending / Accepted / Ready). Each order is a card
  with *one* contextual primary action on it directly ("Accept order", "Mark
  ready", "Serve order") — the action lives right next to the data it acts on.
  Cancel is demoted to a quiet text link, since it's the rare/destructive path.
- Elapsed time is a plain number buried in a table column, so a worker has to
  read every row to find what's about to breach SLA.
  → Docket ring per card (see Signature element above).
- Served/cancelled orders stayed in the same table as active ones, adding
  visual noise to the thing you're scanning most often.
  → Moved into a collapsed "Recently completed" strip at the bottom, expandable
  on demand.
- The "Confirm Logout" dialog renders in unstyled native OS chrome (light grey/
  white) against an otherwise dark app — a jarring inconsistency in the current
  screenshots.
  → Custom themed modal matching the rest of the app (see the Swing note below).
- No VIP visual treatment exists yet even though the architecture already has a
  `PriorityToken.VIP` — the queue can't currently show *why* an order jumped
  ahead of an older one.
  → Small gold-gradient "VIP" ribbon on the card.

**Admin / Merchant Operations Center**
- The "Selected Stall Details" panel is mostly empty vertical whitespace around
  four lines of static text, and the revenue chart is a flat `$0.00` line —
  neither uses the space to show anything actionable.
  → Selected-stall panel now shows that stall's live mini order list. The chart
  is a live area sparkline with a pulsing "latest point" dot, plus a
  revenue-by-stall ranking underneath so an admin can see *which* stall needs
  attention, not just the network-wide total.
- The stall list is a flat, undifferentiated list — you can't tell which stall
  is fine and which is drowning without clicking into each one.
  → Each row gets a status dot using the same fresh/warm/hot scale (backed by
  queue depth), so "Dosa Junction is red" is visible before you even click it.
- Currency is shown as `$0.00` — the deck's own numbers (₹1.5Cr+, BITS Pilani)
  say this is an Indian festival. Worth switching to ₹ for consistency.

## Porting this back into Swing

The app is pure Java/Swing per your architecture doc, not a browser — nothing
here is meant to be dropped in as-is. Rough translation table for whoever (or
whichever agent) implements it:

| This mockup | Swing technique |
|---|---|
| Docket ring (radial timer) | Small custom `JComponent` overriding `paintComponent`, drawing an arc with `Graphics2D.draw(new Arc2D.Double(...))`, same idea as the `paintComponent` revenue chart your architecture doc already plans for `AdminView`. Recompute the arc angle from `Order.getElapsedMs()` on each observer tick. |
| Ticket-notch card shape | Custom `Border` implementation (`AbstractBorder`) that paints two circular cutouts and a dashed divider, applied to each order's `JPanel`. |
| Contextual action button on each card | Instead of one global Accept/Ready/Serve/Cancel toolbar, give each row/card its own small button whose label and action are set from `Order.getStatus()` — this is a natural fit for a custom `TableCellRenderer`/`TableCellEditor` pair if you keep `JTable`, or simpler still if you move to one `JPanel` per order card in a `BoxLayout` column (closer to the board layout here). |
| 3-column board layout | Three `JPanel`s (Pending/Accepted/Ready) in a `GridLayout(1,3)`, each holding a vertically-stacked `Box` of order-card panels. `OrderTableModel`'s role shifts from "one table's rows" to "which column list each order belongs to." |
| Status/urgency color scale | Add three constants to `UiTheme` (`FRESH`, `WARM`, `HOT`) alongside the existing `OK`/`WARN`, and a small helper `UiTheme.urgencyColor(long elapsedMs, long maxMs)` used by both the ring painter and the Admin sidebar's status dots. |
| Themed logout modal | `JDialog` with `setUndecorated(true)` (or at minimum override its content pane's background/fonts) instead of the default `JOptionPane.showConfirmDialog`, which is what's currently producing the unstyled native dialog in your screenshots. |
| Revenue sparkline / ranking | Same `Graphics2D` custom-paint approach already scoped for `AdminView`; ranking bars are just filled rectangles sized proportionally, no library needed. |
| Stall name + ID together | Update `stalls.json` consumers to always render `stallName (stallId)` instead of ID alone, in both `LoginView`'s picker and `AdminView`'s sidebar. |

One optional note, not a recommendation either way: if the native-vs-app title
bar mismatch (light OS chrome over a dark app body, visible in your screenshots)
bothers you, a look-and-feel library like FlatLaf can give you a fully dark,
custom title bar and themed native dialogs for very little code — it's a small
extra dependency against your stated "pure Java, dependency-free" goal, so it's
worth weighing against just leaving the OS chrome as-is.

## Suggested prompt for Antigravity

```
Using vendor-engine-ui-refresh.html and DESIGN-NOTES.md as the visual/UX reference,
restyle the existing Swing views (LoginView, KitchenView, AdminView) in this repo to
match: the marigold/dark token palette, the docket-ring timer per order, the
3-column Pending/Accepted/Ready board in KitchenView, the contextual per-card action
button, the themed logout dialog, and the redesigned AdminView stall list + revenue
chart + ranking. Keep the existing MVC/Observer/Producer-Consumer architecture from
README.md and Vendor_Engine_Architecture.md untouched — this is a view-layer and
UiTheme restyle, not an architecture change. See the porting table in DESIGN-NOTES.md
for how each web concept maps to a Swing technique.
```
