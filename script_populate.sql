-- Script corretto per popolare il database WorkGenio
PRAGMA foreign_keys = OFF;
PRAGMA synchronous = OFF;
PRAGMA journal_mode = MEMORY;

-- 1. CLIENTI (1000 record)
INSERT INTO clienti (nome, cognome, email, telefono, indirizzo) 
SELECT 
    CASE (ABS(RANDOM()) % 20)
        WHEN 0 THEN 'Mario' WHEN 1 THEN 'Luigi' WHEN 2 THEN 'Giuseppe' 
        WHEN 3 THEN 'Antonio' WHEN 4 THEN 'Francesco' WHEN 5 THEN 'Alessandro'
        WHEN 6 THEN 'Andrea' WHEN 7 THEN 'Marco' WHEN 8 THEN 'Matteo'
        WHEN 9 THEN 'Roberto' WHEN 10 THEN 'Anna' WHEN 11 THEN 'Giulia'
        WHEN 12 THEN 'Laura' WHEN 13 THEN 'Chiara' WHEN 14 THEN 'Sara'
        WHEN 15 THEN 'Valentina' WHEN 16 THEN 'Elena' WHEN 17 THEN 'Martina'
        WHEN 18 THEN 'Silvia' ELSE 'Paola'
    END,
    CASE (ABS(RANDOM()) % 25)
        WHEN 0 THEN 'Rossi' WHEN 1 THEN 'Ferrari' WHEN 2 THEN 'Russo'
        WHEN 3 THEN 'Bianchi' WHEN 4 THEN 'Romano' WHEN 5 THEN 'Gallo'
        WHEN 6 THEN 'Conti' WHEN 7 THEN 'Bruno' WHEN 8 THEN 'Costa'
        WHEN 9 THEN 'Ricci' WHEN 10 THEN 'Fontana' WHEN 11 THEN 'Santoro'
        WHEN 12 THEN 'Caruso' WHEN 13 THEN 'Ferrara' WHEN 14 THEN 'Martini'
        WHEN 15 THEN 'Leone' WHEN 16 THEN 'Vitale' WHEN 17 THEN 'Serra'
        WHEN 18 THEN 'Villa' WHEN 19 THEN 'De Rosa' WHEN 20 THEN 'Conte'
        WHEN 21 THEN 'Marchetti' WHEN 22 THEN 'Palmieri' WHEN 23 THEN 'Benedetti'
        ELSE 'Fiore'
    END,
    'cliente' || ABS(RANDOM()) || '@email.com',
    '+39 320' || (1000000 + ABS(RANDOM() % 8999999)),
    'Via Roma ' || (1 + ABS(RANDOM() % 100)) || ', Milano'
FROM (
    WITH RECURSIVE numbers(x) AS (
        SELECT 1
        UNION ALL
        SELECT x+1 FROM numbers WHERE x < 1000
    )
    SELECT x FROM numbers
);

-- 2. FORNITORI (50 record)
INSERT INTO fornitori (ragione_sociale, partita_iva, codice_fiscale, indirizzo, telefono, email, pec, sito_web, note)
SELECT 
    'Fornitore ' || ROW_NUMBER() OVER() || ' S.r.l.',
    printf('%011d', ABS(RANDOM()) % 100000000000),
    printf('%016d', ABS(RANDOM()) % 10000000000000000),
    'Via Industria ' || (1 + ABS(RANDOM() % 50)) || ', Milano',
    '+39 02' || (1000000 + ABS(RANDOM() % 8999999)),
    'info@fornitore' || ROW_NUMBER() OVER() || '.com',
    'pec@fornitore' || ROW_NUMBER() OVER() || '.pec.it',
    'www.fornitore' || ROW_NUMBER() OVER() || '.it',
    'Fornitore affidabile'
FROM (
    WITH RECURSIVE numbers(x) AS (
        SELECT 1
        UNION ALL
        SELECT x+1 FROM numbers WHERE x < 50
    )
    SELECT x FROM numbers
);

-- 3. PRODOTTI (500 record)
INSERT INTO prodotti (codice, nome, descrizione, prezzo, quantita)
SELECT 
    'PROD' || printf('%06d', ROW_NUMBER() OVER()),
    CASE (ABS(RANDOM()) % 20)
        WHEN 0 THEN 'Resistore' WHEN 1 THEN 'Condensatore' WHEN 2 THEN 'Transistor'
        WHEN 3 THEN 'Diodo LED' WHEN 4 THEN 'Circuito Integrato' WHEN 5 THEN 'Connettore'
        WHEN 6 THEN 'Cavo USB' WHEN 7 THEN 'Alimentatore' WHEN 8 THEN 'Sensore'
        WHEN 9 THEN 'Display LCD' WHEN 10 THEN 'Altoparlante' WHEN 11 THEN 'Microfono'
        WHEN 12 THEN 'Interruttore' WHEN 13 THEN 'Motore' WHEN 14 THEN 'Tastiera'
        WHEN 15 THEN 'Mouse' WHEN 16 THEN 'Monitor' WHEN 17 THEN 'Router'
        WHEN 18 THEN 'Smartphone' WHEN 19 THEN 'Tablet' ELSE 'Computer'
    END || ' Professional',
    'Prodotto di alta qualità per uso professionale',
    ROUND((10 + ABS(RANDOM() % 990)) / 10.0, 2),
    ABS(RANDOM() % 500)
FROM (
    WITH RECURSIVE numbers(x) AS (
        SELECT 1
        UNION ALL
        SELECT x+1 FROM numbers WHERE x < 500
    )
    SELECT x FROM numbers
);

-- 4. ORDINI (2000 record)
INSERT INTO ordini (cliente_id, data_ordine, stato, totale)
SELECT 
    (1 + ABS(RANDOM() % 1000)),
    datetime('now', '-' || ABS(RANDOM() % 365) || ' days'),
    CASE (ABS(RANDOM()) % 5)
        WHEN 0 THEN 'New' WHEN 1 THEN 'In Progress' 
        WHEN 2 THEN 'Completed' WHEN 3 THEN 'Completed'
        ELSE 'Cancelled'
    END,
    ROUND((50 + ABS(RANDOM() % 950)) / 10.0, 2)
FROM (
    WITH RECURSIVE numbers(x) AS (
        SELECT 1
        UNION ALL
        SELECT x+1 FROM numbers WHERE x < 2000
    )
    SELECT x FROM numbers
);

-- 5. DETTAGLI ORDINI (4000 record)
INSERT INTO dettagli_ordine (ordine_id, prodotto_id, quantita, prezzo_unitario)
SELECT 
    (1 + ABS(RANDOM() % 2000)),
    (1 + ABS(RANDOM() % 500)),
    (1 + ABS(RANDOM() % 10)),
    ROUND((5 + ABS(RANDOM() % 95)) / 10.0, 2)
FROM (
    WITH RECURSIVE numbers(x) AS (
        SELECT 1
        UNION ALL
        SELECT x+1 FROM numbers WHERE x < 4000
    )
    SELECT x FROM numbers
);

-- 6. FATTURE (1500 record)
DELETE FROM numerazione_fatture;
INSERT INTO numerazione_fatture (anno, ultimo_numero) VALUES (2024, 1500);

INSERT INTO fatture (numero, data, cliente_id, imponibile, iva, totale, stato)
SELECT 
    '2024/' || printf('%04d', ROW_NUMBER() OVER()),
    datetime('now', '-' || ABS(RANDOM() % 365) || ' days'),
    (1 + ABS(RANDOM() % 1000)),
    ROUND((100 + ABS(RANDOM() % 900)) / 10.0, 2),
    ROUND(imponibile * 0.22, 2),
    ROUND(imponibile * 1.22, 2),
    CASE (ABS(RANDOM()) % 4)
        WHEN 0 THEN 'Draft' WHEN 1 THEN 'Issued' 
        WHEN 2 THEN 'Paid' ELSE 'Cancelled'
    END
FROM (
    WITH RECURSIVE numbers(x) AS (
        SELECT 1
        UNION ALL
        SELECT x+1 FROM numbers WHERE x < 1500
    )
    SELECT x, ROUND((100 + ABS(RANDOM() % 900)) / 10.0, 2) as imponibile FROM numbers
);

-- 7. DETTAGLI FATTURE (3000 record)
INSERT INTO dettagli_fattura (fattura_id, prodotto_id, quantita, prezzo_unitario, aliquota_iva, totale)
SELECT 
    (1 + ABS(RANDOM() % 1500)),
    (1 + ABS(RANDOM() % 500)),
    (1 + ABS(RANDOM() % 5)),
    ROUND((10 + ABS(RANDOM() % 90)) / 10.0, 2),
    22.0,
    ROUND(quantita * prezzo_unitario, 2)
FROM (
    WITH RECURSIVE numbers(x) AS (
        SELECT 1
        UNION ALL
        SELECT x+1 FROM numbers WHERE x < 3000
    )
    SELECT x, (1 + ABS(RANDOM() % 5)) as quantita, 
           ROUND((10 + ABS(RANDOM() % 90)) / 10.0, 2) as prezzo_unitario 
    FROM numbers
);

-- 8. MOVIMENTI MAGAZZINO (3000 record)
INSERT INTO movimenti_magazzino (prodotto_id, data, tipo, quantita, causale, documento_numero, documento_tipo, note)
SELECT 
    (1 + ABS(RANDOM() % 500)),
    datetime('now', '-' || ABS(RANDOM() % 180) || ' days'),
    CASE (ABS(RANDOM()) % 2) WHEN 0 THEN 'INWARD' ELSE 'OUTWARD' END,
    (1 + ABS(RANDOM() % 20)),
    CASE (ABS(RANDOM()) % 4)
        WHEN 0 THEN 'PURCHASE' WHEN 1 THEN 'SALE' 
        WHEN 2 THEN 'INVENTORY' ELSE 'OTHER'
    END,
    'DOC-' || (10000 + ABS(RANDOM() % 89999)),
    'DDT',
    'Movimento automatico'
FROM (
    WITH RECURSIVE numbers(x) AS (
        SELECT 1
        UNION ALL
        SELECT x+1 FROM numbers WHERE x < 3000
    )
    SELECT x FROM numbers
);

-- 9. SCORTE MINIME (200 record)
INSERT INTO scorte_minime (prodotto_id, quantita_minima, quantita_riordino, lead_time_giorni, fornitore_preferito_id, note)
SELECT 
    x,
    (5 + ABS(RANDOM() % 20)),
    (20 + ABS(RANDOM() % 80)),
    (3 + ABS(RANDOM() % 14)),
    (1 + ABS(RANDOM() % 50)),
    'Prodotto strategico'
FROM (
    WITH RECURSIVE numbers(x) AS (
        SELECT 1
        UNION ALL
        SELECT x+1 FROM numbers WHERE x < 200
    )
    SELECT x FROM numbers
);

-- 10. NOTIFICHE MAGAZZINO (100 record)
INSERT INTO notifiche_magazzino (prodotto_id, data, tipo, messaggio, stato)
SELECT 
    (1 + ABS(RANDOM() % 500)),
    datetime('now', '-' || ABS(RANDOM() % 30) || ' days'),
    CASE (ABS(RANDOM()) % 3)
        WHEN 0 THEN 'MIN_STOCK' WHEN 1 THEN 'OUT_OF_STOCK' 
        ELSE 'REORDER'
    END,
    'Scorta sotto il minimo. Riordinare urgentemente.',
    CASE (ABS(RANDOM()) % 3)
        WHEN 0 THEN 'NEW' WHEN 1 THEN 'READ' ELSE 'HANDLED'
    END
FROM (
    WITH RECURSIVE numbers(x) AS (
        SELECT 1
        UNION ALL
        SELECT x+1 FROM numbers WHERE x < 100
    )
    SELECT x FROM numbers
);

-- Aggiorna totali
UPDATE ordini SET totale = (
    SELECT COALESCE(SUM(quantita * prezzo_unitario), 0)
    FROM dettagli_ordine WHERE ordine_id = ordini.id
);

-- Riabilita controlli
PRAGMA foreign_keys = ON;
PRAGMA synchronous = NORMAL;
PRAGMA journal_mode = WAL;

-- Statistiche
SELECT 'Clienti: ' || COUNT(*) FROM clienti
UNION ALL SELECT 'Fornitori: ' || COUNT(*) FROM fornitori
UNION ALL SELECT 'Prodotti: ' || COUNT(*) FROM prodotti
UNION ALL SELECT 'Ordini: ' || COUNT(*) FROM ordini
UNION ALL SELECT 'Fatture: ' || COUNT(*) FROM fatture;