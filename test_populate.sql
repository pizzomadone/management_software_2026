-- Simple test script to verify INSERT works
PRAGMA foreign_keys = OFF;

-- Clear test data
DELETE FROM customers;

-- Test 1: Simple INSERT with WITH RECURSIVE
INSERT INTO customers (first_name, last_name, email, phone, address)
SELECT
    'TestName' || x,
    'TestSurname' || x,
    'test' || x || '@test.com',
    '+1 555-' || printf('%04d', 1000 + x),
    'Test Street ' || x || ', Test City'
FROM (
    WITH RECURSIVE numbers(x) AS (
        SELECT 1
        UNION ALL
        SELECT x+1 FROM numbers WHERE x < 10
    )
    SELECT x FROM numbers
);

-- Show results
SELECT 'Test Results:' as result
UNION ALL
SELECT 'Customers inserted: ' || COUNT(*) FROM customers;

-- Show first 5 customers
SELECT '--- First 5 customers ---' as result
UNION ALL
SELECT first_name || ' ' || last_name || ' (' || email || ')'
FROM customers
LIMIT 5;
