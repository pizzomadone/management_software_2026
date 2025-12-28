import java.util.Date;
import java.util.List;
import java.util.ArrayList;

public class Order {
    private int id;
    private int customerId;
    private String customerName;
    private Date orderDate;
    private String status;
    private double total;
    private List<OrderItem> items;

    public Order(int id, int customerId, String customerName, Date orderDate, String status, double total) {
        this.id = id;
        this.customerId = customerId;
        this.customerName = customerName;
        this.orderDate = orderDate;
        this.status = status;
        this.total = total;
        this.items = new ArrayList<>();
    }

    // Getters
    public int getId() { return id; }
    public int getCustomerId() { return customerId; }
    public String getCustomerName() { return customerName; }
    public Date getOrderDate() { return orderDate; }
    public String getStatus() { return status; }
    public double getTotal() { return total; }
    public List<OrderItem> getItems() { return items; }

    // Setters
    public void setId(int id) { this.id = id; }
    public void setCustomerId(int customerId) { this.customerId = customerId; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public void setOrderDate(Date orderDate) { this.orderDate = orderDate; }
    public void setStatus(String status) { this.status = status; }
    public void setTotal(double total) { this.total = total; }
    public void setItems(List<OrderItem> items) { this.items = items; }
}
