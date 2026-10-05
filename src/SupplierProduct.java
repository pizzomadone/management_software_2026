/** One supplier's current purchasing relationship for a product. */
public class SupplierProduct {
    private final int id;
    private final int supplierId;
    private final int productId;
    private final String supplierProductCode;
    private final double purchasePrice;
    private final boolean preferred;
    private final String notes;

    public SupplierProduct(int id,int supplierId,int productId,String supplierProductCode,
                           double purchasePrice,boolean preferred,String notes) {
        this.id=id;
        this.supplierId=supplierId;
        this.productId=productId;
        this.supplierProductCode=supplierProductCode;
        this.purchasePrice=purchasePrice;
        this.preferred=preferred;
        this.notes=notes;
    }

    public int getId(){return id;}
    public int getSupplierId(){return supplierId;}
    public int getProductId(){return productId;}
    public String getSupplierProductCode(){return supplierProductCode;}
    public double getPurchasePrice(){return purchasePrice;}
    public boolean isPreferred(){return preferred;}
    public String getNotes(){return notes;}
}
