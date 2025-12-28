
// File: SupplierOrder.java
import java.util.Date;
import java.util.List;
import java.util.ArrayList;

public class SupplierOrder {
    private int id;
    private int supplierId;
    private String supplierName;
    private String number;
    private Date orderDate;
    private Date expectedDeliveryDate;
    private String status;
    private double total;
    private String notes;
    private List<SupplierOrderItem> items;

    public SupplierOrder(int id, int supplierId, String supplierName, String number,
                        Date orderDate, Date expectedDeliveryDate, String status,
                        double total, String notes) {
        this.id = id;
        this.supplierId = supplierId;
        this.supplierName = supplierName;
        this.number = number;
        this.orderDate = orderDate;
        this.expectedDeliveryDate = expectedDeliveryDate;
        this.status = status;
        this.total = total;
        this.notes = notes;
        this.items = new ArrayList<>();
    }

    // Getters
    public int getId() { return id; }
    public int getSupplierId() { return supplierId; }
    public String getSupplierName() { return supplierName; }
    public String getNumber() { return number; }
    public Date getOrderDate() { return orderDate; }
    public Date getExpectedDeliveryDate() { return expectedDeliveryDate; }
    public String getStatus() { return status; }
    public double getTotal() { return total; }
    public String getNotes() { return notes; }
    public List<SupplierOrderItem> getItems() { return items; }

    // Setters
    public void setId(int id) { this.id = id; }
    public void setSupplierId(int supplierId) { this.supplierId = supplierId; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }
    public void setNumber(String number) { this.number = number; }
    public void setOrderDate(Date orderDate) { this.orderDate = orderDate; }
    public void setExpectedDeliveryDate(Date expectedDeliveryDate) { this.expectedDeliveryDate = expectedDeliveryDate; }
    public void setStatus(String status) { this.status = status; }
    public void setTotal(double total) { this.total = total; }
    public void setNotes(String notes) { this.notes = notes; }
    public void setItems(List<SupplierOrderItem> items) { this.items = items; }
}
