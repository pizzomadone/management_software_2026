import java.math.BigDecimal;
import java.math.MathContext;

/** Central conversion rules for customer-facing VAT-inclusive sale prices. */
public final class SalePrice {
    private SalePrice() {}

    private static BigDecimal factor(double vatRate) {
        double rate = Numbers.vat(vatRate);
        return BigDecimal.ONE.add(BigDecimal.valueOf(rate).movePointLeft(2));
    }

    /** Customer-facing unit price, rounded to currency cents. */
    public static double incVatFromExVat(double priceExVat, double vatRate) {
        double net = Numbers.nonNegative(priceExVat);
        return Numbers.money(BigDecimal.valueOf(net).multiply(factor(vatRate)));
    }

    /**
     * Accounting unit price derived from the customer-facing VAT-inclusive price.
     * Unit prices retain useful sub-cent precision; document totals are rounded separately.
     */
    public static double exVatFromIncVat(double priceIncVat, double vatRate) {
        double gross = Numbers.nonNegative(priceIncVat);
        BigDecimal result = BigDecimal.valueOf(gross)
            .divide(factor(vatRate), MathContext.DECIMAL128);
        double stored = result.doubleValue();
        return Numbers.nonNegative(stored);
    }

    public static double lineIncVat(int quantity, double unitPriceIncVat) {
        return Numbers.lineTotal(quantity, Numbers.nonNegative(unitPriceIncVat));
    }

    /** Net line amount derived from the gross line, so the amount paid remains authoritative. */
    public static double lineExVat(int quantity, double unitPriceIncVat, double vatRate) {
        double gross = lineIncVat(quantity, unitPriceIncVat);
        return Numbers.money(BigDecimal.valueOf(gross)
            .divide(factor(vatRate), MathContext.DECIMAL128));
    }

    public static double vatAmount(int quantity, double unitPriceIncVat, double vatRate) {
        BigDecimal gross = BigDecimal.valueOf(lineIncVat(quantity, unitPriceIncVat));
        BigDecimal net = BigDecimal.valueOf(lineExVat(quantity, unitPriceIncVat, vatRate));
        return Numbers.money(gross.subtract(net));
    }
}
