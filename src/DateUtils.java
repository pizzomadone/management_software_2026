// File: DateUtils.java
import java.sql.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DateUtils {
    private static final SimpleDateFormat[] DATE_FORMATS = {
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
        try {
            // First try as Timestamp
            Timestamp timestamp = rs.getTimestamp(columnName);
            if (timestamp != null) {
                return new Date(timestamp.getTime());
            }
        } catch (SQLException e) {
            // Ignore and try as string
        }
        
        try {
            String dateStr = rs.getString(columnName);
            if (dateStr != null && !dateStr.isEmpty()) {
                // If it's a number (timestamp millis), convert it
                if (dateStr.matches("\\d+")) {
                    try {
                        long timestampMillis = Long.parseLong(dateStr);
                        return new Date(timestampMillis);
                    } catch (NumberFormatException e) {
                        // Continue with string parsing
                    }
                }
                
                // Try all formats
                for (SimpleDateFormat format : DATE_FORMATS) {
                    try {
                        synchronized (format) {
                            return format.parse(dateStr);
                        }
                    } catch (ParseException e) {
                        // Continue with next format
                    }
                }
            }
        } catch (SQLException e) {
            // Log the error if needed
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
            return format.parse(dateStr.trim());
        }
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