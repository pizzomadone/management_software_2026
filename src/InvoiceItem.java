
// File: InvoiceItem.java
public class InvoiceItem {
    private int id;
    private int invoiceId;
    private int productId;
    private String productName;
    private String productCode;
    private int quantity;
    private double unitPrice;
    private double vatRate;
    private double total;

    public InvoiceItem(int id, int invoiceId, int productId, String productName,
                      String productCode, int quantity, double unitPrice,
                      double vatRate, double total) {
        this.id = id;
        this.invoiceId = invoiceId;
        this.productId = productId;
        this.productName = productName;
        this.productCode = productCode;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.vatRate = vatRate;
        this.total = total;
    }

    // Getters
    public int getId() { return id; }
    public int getInvoiceId() { return invoiceId; }
    public int getProductId() { return productId; }
    public String getProductName() { return productName; }
    public String getProductCode() { return productCode; }
    public int getQuantity() { return quantity; }
    public double getUnitPrice() { return unitPrice; }
    public double getVatRate() { return vatRate; }
    public double getTotal() { return total; }

    // Setters
    public void setId(int id) { this.id = id; }
    public void setInvoiceId(int invoiceId) { this.invoiceId = invoiceId; }
    public void setProductId(int productId) { this.productId = productId; }
    public void setProductName(String productName) { this.productName = productName; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public void setUnitPrice(double unitPrice) { this.unitPrice = unitPrice; }
    public void setVatRate(double vatRate) { this.vatRate = vatRate; }
    public void setTotal(double total) { this.total = total; }
}

