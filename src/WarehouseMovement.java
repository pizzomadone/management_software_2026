
// File: WarehouseMovement.java
import java.util.Date;

public class WarehouseMovement {
    private int id;
    private int productId;
    private String productName;
    private Date date;
    private String type;
    private int quantity;
    private String reason;
    private String documentNumber;
    private String documentType;
    private String notes;

    public WarehouseMovement(int id, int productId, String productName, Date date,
                            String type, int quantity, String reason, String documentNumber,
                            String documentType, String notes) {
        this.id = id;
        this.productId = productId;
        this.productName = productName;
        this.date = date;
        this.type = type;
        this.quantity = quantity;
        this.reason = reason;
        this.documentNumber = documentNumber;
        this.documentType = documentType;
        this.notes = notes;
    }

    // Standard Getters and Setters
    public int getId() { return id; }
    public int getProductId() { return productId; }
    public String getProductName() { return productName; }
    public Date getDate() { return date; }
    public String getType() { return type; }
    public int getQuantity() { return quantity; }
    public String getReason() { return reason; }
    public String getDocumentNumber() { return documentNumber; }
    public String getDocumentType() { return documentType; }
    public String getNotes() { return notes; }

    public void setId(int id) { this.id = id; }
    public void setProductId(int productId) { this.productId = productId; }
    public void setProductName(String productName) { this.productName = productName; }
    public void setDate(Date date) { this.date = date; }
    public void setType(String type) { this.type = type; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public void setReason(String reason) { this.reason = reason; }
    public void setDocumentNumber(String documentNumber) { this.documentNumber = documentNumber; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }
    public void setNotes(String notes) { this.notes = notes; }
}
