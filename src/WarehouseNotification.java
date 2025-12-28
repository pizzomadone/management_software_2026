
// File: WarehouseNotification.java
import java.util.Date;

public class WarehouseNotification {
    private int id;
    private int productId;
    private String productName;
    private Date date;
    private String type;
    private String message;
    private String status;

    public WarehouseNotification(int id, int productId, String productName,
                                Date date, String type, String message, String status) {
        this.id = id;
        this.productId = productId;
        this.productName = productName;
        this.date = date;
        this.type = type;
        this.message = message;
        this.status = status;
    }

    // Getters and Setters
    public int getId() { return id; }
    public int getProductId() { return productId; }
    public String getProductName() { return productName; }
    public Date getDate() { return date; }
    public String getType() { return type; }
    public String getMessage() { return message; }
    public String getStatus() { return status; }

    public void setId(int id) { this.id = id; }
    public void setProductId(int productId) { this.productId = productId; }
    public void setProductName(String productName) { this.productName = productName; }
    public void setDate(Date date) { this.date = date; }
    public void setType(String type) { this.type = type; }
    public void setMessage(String message) { this.message = message; }
    public void setStatus(String status) { this.status = status; }
}

