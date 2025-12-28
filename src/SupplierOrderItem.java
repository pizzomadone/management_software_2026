
// File: SupplierOrderItem.java
public class SupplierOrderItem {
    private int id;
    private int orderId;
    private int productId;
    private String productName;
    private String productCode;
    private int quantity;
    private double unitPrice;
    private double total;
    private String notes;

    public SupplierOrderItem(int id, int orderId, int productId, String productName,
                            String productCode, int quantity, double unitPrice,
                            double total, String notes) {
        this.id = id;
        this.orderId = orderId;
        this.productId = productId;
        this.productName = productName;
        this.productCode = productCode;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.total = total;
        this.notes = notes;
    }

    // Getters
    public int getId() { return id; }
    public int getOrderId() { return orderId; }
    public int getProductId() { return productId; }
    public String getProductName() { return productName; }
    public String getProductCode() { return productCode; }
    public int getQuantity() { return quantity; }
    public double getUnitPrice() { return unitPrice; }
    public double getTotal() { return total; }
    public String getNotes() { return notes; }

    // Setters
    public void setId(int id) { this.id = id; }
    public void setOrderId(int orderId) { this.orderId = orderId; }
    public void setProductId(int productId) { this.productId = productId; }
    public void setProductName(String productName) { this.productName = productName; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public void setUnitPrice(double unitPrice) { this.unitPrice = unitPrice; }
    public void setTotal(double total) { this.total = total; }
    public void setNotes(String notes) { this.notes = notes; }
}
