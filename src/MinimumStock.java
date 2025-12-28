public class MinimumStock {
    private int productId;
    private String productName;
    private int minimumQuantity;
    private int reorderQuantity;
    private int leadTimeDays;
    private Integer preferredSupplier;
    private String supplierName;
    private String notes;

    public MinimumStock(int productId, String productName, int minimumQuantity,
                       int reorderQuantity, int leadTimeDays, Integer preferredSupplier,
                       String supplierName, String notes) {
        this.productId = productId;
        this.productName = productName;
        this.minimumQuantity = minimumQuantity;
        this.reorderQuantity = reorderQuantity;
        this.leadTimeDays = leadTimeDays;
        this.preferredSupplier = preferredSupplier;
        this.supplierName = supplierName;
        this.notes = notes;
    }

    // Getters
    public int getProductId() { return productId; }
    public String getProductName() { return productName; }
    public int getMinimumQuantity() { return minimumQuantity; }
    public int getReorderQuantity() { return reorderQuantity; }
    public int getLeadTimeDays() { return leadTimeDays; }
    public Integer getPreferredSupplier() { return preferredSupplier; }
    public String getSupplierName() { return supplierName; }
    public String getNotes() { return notes; }

    // Setters
    public void setProductId(int productId) { this.productId = productId; }
    public void setProductName(String productName) { this.productName = productName; }
    public void setMinimumQuantity(int minimumQuantity) { this.minimumQuantity = minimumQuantity; }
    public void setReorderQuantity(int reorderQuantity) { this.reorderQuantity = reorderQuantity; }
    public void setLeadTimeDays(int leadTimeDays) { this.leadTimeDays = leadTimeDays; }
    public void setPreferredSupplier(Integer preferredSupplier) { this.preferredSupplier = preferredSupplier; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }
    public void setNotes(String notes) { this.notes = notes; }
}
