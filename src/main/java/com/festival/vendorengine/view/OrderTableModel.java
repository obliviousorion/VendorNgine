package com.festival.vendorengine.view;

import com.festival.vendorengine.controller.OrderComparator;
import com.festival.vendorengine.controller.OrderObserver;
import com.festival.vendorengine.model.Order;
import com.festival.vendorengine.model.Stall;

import java.util.ArrayList;
import java.util.List;
import javax.swing.table.AbstractTableModel;

/**
 * Table model for the active order queue of a specific stall (Section 9.2).
 *
 * <p><strong>EDT Discipline:</strong>
 * This class implements {@link OrderObserver}. Its updates only occur on the
 * Event Dispatch Thread (EDT) because the back-end notifies observers via
 * {@code SwingUtilities.invokeLater} in {@code OrderController.notifyObservers}.
 * Therefore, this class does not require additional internal synchronization.
 */
public class OrderTableModel extends AbstractTableModel implements OrderObserver {

    private static final String[] COLUMNS = {
        "Order ID", "Items Summary", "Priority", "Elapsed", "Status"
    };

    private final Stall stall;
    private List<Order> orders;

    /**
     * Constructs a table model bound to the given stall's queue.
     *
     * @param stall the stall to display
     */
    public OrderTableModel(Stall stall) {
        this.stall = stall;
        this.orders = new ArrayList<>();
        refreshOrders();
    }

    /**
     * Returns the Order object at the specified row.
     *
     * @param rowIndex the row index in the table
     * @return the Order object, or null if index is out of bounds
     */
    public Order getOrderAt(int rowIndex) {
        if (rowIndex >= 0 && rowIndex < orders.size()) {
            return orders.get(rowIndex);
        }
        return null;
    }

    /**
     * Refreshes the local snapshot of orders from the stall queue,
     * sorting them in correct priority order (highest urgency first).
     */
    public void refreshOrders() {
        List<Order> temp = new ArrayList<>(stall.getOrderQueue());
        temp.sort(OrderComparator.DEFAULT);
        this.orders = temp;
        fireTableDataChanged();
    }

    // -------------------------------------------------------------------------
    // AbstractTableModel Implementation
    // -------------------------------------------------------------------------

    @Override
    public int getRowCount() {
        return orders.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Order order = getOrderAt(rowIndex);
        if (order == null) {
            return null;
        }

        return switch (columnIndex) {
            case 0 -> getShortOrderId(order.getOrderId());
            case 1 -> getItemsSummary(order.getItems());
            case 2 -> order.getPriorityToken().toString();
            case 3 -> formatElapsed(order.getElapsedMs());
            case 4 -> order.getStatus().toString();
            default -> null;
        };
    }

    // -------------------------------------------------------------------------
    // OrderObserver Implementation
    // -------------------------------------------------------------------------

    @Override
    public void onOrderUpdated(Order order) {
        if (order.getStallId().equals(stall.getStallId())) {
            refreshOrders();
        }
    }

    @Override
    public void onStallSnapshotChanged(Stall stall) {
        if (stall.getStallId().equals(this.stall.getStallId())) {
            refreshOrders();
        }
    }

    // -------------------------------------------------------------------------
    // Formatting Helpers
    // -------------------------------------------------------------------------

    private String getShortOrderId(String uuid) {
        if (uuid == null) return "";
        return uuid.length() > 8 ? uuid.substring(0, 8) + "..." : uuid;
    }

    private String getItemsSummary(List<Order.LineItem> items) {
        if (items == null || items.isEmpty()) {
            return "No items";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            Order.LineItem item = items.get(i);
            sb.append(item.getItemName()).append(" x").append(item.getQuantity());
            if (i < items.size() - 1) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    private String formatElapsed(long elapsedMs) {
        long seconds = elapsedMs / 1000;
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;
        return minutes + "m " + remainingSeconds + "s";
    }
}
