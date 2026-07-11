# Features and Usage Guide

Welcome to the **High-Concurrency Vendor Engine** system documentation. This guide comprehensively covers the features, UI views, concurrency architecture, network failover mechanism, developer simulation tools, and log configuration.

---

## 1. System Features Overview

The Vendor Engine is a local, high-concurrency order management platform designed for festival environments. It features complete separation of concern under the MVC paradigm, zero-dependency lightweight JSON parsing, and native Swing views utilizing a custom dark theme (`UiTheme`).

### Key Performance Guarantees
* **Zero Data Loss**: Orders are cached in memory and serialized locally when network drops occur.
* **Under 100ms UI Latency**: The interface handles high-throughput updates (up to 50 orders/sec) without locking the event loop.
* **Prioritization**: Strict priority sorting (VIP orders served first; FIFO sorting for equal priority tiers).

---

## 2. Interactive Dashboards

### 🔑 Multi-Role Login screen (`LoginView`)
The portal entry allows you to select your profile and enter the respective dashboard.

* **Kitchen Worker Mode**: Requires selecting a specific stall. The selector combobox is live-refreshed, picking up default stalls and any newly created stalls from simulator order discovery.
* **Merchant Admin Mode**: Gain access to global aggregate statistics.
* **Connection Status Pill**: Shows connection status (`● All stalls online · synced` in green or `● System offline · caching locally` in red).

---

### 🍳 Kitchen Dashboard (`KitchenView`)
Designed for stall operators to manage their live queue.

```
┌────────────────────────────────────────────────────────┐
│ [Online Indicator Pill]               [⚙ Simulator]    │
│                     STALL: CHAAT CORNER                │
├────────────────────────────────────────────────────────┤
│ PENDING (2)        │ ACCEPTED (1)      │ READY (0)     │
│ ┌────────────────┐ │ ┌───────────────┐ │               │
│ │ #1209  [VIP]   │ │ │ #1208         │ │               │
│ │ Pani Puri x2   │ │ │ Samosa x4     │ │               │
│ │ [Accept order] │ │ │ [Mark ready]  │ │               │
│ │  Cancel        │ │ │  Cancel       │ │               │
│ └────────────────┘ │ └───────────────┘ │               │
├────────────────────────────────────────────────────────┤
│ ▼ Recently Completed (2 today)                         │
└────────────────────────────────────────────────────────┘
```

#### Three-Column Kanban Board
* **PENDING**: New orders needing acceptance. Displays a yellow **Accept order** button.
* **ACCEPTED**: Orders currently being prepared. Displays a green **Mark ready** button.
* **READY TO SERVE**: Prepared orders awaiting pickup. Displays a red **Serve order** button.
* **Cancel action**: A demoted, text-link style red button at the bottom of the card for the rare/destructive cancellation path.

#### Ticket Card Visual Components
* **VIP Ribbon**: Visual ribbon rendered on cards carrying `PriorityToken.VIP`.
* **DocketRingComponent**: A custom-drawn circular ring utilizing a 2D Graphics arc. The ring's completion status represents the elapsed preparation time against a maximum urgency threshold. Its color shifts from fresh (green) to warm (amber) to hot (red) based on how long the order has been waiting.
* **TicketCardBorder**: A custom abstract border painting a dashed header divider and dual ticket punch-notches to deliver a realistic paper docket aesthetic.

#### Collapsible Completed Panel
* Collapsed by default. Clicking the header expands a panel showing completed (`SERVED` and `CANCELLED`) orders. Once transitioned to these states, tickets leave the columns above, freeing up board space.

#### Restyled Offline Banner
* Fades in at the top of the panel in the custom `HOT` red theme if connection status goes offline: *"Working offline — orders are queueing on this device (X cached)"*.

---

### 📈 Merchant Admin Dashboard (`AdminView`)
A read-only command center displaying real-time metrics across all stalls.

#### Stall JList
Uses a custom list cell renderer to display:
* **Status Dot**: Green (Fresh/small queue), Amber (Warm/medium queue), or Red (Hot/large queue).
* **Stall Name & ID**: e.g., `Chaat Corner (S01)`.
* **Queue Depth Badge**: A pill displaying the count of unresolved orders.

#### Selected-Stall Detail Panel
* Shows a mini real-time list of current orders active at the selected stall.

#### Real-Time Metric Cards
Displays four main KPI metrics with monospace numbers, custom status icons, and delta captions:
1. **Active Stalls**: Shows total registered stalls and a delta compared to the previous tick.
2. **Total Queue Depth**: Displays cumulative pending/preparing orders.
3. **Total Revenue**: Aggregate sales value formatted in Rupee (`₹`).
4. **Network Status**: Displays `ONLINE (synced)` or `OFFLINE (caching)`.

#### Graphics2D Revenue Chart
* **Gridlines**: Visual coordinate grid lines with dynamic Rupee axis labels.
* **Pulse Dot**: A pulsing circular highlight on the latest plot point using alpha compositing.

#### Stall Revenue Ranking Panel
* Renders a horizontal bar chart displaying revenue performance per stall, sorted descending, utilizing color weights to highlight top performers.

---

### 🚪 Shared Logout Confirmation (`ConfirmLogoutDialog`)
To avoid standard OS-looking alerts, clicking "Logout" on either dashboard triggers a custom modal dialog matching the dark `UiTheme` with flat layout options (`Yes, log out` in destructive red vs `Stay`).

---

## 3. Developer Simulator Control Panel

Launched as a separate floating window (governed by the `AppConfig.SHOW_SIMULATOR_PANEL` flag), this panel allows you to interactively manipulate the simulation.

```
┌────────────────────────────────────────────────────────┐
│ ⚙ Simulator Control                                    │
├────────────────────────────────────────────────────────┤
│ ● RUNNING      │  Queue: 14     │  Generated: 1,024    │
├────────────────────────────────────────────────────────┤
│ SPEED MODE                                             │
│ ( ) Slow (4s/order)      (•) Normal (2s/order)         │
│ ( ) Peak (5/sec)         ( ) Custom: [2000] ms         │
│                                                [Apply] │
├────────────────────────────────────────────────────────┤
│      [ ⏸ Pause / ▶ Resume ]    [ 🌐 Network Down ]    │
├────────────────────────────────────────────────────────┤
│ INJECT ORDERS                                          │
│ Target Stall: [ All stalls (random)                v ] │
│ Quantity:     [ 5 ]                  [ ⚡ Inject Now ]  │
└────────────────────────────────────────────────────────┘
```

* **Live Stats Bar**: Updates every second with the active producer state (`RUNNING`, `PAUSED`, or `NETWORK DOWN`), current queue capacity size, and total generated orders counter.
* **Speed Mode Selectors**: Toggles order generation rates:
  - **Slow**: 1 order every 4 seconds.
  - **Normal**: 1 order every 2 seconds.
  - **Peak**: 5 orders per second.
  - **Custom**: Type a custom delay in milliseconds.
* **Pause / Resume**: Instantly suspend or resume the order producer stream.
* **Network Toggle Button**: Simulates network drops by writing `"up"` or `"down"` to `network.flag`.
  - The label changes based on context (e.g. shows "Network Down" in red when online, and "Network Up" in green when offline).
  - Toggling network connection stops/resumes order inflows consistently.
* **Targeted Injector**: Inject any quantity of random standard/VIP orders instantly to all stalls or a selected stall for rapid testing.

---

## 4. Offline Failover & Sync Mechanism

```
                     ┌──────────────────┐
                     │  network.flag    │
                     └────────┬─────────┘
                              │ polled every 1s
                    ┌─────────▼─────────┐
                    │NetworkMonitorDaemon
                    └────┬───────────┬──┘
          if "down"      │           │      if "up"
  ┌──────────────────────▼──┐     ┌──▼──────────────────────┐
  │ 1. Serialize AppState   │     │ 1. Set appState.online=t│
  │    to offline_stalls.ser│     │ 2. Collect SERVED orders│
  │ 2. Set appState.online=f│     │ 3. SyncClient syncs to  │
  │ 3. UI shows red banner  │     │    sync_payload.json    │
  │ 4. Producer pauses      │     │ 4. Producer resumes     │
  └─────────────────────────┘     └─────────────────────────┘
```

1. **Detection**: `NetworkMonitorDaemon` runs as an independent daemon thread, checking `network.flag` every second.
2. **Failover (Offline)**: If the file content is changed from `"up"` to `"down"`, the daemon serializes the current `AppState` (including queues and stall configurations) to `offline_stalls.ser` and flags the UI. The order producer automatically pauses to prevent queue overflows while disconnected.
3. **Reconnection (Online)**: When changed back to `"up"`, the daemon marks the app state as online, gathers all `SERVED` orders, and writes a sync batch to `sync_payload.json` (simulating a database upload). The producer automatically resumes generating orders.

---

## 5. Log & Data File Layout

The application isolates debug/event logging to keep the root working directory clean:

* **`/log/` (Logging directory)**:
  - **`log/app.log`**: Contains redirected outputs from `System.out` and `System.err`.
  - **`log/hs_err_pid*.log` / `log/replay_pid*.log`**: JVM native crash logs and replay data (redirected via compiler/surefire argument configurations).
* **Workspace Root (State / Control files)**:
  - **`stalls.json`**: Static configuration mapping stall IDs to readable names.
  - **`network.flag`**: Heartbeat flag file (`up` / `down`) controlling network status.
  - **`offline_stalls.ser`**: Local binary snapshot of serialized app state on network loss.
  - **`sync_payload.json`**: Synchronization history containing orders processed offline.
