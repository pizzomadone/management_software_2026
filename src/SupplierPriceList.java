
// File: SupplierPriceList.java
import java.util.Date;

public class SupplierPriceList {
    private int id;
    private int supplierId;
    private int productId;
    private String supplierProductCode;
    private double price;
    private int minimumQuantity;
    private Date validityStartDate;
    private Date validityEndDate;
    private String notes;

    public SupplierPriceList(int id, int supplierId, int productId, String supplierProductCode,
                            double price, int minimumQuantity, Date validityStartDate,
                            Date validityEndDate, String notes) {
        this.id = id;
        this.supplierId = supplierId;
        this.productId = productId;
        this.supplierProductCode = supplierProductCode;
        this.price = price;
        this.minimumQuantity = minimumQuantity;
        this.validityStartDate = validityStartDate;
        this.validityEndDate = validityEndDate;
        this.notes = notes;
    }

    // Getters
    public int getId() { return id; }
    public int getSupplierId() { return supplierId; }
    public int getProductId() { return productId; }
    public String getSupplierProductCode() { return supplierProductCode; }
    public double getPrice() { return price; }
    public int getMinimumQuantity() { return minimumQuantity; }
    public Date getValidityStartDate() { return validityStartDate; }
    public Date getValidityEndDate() { return validityEndDate; }
    public String getNotes() { return notes; }

    // Setters
    public void setId(int id) { this.id = id; }
    public void setSupplierId(int supplierId) { this.supplierId = supplierId; }
    public void setProductId(int productId) { this.productId = productId; }
    public void setSupplierProductCode(String supplierProductCode) { this.supplierProductCode = supplierProductCode; }
    public void setPrice(double price) { this.price = price; }
    public void setMinimumQuantity(int minimumQuantity) { this.minimumQuantity = minimumQuantity; }
    public void setValidityStartDate(Date validityStartDate) { this.validityStartDate = validityStartDate; }
    public void setValidityEndDate(Date validityEndDate) { this.validityEndDate = validityEndDate; }
    public void setNotes(String notes) { this.notes = notes; }
}
