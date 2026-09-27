/**
 * Product/supplier relationship retained under the historical class name for source compatibility.
 * There are no price-list periods or quantity tiers anymore.
 */
public class SupplierPriceList {
    private final int id;
    private final int supplierId;
    private final int productId;
    private final String supplierProductCode;
    private final double price;
    private final boolean preferred;
    private final String notes;

    public SupplierPriceList(int id, int supplierId, int productId, String supplierProductCode,
                             double price, boolean preferred, String notes) {
        this.id=id;
        this.supplierId=supplierId;
        this.productId=productId;
        this.supplierProductCode=supplierProductCode;
        this.price=price;
        this.preferred=preferred;
        this.notes=notes;
    }

    public int getId(){return id;}
    public int getSupplierId(){return supplierId;}
    public int getProductId(){return productId;}
    public String getSupplierProductCode(){return supplierProductCode;}
    public double getPrice(){return price;}
    public boolean isPreferred(){return preferred;}
    public String getNotes(){return notes;}
}
