/** Payment state is derived from money actually received, never the reverse. */
public final class PaymentAmounts {
    private PaymentAmounts() {}

    public static String status(double paidAmount, double amountDue) {
        double paid = Numbers.nonNegative(paidAmount);
        double due = Numbers.nonNegative(amountDue);
        return paid <= 0 ? "NOT_PAID"
            : paid + 0.004 >= due ? "PAID" : "PARTIALLY_PAID";
    }
}
