# High-Concurrency Vendor Engine — Complete Implementation Architecture
### CS F213 OOP · Track 4 (Application Development with Java Frameworks) · Part B Reference

This document is the single build reference for Part B. It assumes Part A (the formulation deck)
is locked: **MVC + Producer-Consumer + Observer + Singleton**, pure Java (Swing/AWT, `java.util.concurrent`,
`java.io`, `java.util`), no Spring/JavaFX, JUnit 5 for tests. Everything below — package layout, every
class with its exact fields/methods, thread model, file formats, GUI layout, and the build/test plan —
is meant to be followed directly without needing to consult anything else.

---

## 1. Scope Recap (from the deck)

| | |
|---|---|
| **Problem** | Django/WebSocket bottleneck at peak hours; zero offline resilience for vendor order flow |
| **Inputs** | Simulated WebSocket JSON stream, `stalls.json`, user role, heartbeat signal, priority token |
| **Outputs** | Live prioritised queues (Swing UI), status transitions, `offline_stalls.ser`, `sync_payload.json`, revenue metrics |
| **Success metrics** | ~0 data loss on drop, <100ms UI refresh @ 50 orders/sec, ≥95% priority accuracy, <3s recovery, ≥8 stall threads, no deadlocks |

Everything in this architecture is designed to hit those five metrics directly — each section calls out
which metric it satisfies.

---

## 2. Layered Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│  SIMULATION LAYER        PeakHourSimulator, MockDataLoader          │
└───────────────────────────────┬───────────────────────────────────-─┘
                                 │ produces JSON strings
┌───────────────────────────────▼───────────────────────────────────-─┐
│  CONCURRENCY LAYER       OrderProducer → BlockingQueue<String> →     │
│                          OrderConsumer[8]  (ExecutorServiceManager)  │
└───────────────────────────────┬───────────────────────────────────-─┘
                                 │ parsed Order objects
┌───────────────────────────────▼───────────────────────────────────-─┐
│  CONTROLLER LAYER        OrderController, StallDataSource,           │
│                          OrderComparator, OrderObserver (interface)  │
└──────┬────────────────────────┬───────────────────────────────┬────-┘
       │ notifies                │ persists on fault              │ reads/writes
┌──────▼──────┐          ┌───────▼────────┐              ┌────────▼───────┐
│ VIEW LAYER  │          │  IO/STORAGE     │              │  MODEL LAYER    │
│ KitchenView │          │ OfflineSerializer│              │ Order, Stall,   │
│ AdminView   │          │ SyncClient      │              │ AppState,       │
│ LoginView   │          │ NetworkMonitor  │              │ OrderStatus,    │
└─────────────┘          │ Daemon          │              │ UserRole        │
                         └─────────────────┘              └────────────────┘
```

**Rule enforced everywhere:** Model = zero UI/business logic (pure data + validation of its own
invariants only). View = zero business logic (only renders what Controller/Observer gives it). Controller
= the only place state transitions happen. This is what "MVC framework" means for the rubric — write it
literally like this in the report.

---

## 3. Package Structure (Maven layout)

```
vendor-engine/
├── pom.xml
├── README.md
├── data/
│   ├── stalls.json                 (sample input, checked in)
│   └── sample_orders.jsonl         (optional replay file for demo)
├── src/
│   ├── main/java/com/festival/vendorengine/
│   │   ├── Main.java
│   │   ├── AppConfig.java
│   │   ├── model/
│   │   │   ├── Order.java
│   │   │   ├── OrderStatus.java
│   │   │   ├── UserRole.java
│   │   │   ├── PriorityToken.java
│   │   │   ├── Stall.java
│   │   │   └── AppState.java
│   │   ├── exception/
│   │   │   ├── StallNotFoundException.java
│   │   │   ├── OrderProcessingException.java
│   │   │   └── OfflineSerializationException.java
│   │   ├── concurrency/
│   │   │   ├── ExecutorServiceManager.java
│   │   │   ├── OrderProducer.java
│   │   │   ├── OrderConsumer.java
│   │   │   ├── PeakHourSimulator.java
│   │   │   └── MockDataLoader.java
│   │   ├── controller/
│   │   │   ├── OrderController.java
│   │   │   ├── StallDataSource.java
│   │   │   ├── OrderComparator.java
│   │   │   └── OrderObserver.java
│   │   ├── io/
│   │   │   ├── OfflineSerializer.java
│   │   │   ├── SyncClient.java
│   │   │   ├── NetworkMonitorDaemon.java
│   │   │   └── JsonUtil.java
│   │   └── view/
│   │       ├── LoginView.java
│   │       ├── KitchenView.java
│   │       ├── AdminView.java
│   │       ├── StallPanel.java
│   │       ├── OrderTableModel.java
│   │       └── UiTheme.java
│   └── test/java/com/festival/vendorengine/
│       ├── model/OrderTest.java
│       ├── model/AppStateSerializationTest.java
│       ├── concurrency/OrderProducerConsumerTest.java
│       ├── controller/OrderControllerTest.java
│       ├── controller/OrderComparatorTest.java
│       └── io/OfflineSerializerTest.java
```

~22 classes total — matches the "≈20 classes" scope claim from the deck's feasibility slide.

**pom.xml dependencies (deliberately minimal — "pure Java" per your brief):**
```xml
<dependencies>
    <!-- JUnit 5 for testing -->
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <version>5.10.2</version>
        <scope>test</scope>
    </dependency>
</dependencies>
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration><source>17</source><target>17</target></configuration>
        </plugin>
        <plugin>
            <!-- lets you `mvn package` a runnable fat jar with a Main-Class -->
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-jar-plugin</artifactId>
            <configuration>
                <archive><manifest><mainClass>com.festival.vendorengine.Main</mainClass></manifest></archive>
            </configuration>
        </plugin>
    </plugins>
</build>
```
No JSON library, no logging framework, no GUI framework beyond Swing. `JsonUtil` is a ~120-line
hand-rolled recursive-descent parser (Section 8) — this is intentional: it gives you a legitimate,
demonstrable "custom algorithm" talking point for the report beyond what's in the deck, at near-zero
extra effort, and keeps the dependency tree at one line (JUnit only).

---

## 4. Model Layer — exact class contracts

### 4.1 `OrderStatus` (enum)
```java
public enum OrderStatus { PENDING, ACCEPTED, READY, SERVED, CANCELLED }
```

### 4.2 `UserRole` (enum)
```java
public enum UserRole { KITCHEN_WORKER, MERCHANT_ADMIN }
```

### 4.3 `PriorityToken` (enum)
```java
public enum PriorityToken {
    VIP(100), STANDARD(0);
    private final int weight;
    PriorityToken(int weight) { this.weight = weight; }
    public int getWeight() { return weight; }
}
```

### 4.4 `Order` (immutable-ish data holder + one inner class)
```java
public class Order implements Serializable, Comparable<Order> {
    private static final long serialVersionUID = 1L;

    private final String orderId;          // UUID
    private final String stallId;
    private final List<LineItem> items;    // inner static class, see below
    private final PriorityToken priority;
    private final long createdAtMillis;
    private volatile OrderStatus status;   // volatile: read/written by multiple threads

    public Order(String orderId, String stallId, List<LineItem> items,
                 PriorityToken priority, long createdAtMillis) { ... }

    public long getElapsedMs() { return System.currentTimeMillis() - createdAtMillis; }
    public int getPriority() { return priority.getWeight(); }
    // getters for all fields, setStatus(OrderStatus) is the ONLY mutator,
    // and it must only ever be called from OrderController.

    @Override
    public int compareTo(Order other) { return OrderComparator.DEFAULT.compare(this, other); }

    // Inner (static nested) class — satisfies "inner classes" requirement
    public static class LineItem implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String itemName;
        private final int quantity;
        private final double unitPrice;
        public LineItem(String itemName, int quantity, double unitPrice) { ... }
        public double getSubtotal() { return quantity * unitPrice; }
    }
}
```
Use a **static** nested class for `LineItem` (no implicit outer reference needed) — cheaper and avoids
accidentally serializing the whole `Order` graph twice. If you want a genuine *non-static* inner class
for the rubric ("inner classes" is called out explicitly), add one purely for the GUI:

```java
// inside StallPanel (view layer) — a real (non-static) inner class:
private class OrderRowRenderer extends DefaultTableCellRenderer {
    // non-static: closes over the enclosing StallPanel's `stall` field
    // to colour rows by elapsed wait time without any extra plumbing.
}
```

### 4.5 `Stall`
```java
public class Stall implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String stallId;
    private final String stallName;
    private final PriorityBlockingQueue<Order> orderQueue; // see Section 6.4
    private double revenueTotal;

    public Stall(String stallId, String stallName) {
        this.orderQueue = new PriorityBlockingQueue<>(64, OrderComparator.DEFAULT);
    }
    public synchronized void addRevenue(double amount) { revenueTotal += amount; }
    public synchronized double getRevenueTotal() { return revenueTotal; }
    public PriorityBlockingQueue<Order> getOrderQueue() { return orderQueue; }
    // enqueue(Order) delegates to orderQueue.put(order)
}
```

### 4.6 `AppState` — the object that gets serialized whole
```java
public class AppState implements Serializable {
    private static final long serialVersionUID = 1L; // BUMP this if fields ever change shape
    private final ConcurrentHashMap<String, Stall> stallMap;
    private volatile boolean online;
    private final long lastSyncedAtMillis;

    public AppState(ConcurrentHashMap<String, Stall> stallMap, boolean online, long lastSyncedAtMillis) { ... }
    // getters only — this class is a snapshot, mutated only by OrderController
}
```
`ConcurrentHashMap` is itself `Serializable`, so `AppState` serializes cleanly as long as every value
type inside it (`Stall` → `PriorityBlockingQueue<Order>` → `Order` → `LineItem`) also implements
`Serializable`. **`PriorityBlockingQueue` is NOT serializable in a portable way if you pass it a lambda
comparator** — this is a real gotcha, addressed in Section 6.4.

---

## 5. Exceptions (custom, checked where it matters)

```java
public class StallNotFoundException extends Exception {
    public StallNotFoundException(String stallId) {
        super("No stall registered with id: " + stallId);
    }
}

public class OrderProcessingException extends RuntimeException {
    public OrderProcessingException(String message, Throwable cause) { super(message, cause); }
}

public class OfflineSerializationException extends Exception {
    public OfflineSerializationException(String message, Throwable cause) { super(message, cause); }
}
```
**Design choice to defend in the report:** `StallNotFoundException` and `OfflineSerializationException`
are checked (caller must decide: retry, log, or surface to UI) because both represent recoverable,
expected-in-normal-operation conditions. `OrderProcessingException` is unchecked because it wraps
malformed-JSON-from-a-corrupt-stream — a programmer/data error you don't want every call site forced
to catch, but that must still never be silently swallowed (log it in the consumer's catch block, always
with the original cause chained via `getCause()`).

---

## 6. Concurrency Layer — the part the rubric weighs heaviest

### 6.1 `ExecutorServiceManager` (Singleton)
```java
public final class ExecutorServiceManager {
    private static final ExecutorServiceManager INSTANCE = new ExecutorServiceManager();
    private final ExecutorService pool = Executors.newFixedThreadPool(8);

    private ExecutorServiceManager() {}
    public static ExecutorServiceManager getInstance() { return INSTANCE; }
    public void submit(Runnable task) { pool.submit(task); }
    public void shutdown() {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) pool.shutdownNow();
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
```
Eagerly-initialized, thread-safe-by-classloader singleton — no double-checked locking needed, simplest
correct form. `shutdown()` must be wired to a JVM shutdown hook in `Main` (`Runtime.getRuntime()
.addShutdownHook(...)`) so `mvn exec` / closing the window doesn't leak threads.

### 6.2 `OrderProducer` (implements `Runnable`)
Reads from `PeakHourSimulator` (or a real socket later) and calls `queue.put(jsonString)`. One producer
thread is enough — the queue is the fan-out point.
```java
public class OrderProducer implements Runnable {
    private final BlockingQueue<String> queue;
    private final PeakHourSimulator simulator;
    private volatile boolean running = true;

    public void run() {
        while (running) {
            try {
                String payload = simulator.nextPayload(); // blocks/sleeps internally to control rate
                queue.put(payload);                         // blocks if queue full (backpressure)
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                running = false;
            }
        }
    }
    public void stop() { running = false; }
}
```

### 6.3 `OrderConsumer` (implements `Runnable`, 8 instances submitted to the pool)
```java
public class OrderConsumer implements Runnable {
    private final BlockingQueue<String> queue;
    private final OrderController controller;

    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                String raw = queue.take();                 // blocks if empty
                Order order = JsonUtil.parseOrder(raw);     // generic parse method, Section 8
                controller.routeOrder(order);               // ConcurrentHashMap lookup + enqueue
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (OrderProcessingException e) {
                System.err.println("[Consumer] dropped malformed order: " + e.getMessage());
                // never silently swallow — always log with context
            }
        }
    }
}
```
**Why 8 consumers, not 1:** the deck's success metric is "≥8 concurrent stall threads with no
deadlocks" and "<100ms UI refresh @ 50 orders/sec." A single consumer serializes all JSON parsing +
routing, which becomes the bottleneck at 90+ simultaneous stalls. Eight consumers pulling from one
shared `LinkedBlockingQueue<String>` (capacity 1000) gives you real parallelism with zero shared
mutable state between consumers (`ConcurrentHashMap` handles the only shared structure — no manual
locks needed anywhere in this layer, which is exactly what "zero deadlocks" should mean in your report:
not "we were careful," but "we structurally cannot deadlock because there are no nested locks").

### 6.4 The `PriorityBlockingQueue` serialization gotcha
`Stall.orderQueue` needs a comparator for correct ordering, but `PriorityBlockingQueue`'s constructor
comparator field is only serializable if the comparator class itself is serializable (lambdas are
*not* reliably serializable across JVM versions). Fix: make `OrderComparator` a real named class
implementing both `Comparator<Order>` and `Serializable`:

```java
public class OrderComparator implements Comparator<Order>, Serializable {
    private static final long serialVersionUID = 1L;
    public static final OrderComparator DEFAULT = new OrderComparator();

    @Override
    public int compare(Order a, Order b) {
        // composite score: priority weight first, then longer-waiting first
        int byPriority = Integer.compare(b.getPriority(), a.getPriority()); // higher weight first
        if (byPriority != 0) return byPriority;
        return Long.compare(b.getElapsedMs(), a.getElapsedMs()); // older order first
    }
}
```
This single class is what the deck's slide 5 snippet (`Comparator.comparingInt(...).thenComparingLong(...)`)
is glossing over — write it as a named class, not an inline lambda, specifically *because* it needs to
survive serialization. Mention this trade-off explicitly in the report; it's a strong "attention to
correctness" signal for the "advanced Java features" rubric line.

### 6.5 `MockDataLoader` / `PeakHourSimulator`
- `MockDataLoader.loadStalls(Path stallsJsonPath)` → reads `stalls.json` with `BufferedReader` +
  try-with-resources, returns `ConcurrentHashMap<String, Stall>`.
- `PeakHourSimulator` holds a configurable `ordersPerSecond` (default 10, spike mode 50) and generates
  synthetic JSON payloads with randomized `stallId`, random 1–4 `LineItem`s from a fixed menu pool, and
  a random `PriorityToken` (10% VIP). Sleep interval = `1000 / ordersPerSecond` ms between emissions,
  jittered ±20% so it isn't perfectly periodic (more realistic + better stresses the queue).

---

## 7. Controller Layer

### 7.1 `OrderObserver` (interface — Observer pattern)
```java
public interface OrderObserver {
    void onOrderUpdated(Order order);
    void onStallSnapshotChanged(Stall stall);
}
```

### 7.2 `OrderController` — the only class allowed to mutate status
```java
public class OrderController {
    private final ConcurrentHashMap<String, Stall> stallMap;
    private final CopyOnWriteArrayList<OrderObserver> observers = new CopyOnWriteArrayList<>();
    // CopyOnWriteArrayList: observers register once at startup, read far more than written —
    // ideal fit, and iteration during notify() never throws ConcurrentModificationException.

    public void registerObserver(OrderObserver o) { observers.add(o); }

    public void routeOrder(Order order) throws OrderProcessingException {
        Stall stall = stallMap.computeIfAbsent(order.getStallId(), Stall::new); // no explicit lock
        stall.getOrderQueue().put(order);
        notifyObservers(order, stall);
    }

    public void transitionStatus(String stallId, String orderId, OrderStatus next)
            throws StallNotFoundException {
        Stall stall = Optional.ofNullable(stallMap.get(stallId))
                .orElseThrow(() -> new StallNotFoundException(stallId));
        // find + validate legal transition (PENDING->ACCEPTED->READY->SERVED only, no skipping)
        // then order.setStatus(next); if SERVED, stall.addRevenue(order total)
        notifyObservers(order, stall);
    }

    private void notifyObservers(Order order, Stall stall) {
        SwingUtilities.invokeLater(() -> {
            for (OrderObserver obs : observers) {
                obs.onOrderUpdated(order);
                obs.onStallSnapshotChanged(stall);
            }
        });
    }
}
```
**This `SwingUtilities.invokeLater` call is the single most important line in the whole app.** Every
background thread (consumers, the sync worker, the network monitor) eventually calls into
`OrderController`, and every UI-touching callback funnels through here — so Swing's single-threaded
rule ("only touch components from the Event Dispatch Thread") is enforced in exactly one place instead
of scattered across 8 consumer threads. Put a code comment here in the actual submission; it's a
natural place for an evaluator to check "do they actually understand why this line exists."

### 7.3 `StallDataSource`
Thin read-only façade over `stallMap` for the `AdminView` (list all stalls, aggregate revenue, order
counts) — keeps `AdminView` from reaching into `OrderController`'s mutation methods.

---

## 8. IO / Storage Layer

### 8.1 `JsonUtil` — the hand-rolled parser (pure-Java, no dependency)
You only need to parse a flat, known shape (`stalls.json`, order payloads), so a full generic JSON
library is overkill. A ~120-line recursive-descent parser is a legitimate "algorithm" artifact:

```java
public final class JsonUtil {
    private JsonUtil() {}

    public static Order parseOrder(String raw) throws OrderProcessingException {
        try {
            Map<String, Object> map = parseObject(raw, new int[]{0});
            // extract fields, build List<Order.LineItem>, construct Order
            return new Order(...);
        } catch (RuntimeException e) {
            throw new OrderProcessingException("Malformed order JSON: " + raw, e);
        }
    }

    // Recursive-descent core: parseObject/parseArray/parseValue/parseString/parseNumber
    // each taking (String json, int[] cursor) and advancing cursor[0] as they consume tokens.
    private static Map<String, Object> parseObject(String json, int[] pos) { ... }
    private static List<Object> parseArray(String json, int[] pos) { ... }
    private static Object parseValue(String json, int[] pos) { ... }
}
```
If time is short in week 3, it's fully acceptable to swap this for `org.json` (single small dependency)
— note the trade-off explicitly in the report ("we hand-rolled a minimal parser to stay dependency-free;
a production system would use Jackson/org.json"). Either choice is defensible; just be explicit about it.

### 8.2 `OfflineSerializer`
```java
public class OfflineSerializer {
    private static final String FILE_NAME = "offline_stalls.ser";

    public void save(AppState state) throws OfflineSerializationException {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(FILE_NAME))) {
            oos.writeObject(state);
        } catch (IOException e) {
            throw new OfflineSerializationException("Failed to persist offline state", e);
        }
    }

    public AppState load() throws OfflineSerializationException {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(FILE_NAME))) {
            return (AppState) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new OfflineSerializationException("Failed to restore offline state", e);
        }
    }
}
```
This is exactly the deck's slide 7 snippet — kept here verbatim in spirit because it's already correct
(try-with-resources, checked custom exception, clear file name constant).

### 8.3 `NetworkMonitorDaemon`
```java
public class NetworkMonitorDaemon implements Runnable {
    private final AppState state;
    private final OfflineSerializer serializer;
    private final SyncClient syncClient;
    private volatile boolean lastKnownOnline = true;

    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            boolean heartbeatOk = pingHeartbeat(); // simulated: random or config-toggled failure
            if (!heartbeatOk && lastKnownOnline) {
                onNetworkLost();       // save state, flip AppState.online = false, flag UI
            } else if (heartbeatOk && !lastKnownOnline) {
                onNetworkRestored();   // load state, syncClient.pushBatch(), flip online = true
            }
            lastKnownOnline = heartbeatOk;
            try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }
}
```
Run this as a **daemon thread** (`thread.setDaemon(true)`) submitted outside the fixed pool (it must
never be starved out by 8 busy consumers) — either its own single-thread executor or a raw `Thread`.
This satisfies "fully autonomous, no manual trigger" from the deck.

### 8.4 `SyncClient`
`pushBatch(List<Order> servedOrSyncedOrders)` writes `sync_payload.json` via `BufferedWriter` +
try-with-resources (JSON built manually — no need for `JsonUtil`'s parse path, just string-building with
a `StringBuilder`, escaping quotes). In the demo, "syncing with Django" = writing this file; narrate in
the video that a real deployment would `POST` it instead.

---

## 9. View Layer (Swing)

### 9.1 Navigation flow
```
LoginView  →  (role == MERCHANT_ADMIN)  →  AdminView
           →  (role == KITCHEN_WORKER)  →  KitchenView
```
`LoginView` is a small `JFrame` with a `JComboBox<UserRole>` + stall picker (for kitchen workers) +
"Enter" button. On submit, `dispose()` the login frame and construct the target view, passing in the
shared `OrderController` and `StallDataSource` — **never construct a second `OrderController`.**

### 9.2 `KitchenView` (per-stall staff screen)
- `JFrame` → `StallPanel` (one active stall's queue).
- `JTable` bound to `OrderTableModel extends AbstractTableModel` (columns: Order ID, Items summary,
  Priority, Elapsed, Status). `OrderTableModel` implements `OrderObserver`; on `onOrderUpdated`, it
  calls `fireTableDataChanged()` — always from the EDT because `OrderController` already funneled it
  through `invokeLater`.
- Action buttons per row (via a custom `TableCellRenderer`/`TableCellEditor` pair, or simpler: three
  toolbar buttons — Accept / Ready / Serve — acting on the selected row) wired with lambdas:
  `btn.addActionListener(e -> controller.transitionStatus(stallId, selectedOrderId, OrderStatus.ACCEPTED));`
- Visual "offline" banner: a `JLabel` bound to `AppState.online`, red background + "OFFLINE — orders
  cached locally" text when false, hidden when true.

### 9.3 `AdminView` (multi-stall dashboard)
- Left: `JList<String>` of all stall IDs (from `StallDataSource`).
- Center: aggregate metrics — total revenue, orders/min, per-stall queue depth — in a small grid of
  `JPanel`s styled as cards (see `UiTheme`).
- A live line/bar chart is **optional/bonus**; if you want one without extra dependencies, draw it
  directly with `Graphics2D` inside a custom `JPanel.paintComponent` override (a small set of revenue
  points every 5s) — this doubles as a nice "custom rendering" talking point.

### 9.4 `UiTheme` — "good-looking" without extra dependencies
Pure Swing look, no third-party Look-and-Feel:
```java
public final class UiTheme {
    public static final Color BG = new Color(0x1E1E2E);
    public static final Color PANEL = new Color(0x2A2A3D);
    public static final Color ACCENT = new Color(0xF5A623);   // priority/VIP highlight
    public static final Color OK = new Color(0x4CAF50);
    public static final Color WARN = new Color(0xE94E4E);
    public static final Font TITLE = new Font("Segoe UI", Font.BOLD, 18);
    public static final Font BODY = new Font("Segoe UI", Font.PLAIN, 13);

    public static void apply() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) { /* fall back to cross-platform default */ }
        UIManager.put("control", BG);
        UIManager.put("info", PANEL);
        UIManager.put("nimbusBase", ACCENT);
        UIManager.put("text", Color.WHITE);
    }
}
```
Call `UiTheme.apply()` once at the very top of `Main.main()`, before any frame is constructed. Dark,
consistent palette + one accent color reused for VIP badges and the "offline" banner reads as
deliberately designed rather than default-grey-Swing — cheap visual win, zero dependencies.

### 9.5 EDT discipline checklist (put this literally in your code review / report)
1. `Main.main()` must start the GUI via `SwingUtilities.invokeLater(() -> new LoginView().setVisible(true))`.
2. No background thread (`OrderProducer`, `OrderConsumer`, `NetworkMonitorDaemon`, `SyncClient`) ever
   touches a Swing component directly — only through `OrderController.notifyObservers`.
3. Long-running work triggered *from* the UI (e.g., an admin clicking "Force Sync Now") uses
   `SwingWorker<Void, Void>`, not a raw thread, so `done()` can safely update the UI afterward.

---

## 10. Design Patterns — where each one actually lives

| Pattern | Where | Why it's real, not decorative |
|---|---|---|
| MVC | model/, view/, controller/ packages | Controller is the only class with `transitionStatus`; Model has no Swing imports at all; verify with `grep -r "javax.swing" model/` returning nothing |
| Observer | `OrderObserver`, `OrderController.observers` | Decouples "state changed" from "how many views care" — adding a 3rd view later needs zero controller changes |
| Producer-Consumer | `OrderProducer`/`OrderConsumer`/`BlockingQueue` | Textbook decoupling of ingestion rate from processing rate |
| Singleton | `ExecutorServiceManager` | One shared pool app-wide; prevents thread-count explosion if multiple components each made their own pool |
| Strategy (bonus, easy to add) | `OrderComparator` could be swapped for a `FifoComparator` or `RevenueWeightedComparator` at `Stall` construction time | Costs ~10 minutes, gives you a 6th pattern to cite |
| DAO (stub for future) | `StallDataSource` already has the read-only-façade shape of a DAO; note in report as "JDBC-ready" per the deck's slide 8 | Matches "future JDBC integration" claim without needing an actual DB for the demo |

---

## 11. Threading & Concurrency Summary Table

| Component | Thread(s) | Shared state touched | Synchronization mechanism |
|---|---|---|---|
| `OrderProducer` | 1 dedicated thread | `BlockingQueue<String>` | Built-in (blocking `put`) |
| `OrderConsumer` ×8 | pooled (`ExecutorServiceManager`) | same queue (take), `ConcurrentHashMap<String,Stall>` | Built-in (blocking `take`), lock-free map ops |
| `Stall.orderQueue` | any consumer thread | `PriorityBlockingQueue<Order>` | Built-in internal lock |
| `OrderController.observers` | consumers + daemon + sync worker (writers), EDT (reader) | `CopyOnWriteArrayList` | Copy-on-write, no explicit lock |
| `NetworkMonitorDaemon` | 1 daemon thread | `AppState.online` (volatile) | `volatile` flag, no lock needed |
| Swing components | EDT only | — | `SwingUtilities.invokeLater` funnel |

No method in this design acquires two locks at once, and nowhere does application code call
`synchronized` manually except `Stall.addRevenue`/`getRevenueTotal` (a single, uncontended, short
critical section) — which is why "no deadlocks" is a structural guarantee, not a hope. Say this
explicitly in the report; it directly answers the "Correctness & functionality: handles edge cases"
rubric line.

---

## 12. File Formats

### `stalls.json` (input, checked into `data/`)
```json
{
  "stalls": [
    { "stallId": "S01", "stallName": "Chaat Corner" },
    { "stallId": "S02", "stallName": "Momo Point" }
  ]
}
```

### Simulated order payload (what flows through the `BlockingQueue<String>`)
```json
{
  "orderId": "b1a7...-uuid",
  "stallId": "S01",
  "priority": "VIP",
  "createdAt": 1731234567890,
  "items": [
    { "itemName": "Pani Puri", "quantity": 2, "unitPrice": 60.0 }
  ]
}
```

### `sync_payload.json` (output, written by `SyncClient` on reconnect)
```json
{
  "syncedAt": 1731234999999,
  "orders": [
    { "orderId": "...", "stallId": "S01", "status": "SERVED", "total": 120.0 }
  ]
}
```

### `offline_stalls.ser`
Binary, produced by `ObjectOutputStream.writeObject(AppState)`. Never hand-edit; only ever
read/written by `OfflineSerializer`. Delete it between demo runs if you want a clean-start
recording.

---

## 13. Testing Plan (JUnit 5)

| Test class | Key cases |
|---|---|
| `OrderTest` | `getElapsedMs()` monotonic increase; `LineItem.getSubtotal()` correctness |
| `OrderComparatorTest` | VIP always ranks above Standard regardless of wait time; among equal priority, older order wins; comparator is consistent (`compare(a,b) == -compare(b,a)`) |
| `AppStateSerializationTest` | Round-trip: build `AppState` with 2 stalls/3 orders → serialize → deserialize → assert deep equality of order IDs, statuses, revenue totals |
| `OrderProducerConsumerTest` | Push 500 synthetic payloads through a real `LinkedBlockingQueue` + 8 consumers (small thread pool in test scope) → assert all 500 land in some stall's queue, none duplicated, none lost (use a `CountDownLatch(500)` to await completion, timeout 5s) |
| `OrderControllerTest` | Illegal transition (`PENDING → SERVED` skipping steps) throws/rejects; `StallNotFoundException` thrown for unknown stall ID |
| `OfflineSerializerTest` | Save then load from a temp file (`@TempDir`) round-trips correctly; corrupted file throws `OfflineSerializationException`, not a raw `IOException` |

Target ≥80% line coverage on `model/`, `controller/`, and `io/` (view/ is intentionally excluded — GUI
unit testing is out of scope and not worth the effort here; say so plainly in the report rather than
faking coverage numbers).

---

## 14. Build & Run Instructions (put verbatim in README.md)

```bash
# Build
mvn clean package

# Run (starts LoginView; PeakHourSimulator begins emitting immediately)
java -jar target/vendor-engine-1.0.jar

# Run tests
mvn test

# Toggle offline simulation manually during a live demo:
#   the NetworkMonitorDaemon reads a flag file `network.flag` every heartbeat tick;
#   `echo down > network.flag` to force offline mode, `echo up > network.flag` to restore.
#   This gives you a clean, narratable "watch it fail over" moment for the video
#   instead of relying on flaky real Wi-Fi during recording.
```

---

## 15. Week-by-Week Task List (expanding the deck's timeline)

**Week 1 — Core Foundation**
- Day 1–2: `model/` package complete + compiling, `OrderComparatorTest` and `OrderTest` passing
- Day 3: `ExecutorServiceManager`, `OrderProducer`, `OrderConsumer` wired to a throwaway `System.out.println` sink (no controller yet) — prove the queue moves data correctly under load
- Day 4–5: `OrderController` + `MockDataLoader` (`stalls.json` parsing), `OrderProducerConsumerTest` green

**Week 2 — GUI + Observer**
- Day 1–2: `LoginView`, `UiTheme`, basic `KitchenView` shell rendering a static table
- Day 3: `OrderTableModel` wired as a live `OrderObserver`; confirm EDT-only updates (no `IllegalStateException`/flicker under load)
- Day 4: `AdminView` aggregate panel
- Day 5: Polish, verify `PriorityQueue` visually ranks VIP correctly at 50 orders/sec

**Week 3 — Failover + Polish + Testing**
- Day 1: `OfflineSerializer` + `AppStateSerializationTest`
- Day 2: `NetworkMonitorDaemon` + manual `network.flag` toggle for demo control
- Day 3: `SyncClient` + `sync_payload.json` round trip
- Day 4: Remaining JUnit tests, fix any flaky timing-based tests (use `CountDownLatch`/`awaitTermination`, never raw `Thread.sleep` assertions)
- Day 5: Record demo video, finalize README + report

---

## 16. Report-Writing Cheat Sheet (map straight to the rubric)

- **Correctness & functionality** → cite Section 11's "no two locks at once" argument + the
  `OrderProducerConsumerTest` load test numbers you actually measure (report your real
  orders/sec and observed UI latency — measure, don't estimate).
- **OOP design & code quality** → cite Section 10's pattern table + the `model/` package's zero
  Swing-import guarantee.
- **Integration with platform** → cite Section 12's three file formats + Section 8.4's "would be a
  POST in production" note.
- **Advanced Java features** → you have, at minimum: `ExecutorService`, `BlockingQueue`,
  `PriorityBlockingQueue`, `ConcurrentHashMap`, `CopyOnWriteArrayList`, generics throughout,
  custom checked/unchecked exceptions, `Serializable` + `ObjectOutputStream`/`ObjectInputStream`,
  lambdas (action listeners), a hand-rolled recursive-descent parser, and an inner class in the view
  layer — nine distinct features against a "two minimum" bar.
- **Testing & documentation** → six JUnit classes (Section 13) + this document + the README's
  exact run commands (Section 14).

---

*This document intentionally leaves no class unspecified. If you follow Sections 3–9 top to bottom,
every file in Section 3's tree gets written exactly once, in dependency order (model → exception →
concurrency → controller → io → view), and nothing is left to improvise during the actual coding
weeks.*
