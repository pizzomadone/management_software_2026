import java.sql.*;

public class CompanyData {
    private static CompanyData instance;
    
    private String companyName;
    private String vatNumber;
    private String taxCode;
    private String address;
    private String city;
    private String postalCode;
    private String country;
    private String phone;
    private String email;
    private String website;
    private String logoPath;
    
    private CompanyData() {
        loadFromDatabase();
    }
    
    public static CompanyData getInstance() {
        if (instance == null) {
            instance = new CompanyData();
        }
        return instance;
    }
    
    private void loadFromDatabase() {
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            String query = "SELECT * FROM company_data LIMIT 1";
            
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {
                if (rs.next()) {
                    this.companyName = rs.getString("company_name");
                    this.vatNumber = rs.getString("vat_number");
                    this.taxCode = rs.getString("tax_code");
                    this.address = rs.getString("address");
                    this.city = rs.getString("city");
                    this.postalCode = rs.getString("postal_code");
                    this.country = rs.getString("country");
                    this.phone = rs.getString("phone");
                    this.email = rs.getString("email");
                    this.website = rs.getString("website");
                    this.logoPath = rs.getString("logo_path");
                } else {
                    // Set default values
                    setDefaults();
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            setDefaults();
        }
    }
    
    private void setDefaults() {
        this.companyName = "";
        this.vatNumber = "";
        this.taxCode = "";
        this.address = "";
        this.city = "";
        this.postalCode = "";
        this.country = "Italy";
        this.phone = "";
        this.email = "";
        this.website = "";
        this.logoPath = "";
    }
    
    public boolean saveToDatabase() {
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            
            // Check if record exists
            String checkQuery = "SELECT COUNT(*) FROM company_data";
            boolean exists = false;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(checkQuery)) {
                if (rs.next() && rs.getInt(1) > 0) {
                    exists = true;
                }
            }
            
            String query;
            if (exists) {
                query = """
                    UPDATE company_data SET
                        company_name = ?, vat_number = ?, tax_code = ?,
                        address = ?, city = ?, postal_code = ?, country = ?,
                        phone = ?, email = ?, website = ?, logo_path = ?
                """;
            } else {
                query = """
                    INSERT INTO company_data (
                        company_name, vat_number, tax_code, address,
                        city, postal_code, country, phone, email, website, logo_path
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
            }
            
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setString(1, companyName);
                pstmt.setString(2, vatNumber);
                pstmt.setString(3, taxCode);
                pstmt.setString(4, address);
                pstmt.setString(5, city);
                pstmt.setString(6, postalCode);
                pstmt.setString(7, country);
                pstmt.setString(8, phone);
                pstmt.setString(9, email);
                pstmt.setString(10, website);
                pstmt.setString(11, logoPath);
                
                pstmt.executeUpdate();
                return true;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    // Getters
    public String getCompanyName() { return companyName != null ? companyName : ""; }
    public String getVatNumber() { return vatNumber != null ? vatNumber : ""; }
    public String getTaxCode() { return taxCode != null ? taxCode : ""; }
    public String getAddress() { return address != null ? address : ""; }
    public String getCity() { return city != null ? city : ""; }
    public String getPostalCode() { return postalCode != null ? postalCode : ""; }
    public String getCountry() { return country != null ? country : ""; }
    public String getPhone() { return phone != null ? phone : ""; }
    public String getEmail() { return email != null ? email : ""; }
    public String getWebsite() { return website != null ? website : ""; }
    public String getLogoPath() { return logoPath != null ? logoPath : ""; }
    
    // Setters
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public void setVatNumber(String vatNumber) { this.vatNumber = vatNumber; }
    public void setTaxCode(String taxCode) { this.taxCode = taxCode; }
    public void setAddress(String address) { this.address = address; }
    public void setCity(String city) { this.city = city; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
    public void setCountry(String country) { this.country = country; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setEmail(String email) { this.email = email; }
    public void setWebsite(String website) { this.website = website; }
    public void setLogoPath(String logoPath) { this.logoPath = logoPath; }
    
    public String getFullAddress() {
        StringBuilder sb = new StringBuilder();
        if (!getAddress().isEmpty()) sb.append(getAddress());
        if (!getCity().isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(getCity());
        }
        if (!getPostalCode().isEmpty()) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(getPostalCode());
        }
        if (!getCountry().isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(getCountry());
        }
        return sb.toString();
    }
    
    public boolean isValid() {
        return companyName != null && !companyName.trim().isEmpty();
    }
}