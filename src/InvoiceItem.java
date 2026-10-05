// File: InvoiceItem.java
public class InvoiceItem {
    private int id;
    private int invoiceId;
    private int productId;
    private String productName;
    private String productCode;
    private int quantity;
    private double unitPriceExVat;
    private double unitPriceIncVat;
    private double vatRate;
    private double totalExVat;

    /** Legacy-compatible constructor: supplied unit price is VAT-exclusive. */
    public InvoiceItem(int id, int invoiceId, int productId, String productName,
                      String productCode, int quantity, double unitPrice,
                      double vatRate, double total) {
        this(id, invoiceId, productId, productName, productCode, quantity,
            unitPrice, SalePrice.incVatFromExVat(unitPrice, vatRate), vatRate, total);
    }

    public InvoiceItem(int id, int invoiceId, int productId, String productName,
                      String productCode, int quantity, double unitPriceExVat,
                      double unitPriceIncVat, double vatRate, double totalExVat) {
        this.id = id;
        this.invoiceId = invoiceId;
        this.productId = productId;
        this.productName = productName;
        this.productCode = productCode;
        this.quantity = quantity;
        this.unitPriceExVat = unitPriceExVat;
        this.unitPriceIncVat = unitPriceIncVat;
        this.vatRate = vatRate;
        this.totalExVat = totalExVat;
    }

    public int getId() { return id; }
    public int getInvoiceId() { return invoiceId; }
    public int getProductId() { return productId; }
    public String getProductName() { return productName; }
    public String getProductCode() { return productCode; }
    public int getQuantity() { return quantity; }
    /** Accounting unit price, VAT excluded. */
    public double getUnitPrice() { return unitPriceExVat; }
    public double getUnitPriceExVat() { return unitPriceExVat; }
    public double getUnitPriceIncVat() { return unitPriceIncVat; }
    public double getVatRate() { return vatRate; }
    /** Stored invoice line total, VAT excluded. */
    public double getTotal() { return totalExVat; }
    public double getTotalExVat() { return totalExVat; }
    public double getTotalIncVat() { return SalePrice.lineIncVat(quantity, unitPriceIncVat); }

    public void setId(int id) { this.id = id; }
    public void setInvoiceId(int invoiceId) { this.invoiceId = invoiceId; }
    public void setProductId(int productId) { this.productId = productId; }
    public void setProductName(String productName) { this.productName = productName; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public void setUnitPrice(double unitPrice) { this.unitPriceExVat = unitPrice; }
    public void setUnitPriceExVat(double unitPrice) { this.unitPriceExVat = unitPrice; }
    public void setUnitPriceIncVat(double unitPrice) { this.unitPriceIncVat = unitPrice; }
    public void setVatRate(double vatRate) { this.vatRate = vatRate; }
    public void setTotal(double total) { this.totalExVat = total; }
}
