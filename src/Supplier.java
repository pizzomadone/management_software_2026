
// File: Supplier.java
public class Supplier {
    private int id;
    private String companyName;
    private String vatNumber;
    private String taxCode;
    private String address;
    private String phone;
    private String email;
    private String certifiedEmail;
    private String website;
    private String notes;

    public Supplier(int id, String companyName, String vatNumber, String taxCode,
                   String address, String phone, String email, String certifiedEmail,
                   String website, String notes) {
        this.id = id;
        this.companyName = companyName;
        this.vatNumber = vatNumber;
        this.taxCode = taxCode;
        this.address = address;
        this.phone = phone;
        this.email = email;
        this.certifiedEmail = certifiedEmail;
        this.website = website;
        this.notes = notes;
    }

    // Getters
    public int getId() { return id; }
    public String getCompanyName() { return companyName; }
    public String getVatNumber() { return vatNumber; }
    public String getTaxCode() { return taxCode; }
    public String getAddress() { return address; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getCertifiedEmail() { return certifiedEmail; }
    public String getWebsite() { return website; }
    public String getNotes() { return notes; }

    // Setters
    public void setId(int id) { this.id = id; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public void setVatNumber(String vatNumber) { this.vatNumber = vatNumber; }
    public void setTaxCode(String taxCode) { this.taxCode = taxCode; }
    public void setAddress(String address) { this.address = address; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setEmail(String email) { this.email = email; }
    public void setCertifiedEmail(String certifiedEmail) { this.certifiedEmail = certifiedEmail; }
    public void setWebsite(String website) { this.website = website; }
    public void setNotes(String notes) { this.notes = notes; }
}
