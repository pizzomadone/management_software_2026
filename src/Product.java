// File: Product.java
public class Product {
    private int id;
    private String code;
    private String name;
    private String description;
    /** Customer-facing sale price, VAT included. */
    private double price;
    private double salePriceExVat;
    private int quantity;

    private String category;
    private String alternativeSku;
    private double weight;
    private String unitOfMeasure;
    private int minimumQuantity;
    private double acquisitionCost;
    private boolean active;
    private Integer supplierId;
    private String supplierName;
    private String warehousePosition;
    private double vatRate;

    public Product(int id, String code, String name, String description, double price, int quantity) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.description = description;
        this.price = price;
        this.salePriceExVat = price;
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
        this.vatRate = vatRate;
        this.salePriceExVat = SalePrice.exVatFromIncVat(price, vatRate);
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
    }

    public int getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    /** Compatibility getter: customer-facing price, VAT included. */
    public double getPrice() { return price; }
    public double getSalePriceIncVat() { return price; }
    public double getSalePriceExVat() { return salePriceExVat; }
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

    public void setId(int id) { this.id = id; }
    public void setCode(String code) { this.code = code; }
    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setPrice(double price) {
        this.price = Numbers.nonNegative(price);
        this.salePriceExVat = SalePrice.exVatFromIncVat(this.price, vatRate);
    }
    public void setSalePriceIncVat(double price) { setPrice(price); }
    public void setSalePriceExVat(double price) {
        this.salePriceExVat = Numbers.nonNegative(price);
        this.price = SalePrice.incVatFromExVat(this.salePriceExVat, vatRate);
    }
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
    public void setVatRate(double vatRate) {
        this.vatRate = Numbers.vat(vatRate);
        this.salePriceExVat = SalePrice.exVatFromIncVat(price, this.vatRate);
    }
}
