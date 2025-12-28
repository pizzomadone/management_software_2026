// File: Product.java
public class Product {
    private int id;
    private String code;
    private String name;
    private String description;
    private double price;
    private int quantity;

    // Category & Management
    private String category;
    private String alternativeSku;

    // Dimensions & Weight
    private double weight;
    private String unitOfMeasure;

    // Stock Management
    private int minimumQuantity;
    private double acquisitionCost;

    // Logistics
    private boolean active;
    private Integer supplierId;
    private String supplierName; // For display purposes

    // Warehouse & Tax
    private String warehousePosition;
    private double vatRate;

    public Product(int id, String code, String name, String description, double price, int quantity) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.description = description;
        this.price = price;
        this.quantity = quantity;
        this.category = "";
        this.alternativeSku = "";
        this.weight = 0.0;
        this.unitOfMeasure = "pcs";
        this.minimumQuantity = 0;
        this.acquisitionCost = 0.0;
        this.active = true;
        this.supplierId = null;
        this.supplierName = "";
        this.warehousePosition = "";
        this.vatRate = 0.0;
    }

    public Product(int id, String code, String name, String description, double price, int quantity,
                   String category, String alternativeSku, double weight, String unitOfMeasure,
                   int minimumQuantity, double acquisitionCost, boolean active, Integer supplierId, String supplierName,
                   String warehousePosition, double vatRate) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.description = description;
        this.price = price;
        this.quantity = quantity;
        this.category = category;
        this.alternativeSku = alternativeSku;
        this.weight = weight;
        this.unitOfMeasure = unitOfMeasure;
        this.minimumQuantity = minimumQuantity;
        this.acquisitionCost = acquisitionCost;
        this.active = active;
        this.supplierId = supplierId;
        this.supplierName = supplierName;
        this.warehousePosition = warehousePosition;
        this.vatRate = vatRate;
    }

    // Getters
    public int getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public double getPrice() { return price; }
    public int getQuantity() { return quantity; }
    public String getCategory() { return category; }
    public String getAlternativeSku() { return alternativeSku; }
    public double getWeight() { return weight; }
    public String getUnitOfMeasure() { return unitOfMeasure; }
    public int getMinimumQuantity() { return minimumQuantity; }
    public double getAcquisitionCost() { return acquisitionCost; }
    public boolean isActive() { return active; }
    public Integer getSupplierId() { return supplierId; }
    public String getSupplierName() { return supplierName; }
    public String getWarehousePosition() { return warehousePosition; }
    public double getVatRate() { return vatRate; }

    // Setters
    public void setId(int id) { this.id = id; }
    public void setCode(String code) { this.code = code; }
    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setPrice(double price) { this.price = price; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public void setCategory(String category) { this.category = category; }
    public void setAlternativeSku(String alternativeSku) { this.alternativeSku = alternativeSku; }
    public void setWeight(double weight) { this.weight = weight; }
    public void setUnitOfMeasure(String unitOfMeasure) { this.unitOfMeasure = unitOfMeasure; }
    public void setMinimumQuantity(int minimumQuantity) { this.minimumQuantity = minimumQuantity; }
    public void setAcquisitionCost(double acquisitionCost) { this.acquisitionCost = acquisitionCost; }
    public void setActive(boolean active) { this.active = active; }
    public void setSupplierId(Integer supplierId) { this.supplierId = supplierId; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }
    public void setWarehousePosition(String warehousePosition) { this.warehousePosition = warehousePosition; }
    public void setVatRate(double vatRate) { this.vatRate = vatRate; }
}
