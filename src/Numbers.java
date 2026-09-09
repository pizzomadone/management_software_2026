import java.math.BigDecimal;
import java.math.RoundingMode;
import javax.swing.JTable;

/** Numeric rules shared by document editors and persistence. */
public final class Numbers {
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("999999999999.99");
    private Numbers() {}

    public static BigDecimal decimal(Object value) {
        if (value == null) throw new IllegalArgumentException("A numeric value is required");
        String text = value.toString().trim().replace(',', '.');
        try { return new BigDecimal(text); }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number: " + value);
        }
    }

    public static int quantity(Object value) {
        try { return decimal(value).intValueExact(); }
        catch (ArithmeticException e) {
            throw new IllegalArgumentException("Quantity must be a whole number within the supported range");
        }
    }

    public static int positiveQuantity(Object value) {
        int result = quantity(value);
        if (result <= 0) throw new IllegalArgumentException("Quantity must be greater than zero");
        return result;
    }

    public static double nonNegative(Object value) {
        BigDecimal result = decimal(value);
        if (result.signum() < 0 || result.compareTo(MAX_AMOUNT) > 0)
            throw new IllegalArgumentException("Value must be between 0 and " + MAX_AMOUNT);
        double stored=result.doubleValue();
        if(!Double.isFinite(stored) || BigDecimal.valueOf(stored).compareTo(result)!=0)
            throw new IllegalArgumentException("The value has more precision than the database can preserve");
        return stored;
    }

    public static double vat(Object value) {
        double result = nonNegative(value);
        if (result > 100) throw new IllegalArgumentException("VAT must be between 0 and 100");
        return result;
    }

    public static double money(BigDecimal value) {
        BigDecimal rounded = value.setScale(2, RoundingMode.HALF_UP);
        if (rounded.abs().compareTo(MAX_AMOUNT) > 0)
            throw new IllegalArgumentException("Amount exceeds the supported limit: " + MAX_AMOUNT);
        return rounded.doubleValue();
    }

    public static double lineTotal(int quantity, double price) {
        return money(BigDecimal.valueOf(quantity).multiply(BigDecimal.valueOf(price)));
    }

    /** Value physical stock on hand; shortages are reported separately, not netted against other stock. */
    public static double stockValue(int quantity, double unitValue) {
        return lineTotal(Math.max(0, quantity), nonNegative(unitValue));
    }

    public static double tax(double taxable, double rate) {
        return money(BigDecimal.valueOf(taxable).multiply(BigDecimal.valueOf(rate)).movePointLeft(2));
    }

    public static double add(double left, double right) {
        return money(BigDecimal.valueOf(left).add(BigDecimal.valueOf(right)));
    }

    public static void commitEdits(JTable table) {
        if (table != null && table.isEditing() && !table.getCellEditor().stopCellEditing())
            throw new IllegalArgumentException("Complete the current cell before saving");
    }
}
