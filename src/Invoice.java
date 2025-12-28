
// File: Invoice.java
import java.util.Date;
import java.util.List;
import java.util.ArrayList;

public class Invoice {
    private int id;
    private String number;
    private Date date;
    private int customerId;
    private String customerName;
    private double taxableAmount;
    private double vat;
    private double total;
    private String status;
    private List<InvoiceItem> items;

    public Invoice(int id, String number, Date date, int customerId, String customerName,
                  double taxableAmount, double vat, double total, String status) {
        this.id = id;
        this.number = number;
        this.date = date;
        this.customerId = customerId;
        this.customerName = customerName;
        this.taxableAmount = taxableAmount;
        this.vat = vat;
        this.total = total;
        this.status = status;
        this.items = new ArrayList<>();
    }

    // Getters
    public int getId() { return id; }
    public String getNumber() { return number; }
    public Date getDate() { return date; }
    public int getCustomerId() { return customerId; }
    public String getCustomerName() { return customerName; }
    public double getTaxableAmount() { return taxableAmount; }
    public double getVat() { return vat; }
    public double getTotal() { return total; }
    public String getStatus() { return status; }
    public List<InvoiceItem> getItems() { return items; }

    // Setters
    public void setId(int id) { this.id = id; }
    public void setNumber(String number) { this.number = number; }
    public void setDate(Date date) { this.date = date; }
    public void setCustomerId(int customerId) { this.customerId = customerId; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public void setTaxableAmount(double taxableAmount) { this.taxableAmount = taxableAmount; }
    public void setVat(double vat) { this.vat = vat; }
    public void setTotal(double total) { this.total = total; }
    public void setStatus(String status) { this.status = status; }
    public void setItems(List<InvoiceItem> items) { this.items = items; }
}
