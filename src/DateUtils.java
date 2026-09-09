// File: DateUtils.java
import java.sql.*;
import java.text.ParseException;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DateUtils {
    private static final SimpleDateFormat[] DATE_FORMATS = {
        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS"),
        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SS"),
        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.S"),
        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"),
        new SimpleDateFormat("yyyy-MM-dd"),
        new SimpleDateFormat("dd/MM/yyyy HH:mm:ss"),
        new SimpleDateFormat("dd/MM/yyyy"),
        new SimpleDateFormat("MM/yyyy")
    };
    
    public static final SimpleDateFormat DEFAULT_FORMAT = new SimpleDateFormat("dd/MM/yyyy");
    public static final SimpleDateFormat DATETIME_FORMAT = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
    
    /**
     * Parse a date from ResultSet handling various formats
     */
    public static Date parseDate(ResultSet rs, String columnName) throws SQLException {
        return parseStored(rs.getObject(columnName));
    }
    static Date parseStored(Object raw) {
        if(raw==null)return null;
        String text=raw.toString().trim();
        if(raw instanceof Number || text.matches("-?\\d+")) {
            try {
                long millis=Long.parseLong(text);
                int year=java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).getYear();
                return year>=1 && year<=9999?new Date(millis):null;
            } catch(IllegalArgumentException e) {return null;}
        }
        for(SimpleDateFormat format:DATE_FORMATS) {
            try {
                SimpleDateFormat current=(SimpleDateFormat)format.clone();
                current.setTimeZone(current.toPattern().startsWith("yyyy-MM-dd HH:")?java.util.TimeZone.getTimeZone("UTC"):java.util.TimeZone.getDefault());
                return parseDate(text,current);
            }catch(ParseException e) { /* Try the next persisted format. */ }
        }
        return null;
    }

    /**
     * Format a date safely
     */
    public static String formatDate(Date date, SimpleDateFormat format) {
        if (date == null) return "";
        synchronized (format) {
            return format.format(date);
        }
    }
    
    /**
     * Format a date with default format
     */
    public static String formatDate(Date date) {
        return formatDate(date, DEFAULT_FORMAT);
    }
    
    /**
     * Parse a date string safely
     */
    public static Date parseDate(String dateStr, SimpleDateFormat format) throws ParseException {
        if (dateStr == null || dateStr.trim().isEmpty()) return null;
        synchronized (format) {
            SimpleDateFormat strict = (SimpleDateFormat) format.clone();
            strict.setLenient(false);
            String input = dateStr.trim();
            ParsePosition position = new ParsePosition(0);
            Date value = strict.parse(input, position);
            if (value == null || position.getIndex() != input.length() || !strict.format(value).equals(input))
                throw new ParseException("Invalid date: " + input, Math.max(0, position.getErrorIndex()));
            return value;
        }
    }
    
    public static String sqlDate(String column) {
        if (!column.matches("[A-Za-z_][A-Za-z0-9_.]*")) throw new IllegalArgumentException("Invalid date column");
        return "warestat_date(" + column + ")";
    }

    /** Convert dates using the same Java zone as the UI, independent of SQLite/libc localtime. */
    static void installSqlFunctions(Connection c) throws SQLException {
        org.sqlite.Function.create(c,"warestat_date",new org.sqlite.Function() {
            @Override protected void xFunc() throws SQLException {
                Date date=parseStored(value_text(0));
                if(date==null){result();return;}
                result(new SimpleDateFormat("yyyy-MM-dd",java.util.Locale.ROOT).format(date));
            }
        },1);
        // SQLite ROUND works on binary floating point and disagrees with WareStat's
        // accounting HALF_UP rule on values such as 2.675. Reports must use the same
        // line calculation as order/invoice persistence.
        org.sqlite.Function.create(c,"warestat_line_total",new org.sqlite.Function() {
            @Override protected void xFunc() throws SQLException {
                try {
                    result(Numbers.lineTotal(Numbers.positiveQuantity(value_long(0)),
                        Numbers.nonNegative(value_double(1))));
                } catch(IllegalArgumentException e) {
                    throw new SQLException("Invalid monetary line in report",e);
                }
            }
        },2);
    }

    /**
     * Convert Date to SQL Date
     */
    public static java.sql.Date toSqlDate(Date date) {
        return date != null ? new java.sql.Date(date.getTime()) : null;
    }
    
    /**
     * Convert Date to SQL Timestamp
     */
    public static Timestamp toSqlTimestamp(Date date) {
        return date != null ? new Timestamp(date.getTime()) : null;
    }
}