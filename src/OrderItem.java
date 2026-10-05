public class OrderItem {
    private int id;
    private int orderId;
    private int productId;
    private String productName;
    private int quantity;
    private double unitPriceExVat;
    private double unitPriceIncVat;
    private double vatRate;

    /** Legacy-compatible constructor: treats the supplied price as VAT-exclusive. */
    public OrderItem(int id, int orderId, int productId, String productName, int quantity, double unitPrice) {
        this(id, orderId, productId, productName, quantity, unitPrice, unitPrice, 0.0);
    }

    public OrderItem(int id, int orderId, int productId, String productName, int quantity,
                     double unitPriceExVat, double unitPriceIncVat, double vatRate) {
        this.id = id;
        this.orderId = orderId;
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPriceExVat = unitPriceExVat;
        this.unitPriceIncVat = unitPriceIncVat;
        this.vatRate = vatRate;
    }

    public int getId() { return id; }
    public int getOrderId() { return orderId; }
    public int getProductId() { return productId; }
    public String getProductName() { return productName; }
    public int getQuantity() { return quantity; }

    /** Accounting unit price, VAT excluded. */
    public double getUnitPrice() { return unitPriceExVat; }
    public double getUnitPriceExVat() { return unitPriceExVat; }
    public double getUnitPriceIncVat() { return unitPriceIncVat; }
    public double getVatRate() { return vatRate; }

    /** Legacy-compatible net line total. */
    public double getTotal() { return getTotalExVat(); }
    public double getTotalExVat() { return Numbers.lineTotal(quantity, unitPriceExVat); }
    public double getTotalIncVat() { return SalePrice.lineIncVat(quantity, unitPriceIncVat); }
    public double getVatAmount() { return Numbers.money(java.math.BigDecimal.valueOf(getTotalIncVat()).subtract(java.math.BigDecimal.valueOf(getTotalExVat()))); }

    public void setId(int id) { this.id = id; }
    public void setOrderId(int orderId) { this.orderId = orderId; }
    public void setProductId(int productId) { this.productId = productId; }
    public void setProductName(String productName) { this.productName = productName; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public void setUnitPrice(double unitPrice) { this.unitPriceExVat = unitPrice; }
    public void setUnitPriceExVat(double unitPrice) { this.unitPriceExVat = unitPrice; }
    public void setUnitPriceIncVat(double unitPrice) { this.unitPriceIncVat = unitPrice; }
    public void setVatRate(double vatRate) { this.vatRate = vatRate; }
}
