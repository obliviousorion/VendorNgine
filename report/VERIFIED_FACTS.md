# Verified Facts Ledger

This ledger contains verified facts from the codebase of the CS F213 OOP Vendor Engine project. Every fact is mapped to its source file and line/section, along with its verification status.

## 1. Package Structure & Class Layout
- **AppConfig** lives at `src/main/java/com/festival/vendorengine/AppConfig.java` | verified: yes, file loaded.
- **Main** entry point is at `src/main/java/com/festival/vendorengine/Main.java` | verified: yes, file loaded.
- **Model classes** (`Order`, `OrderStatus`, `PriorityToken`, `AppState`, `Stall`, `UserRole`) live in `com.festival.vendorengine.model` package | verified: yes, files verified in `src/main/java/com/festival/vendorengine/model/`.
- **Exception classes** (`OfflineSerializationException`, `OrderProcessingException`, `StallNotFoundException`) live in `com.festival.vendorengine.exception` package | verified: yes, files verified in `src/main/java/com/festival/vendorengine/exception/`.
- **Concurrency classes** (`ExecutorServiceManager`, `MockDataLoader`, `OrderConsumer`, `OrderProducer`, `PeakHourSimulator`) live in `com.festival.vendorengine.concurrency` package | verified: yes, files verified in `src/main/java/com/festival/vendorengine/concurrency/`.
- **Controller classes** (`OrderComparator`, `OrderController`, `OrderObserver`, `StallDataSource`) live in `com.festival.vendorengine.controller` package | verified: yes, files verified in `src/main/java/com/festival/vendorengine/controller/`.
- **IO classes** (`JsonUtil`, `NetworkMonitorDaemon`, `OfflineSerializer`, `SyncClient`) live in `com.festival.vendorengine.io` package | verified: yes, files verified in `src/main/java/com/festival/vendorengine/io/`.
- **View classes** (`AdminView`, `ConfirmLogoutDialog`, `DocketRingComponent`, `KitchenView`, `LoginView`, `OrderCardPanel`, `OrderTableModel`, `SimulatorControlPanel`, `StallListCellRenderer`, `StallPanel`, `TicketCardBorder`, `UiTheme`) live in `com.festival.vendorengine.view` package | verified: yes, files verified in `src/main/java/com/festival/vendorengine/view/`.

## 2. Model Layer Details
- **OrderStatus** enum has values `PENDING`, `ACCEPTED`, `READY`, `SERVED`, `CANCELLED` — source: `com/festival/vendorengine/model/OrderStatus.java` | verified: yes, file checked.
- **UserRole** enum has values `KITCHEN_WORKER`, `MERCHANT_ADMIN` — source: `com/festival/vendorengine/model/UserRole.java` | verified: yes, file checked.
- **PriorityToken** enum has values `VIP(100)`, `STANDARD(0)` with weights accessed via `getWeight()` — source: `com/festival/vendorengine/model/PriorityToken.java` | verified: yes, file checked.
- **Order** contains fields `orderId` (String UUID), `stallId` (String), `items` (`List<LineItem>`), `priority` (`PriorityToken`), `createdAtMillis` (long), and `status` (`OrderStatus` declared as `volatile`) — source: `com/festival/vendorengine/model/Order.java:21-26` | verified: yes, fields verified.
- **Order.LineItem** is a static nested class inside `Order` with fields `itemName` (String), `quantity` (int), and `unitPrice` (double) — source: `com/festival/vendorengine/model/Order.java:107-136` | verified: yes, verified static nested class.
- **Stall** contains a `PriorityBlockingQueue<Order>` configured with `OrderComparator.DEFAULT` and has thread-safe revenue calculations (`addRevenue` and `getRevenueTotal`) using `synchronized` blocks — source: `com/festival/vendorengine/model/Stall.java:27-30, 46-57, 79-87` | verified: yes, verified queue and synchronized revenue methods.
- **AppState** represents system state with `ConcurrentHashMap<String, Stall> stallMap`, `volatile boolean online`, and `lastSyncedAtMillis` (long) — source: `com/festival/vendorengine/model/AppState.java` | verified: yes, checked fields.

## 3. Concurrency & Threading Model
- **ExecutorServiceManager** is a singleton managing a thread pool of size 16 (`Executors.newFixedThreadPool(16)`) — source: `com/festival/vendorengine/concurrency/ExecutorServiceManager.java:36` | **DISCREPANCY**: Code initializes pool with 16 threads, but planning docs specify 8.
- **OrderConsumer** run loop pulls from ingestion queue using `queue.take()`, parses JSON, and routes orders via the controller — source: `com/festival/vendorengine/concurrency/OrderConsumer.java:37-55` | verified: yes, run loop checked.
- **OrderProducer** run loop gets simulated order JSON strings from `PeakHourSimulator` and puts them in the bounded blocking queue (`queue.put()`) — source: `com/festival/vendorengine/concurrency/OrderProducer.java:42-57` | verified: yes, checked queue operations.
- **Ingestion queue capacity** is configured as 1000 — source: `Main.java:127` | verified: yes, `new LinkedBlockingQueue<>(1000)` checked.
- **Number of OrderConsumer instances** started at runtime is 8 — source: `Main.java:154-156` | verified: yes, loop runs 8 times.
- **OrderComparator** is a named class implementing `Comparator<Order>` and `Serializable` — source: `com/festival/vendorengine/controller/OrderComparator.java:40` | verified: yes, verified class definition and serializability.
- **OrderComparator ordering logic** sorts by priority weight descending, then by elapsed time descending (older orders served first) — source: `com/festival/vendorengine/controller/OrderComparator.java:68-80` | verified: yes, verified logic comparing `b` with `a`.
- **PeakHourSimulator** generates synthetic JSON streams simulating peak-hour traffic — source: `com/festival/vendorengine/concurrency/PeakHourSimulator.java` | verified: yes, file loaded.

## 4. Controller Layer & Observers
- **OrderObserver** is an interface defining `onOrderUpdated(Order)` and `onStallSnapshotChanged(Stall)` — source: `com/festival/vendorengine/controller/OrderObserver.java` | verified: yes, file loaded.
- **OrderController** is the sole mutator of order states, registering observers using a thread-safe `CopyOnWriteArrayList<OrderObserver>`, and notifying them on EDT via `SwingUtilities.invokeLater()` — source: `com/festival/vendorengine/controller/OrderController.java:23, 30, 94-102` | verified: yes, verified controller operations and invokeLater funnel.
- **StallDataSource** provides a read-only facade wrapping the map of stalls — source: `com/festival/vendorengine/controller/StallDataSource.java` | verified: yes, checks verified.

## 5. I/O, Persistence & Network Failover
- **JsonUtil** is a custom recursive-descent JSON parser with zero dependencies — source: `com/festival/vendorengine/io/JsonUtil.java` | verified: yes, custom recursive parsing implementation checked.
- **OfflineSerializer** writes `AppState` to `offline_stalls.ser` on disconnect and loads it on recovery — source: `com/festival/vendorengine/io/OfflineSerializer.java` | verified: yes, Java serialization checked.
- **NetworkMonitorDaemon** runs as a daemon thread outside the main pool, checking the file `network.flag` (FLAG_FILE) every 1000ms. On connection lost, it serializes state and sets `AppState.online = false`. On reconnection, it sets `AppState.online = true` and calls `SyncClient` to sync — source: `com/festival/vendorengine/io/NetworkMonitorDaemon.java` | verified: yes, run loop and daemon config verified.
- **SyncClient** builds a JSON payload manually using `StringBuilder` representing served orders and writes it to `sync_payload.json` — source: `com/festival/vendorengine/io/SyncClient.java` | verified: yes, JSON generation verified.

## 6. View Layer & Visual Redesign Features
- **UiTheme** defines a custom dark palette: Background `0x1E1E2E` (#1E1E2E), Panel `0x2A2A3D` (#2A2A3D), Accent `0xF5A623` (#F5A623), OK `0x4CAF50` (#4CAF50), Warn `0xE94E4E` (#E94E4E) — source: `com/festival/vendorengine/view/UiTheme.java` | verified: yes, colors checked.
- **LoginView** allows multi-role selection and checks network status pill at the bottom — source: `com/festival/vendorengine/view/LoginView.java` | verified: yes, checked.
- **KitchenView** displays a three-column Kanban layout (Pending, Accepted, Ready) with row rendering, collapsible completed orders, and a red offline banner — source: `com/festival/vendorengine/view/KitchenView.java` | verified: yes, checked.
- **DocketRingComponent** is a custom JComponent overriding `paintComponent` to draw a visual elapsed time radial ring that changes color based on wait time — source: `com/festival/vendorengine/view/DocketRingComponent.java` | verified: yes, custom graphics verified.
- **TicketCardBorder** implements ticket paper notches and dashed dividers — source: `com/festival/vendorengine/view/TicketCardBorder.java` | verified: yes, border painting checked.
- **AdminView** displays global metrics, list of stalls, real-time Mini order lists, and dynamic Graphics2D chart redrawing — source: `com/festival/vendorengine/view/AdminView.java` | verified: yes, verified painting chart.
- **ConfirmLogoutDialog** is a custom undecorated modal dialog styled to match the theme — source: `com/festival/vendorengine/view/ConfirmLogoutDialog.java` | verified: yes, custom undecorated Dialog verified.
- **SimulatorControlPanel** floats separately (if `AppConfig.SHOW_SIMULATOR_PANEL` is true), updating stats and speed modes (Slow, Normal, Peak, Custom), and toggling network.flag manually — source: `com/festival/vendorengine/view/SimulatorControlPanel.java` | verified: yes, control panels loaded.

## 7. Testing & Coverage Results
- **JUnit 5 tests** run: 69, Failures: 0, Errors: 0, Skipped: 0 — source: Maven surefire output | verified: yes, `mvn clean test` run successfully.
- **Measured instruction coverage** via `jacoco-maven-plugin`:
  - `com.festival.vendorengine.model`: **100.0%** (199/199 instructions) — source: `target/site/jacoco/jacoco.csv` | verified: yes, csv parsed.
  - `com.festival.vendorengine.controller`: **97.21%** (279/287 instructions) — source: `target/site/jacoco/jacoco.csv` | verified: yes, csv parsed.
  - `com.festival.vendorengine.io`: **98.13%** (1262/1286 instructions) — source: `target/site/jacoco/jacoco.csv` | **DISCREPANCY**: README claims 98.5% instruction / 78.2% branch coverage. Actual is 98.13% instruction / 85.16% branch coverage (132/155 branches).
  - `com.festival.vendorengine.view` is omitted from coverage reporting | verified: yes, confirmed view package has no coverage data in the CSV.
