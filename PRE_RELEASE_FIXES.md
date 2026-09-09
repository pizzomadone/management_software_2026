# Correzioni pre-release WareStat

Il ramo `codex/pre-release-corrections-20260908` contiene gli interventi sui 49 rilievi dell’audit della revisione `8fe23b2d67769fb3c3ed5ae046588c563fd897cb`.

La verifica automatica finale ha superato **62 gruppi di regressione**, comprendenti tutte le 16 transizioni degli ordini cliente e le 25 degli ordini fornitore. I test eseguono i metodi applicativi su database sintetici, verificano i dati salvati e controllano testo e coordinate dei PDF. La fattura di 50 righe e i PDF con Unicode sono stati anche renderizzati e ispezionati. L’esito completo dei gruppi è in [tests/verification.txt](tests/verification.txt).

## Prima della pubblicazione

Restano da eseguire la compilazione dei pacchetti di distribuzione e il collaudo nativo su Windows e macOS: avvio, dialoghi, browser incorporato, stampa, installazione/aggiornamento e permessi delle cartelle. La suite eseguita usa Java 21 e Swing senza finestre native. I controlli automatici non sostituiscono queste prove.

Gli archivi già danneggiati dai comportamenti precedenti richiedono una riconciliazione con documenti e conteggi attendibili. Il ramo previene nuovi errori nei flussi corretti, ma non ricostruisce quantità perdute né anagrafiche storiche già sovrascritte. Verificare una copia dell’archivio reale prima dell’adozione; nessun archivio di produzione è stato usato per questi test.

Le migrazioni rifiutano riferimenti orfani, valori numerici fuori dominio, fornitori legacy ambigui e numeri d’ordine fornitore duplicati. Conservare l’archivio precedente per tornare alla vecchia versione: lo schema aggiornato non è destinato a essere riaperto con versioni precedenti.

## Regole applicative rese esplicite

- Le quantità delle righe sono interi positivi fino a 2.147.483.647. Le giacenze fisiche possono diventare negative dopo lo storno di merce già utilizzata o confermando uno scarico insufficiente.
- I prezzi unitari conservano la precisione rappresentabile dal database. Gli importi di riga e la relativa IVA sono arrotondati a due decimali con HALF_UP; i totali sommano questi importi. Limite per importo: 999.999.999.999,99. Un valore non rappresentabile viene rifiutato.
- Ogni database usa una sola valuta. Il cambio dell’etichetta non esegue conversioni. Gli importi sono gestiti a due decimali anche nelle valute con convenzioni diverse.
- La valorizzazione del magazzino usa il metodo del costo standard e somma, per i prodotti attivi, `max(giacenza, 0) × costo corrente di catalogo`, arrotondando ogni prodotto a due decimali. Non implementa FIFO o costo medio dei lotti. La dashboard mostra separatamente il valore al prezzo di vendita: non è un ricavo realizzato. Le giacenze negative restano salvate e visibili, ma non sottraggono valore agli altri prodotti.
- Le vendite mensili sommano gli ordini cliente completati con data nel mese corrente, IVA esclusa, indipendentemente da fatture e pagamenti. Il fatturato mensile somma gli imponibili, IVA esclusa, delle sole fatture emesse o pagate con data nel mese. I due indicatori sono separati e non si sommano fra loro.
- Il margine potenziale di magazzino è `100 × (valore di vendita - valore al costo standard) / valore di vendita`. Pesa i prodotti secondo il loro valore di vendita in magazzino, non secondo una media semplice delle percentuali. Con valore di vendita zero è indicato come non disponibile. È una stima sulla merce presente, non l’utile storico delle vendite.
- Le date numeriche sono millisecondi dall’epoca Unix. Le date/ore ISO testuali SQLite sono interpretate come UTC; le date senza ora sono giorni di calendario. Query e interfaccia usano lo stesso fuso Java.
- I prezzi degli ordini fornitore sono proposti quando si aggiunge il prodotto. Dopo modifiche di data o quantità, Apply Price List permette di ricalcolarli senza sovrascrivere implicitamente prezzi concordati.
- Le bozze delle fatture possono aggiornare le anagrafiche al salvataggio. Le copie legacy indicano che i dati sono stati acquisiti durante l’aggiornamento. Un PDF con totali salvati incoerenti viene rifiutato prima di sostituire un file esistente.
- I backup automatici operano mentre WareStat è aperto. Il ripristino conserva una copia dell’archivio precedente. I vecchi file registrati senza prova della gestione da parte di WareStat non vengono cancellati automaticamente.
- Alcuni testi senza traduzione verificata rimangono in inglese. I pulsanti di cronologia Discover restano disabilitati se il motore non espone informazioni affidabili; il comportamento va verificato sulle piattaforme native.

## Calcoli e spiegazioni delle KPI

La precedente etichetta «Monthly Revenue» mostrava esclusivamente le fatture, nascondendo le vendite completate non ancora fatturate. La valorizzazione al costo poteva risultare zero con prezzi di vendita compilati ma costi di acquisto zero; inoltre sottraeva le giacenze negative dal valore degli altri articoli. La nuova etichetta lunga del valore di magazzino non aveva una chiave di traduzione completa.

La dashboard distingue ora valore al costo standard, valore al prezzo di vendita, vendite mensili degli ordini e fatturato mensile al netto dell’IVA. Segnala i prodotti presenti con costo zero, senza inventare un costo, e conta le giacenze negative anche quando non è impostata una scorta minima. Gli avvisi danno priorità alle giacenze negative e gestiscono anche deficit superiori al massimo intero a 32 bit. Il report di magazzino usa la stessa formula al costo e conserva in tabella le quantità effettive. Ogni scheda KPI ha un piccolo punto interrogativo in basso a destra: passando il mouse si apre un tooltip con formula, dati inclusi ed esclusi, periodo e casi particolari. Il tooltip resta leggibile durante la sosta sul punto interrogativo; uscendo con il mouse o nascondendo il componente viene ripristinata la durata ordinaria degli altri tooltip. Etichette e otto spiegazioni sono disponibili in tutte le sette lingue e si aggiornano al cambio della lingua. I prodotti esauriti comprendono quantità zero e negative, in accordo con il report di magazzino; le prenotazioni non riducono la quantità fisica usata per la valorizzazione e i conteggi. Se un aggiornamento dei dati fallisce, le KPI e il report non conservano importi precedenti o parziali come se fossero attuali.

I nove gruppi aggiunti per le KPI verificano costi zero e modifica del costo, magazzino vuoto, prodotti inattivi, arrotondamento per articolo, giacenze negative e ripristino della disponibilità, vendite senza fattura, modifica/salvataggio ripetuto/annullamento degli ordini, distinzione fra imponibile e IVA, cambio di stato delle fatture, confini mensili con date numeriche e testuali in tre fusi orari, cambio delle sette lingue, ripresa dopo un errore di lettura, margine ponderato con perdite e denominatore zero, conteggi delle scorte e ordini aperti di mesi precedenti. Le schede e i tooltip sono stati renderizzati fuori schermo; i controlli verificano la presenza degli otto punti interrogativi sotto gli importi, la registrazione dei tooltip al passaggio del mouse, traduzioni complete, testo a capo e assenza di etichette tagliate a 1200 pixel di larghezza.

Il confronto con la documentazione non identifica un unico metodo di costo usato da tutti i gestionali. La scelta per WareStat è il costo standard, definito nella scheda prodotto e indicato come predefinito nella [documentazione Odoo 18](https://www.odoo.com/documentation/18.0/applications/inventory_and_mrp/inventory/product_management/inventory_valuation/inventory_valuation_config.html). [Zoho Inventory](https://www.zoho.com/en-ng/inventory/help/reports/inventory-valuation-reports.html) offre invece FIFO e costo medio mobile. Questi metodi richiedono una gestione dei costi dei movimenti e non sono stati simulati usando gli ordini storici di WareStat. Il fatturato netto segue la distinzione tra vendite al netto delle imposte e totali con imposte descritta nei [report vendite di Zoho Inventory](https://www.zoho.com/us/inventory/help/reports/sales-reports.html). Le quantità negative escluse dal valore e segnalate a parte sono una regola operativa esplicita di WareStat.

## Scorte minime, statistiche e font PDF

La scorta minima è salvata soltanto in `minimum_stock.minimum_quantity`, insieme a `reorder_quantity`, `lead_time_days`, fornitore preferito e note. La colonna duplicata in `products` e i tre trigger di sincronizzazione sono stati eliminati dalla migrazione 010. I nuovi database nascono senza la colonna duplicata. Anagrafica prodotti, ricerca, dashboard, avvisi e report leggono la stessa soglia tramite collegamento a `minimum_stock`; una riga assente o una soglia zero disattiva il controllo della scorta minima, mantenendo gli avvisi separati sulle giacenze negative.

Anche il campo nella scheda prodotto salva in `minimum_stock`, creando la riga se manca e aggiornando soltanto la soglia se esiste. Non sovrascrive quantità di riordino, lead time, fornitore preferito o note. La transazione comprende anagrafica, eventuale rettifica di giacenza e soglia: un errore annulla tutte le scritture. L’editor di magazzino modifica direttamente la stessa riga.

Per i database esistenti, la migrazione conserva le impostazioni già presenti in `minimum_stock`, incluso uno zero esplicito. Trasferisce la soglia da `products` soltanto per i prodotti senza impostazioni di magazzino; per questi la quantità di riordino iniziale è zero e gli altri campi restano non impostati. In caso di discordanza nei vecchi archivi prevale quindi la soglia di magazzino. La rimozione della colonna, il trasferimento e l’aggiornamento dei trigger numerici avvengono nella transazione delle migrazioni. Valori da trasferire nulli, negativi, frazionari o fuori intervallo causano il rollback, senza conversioni implicite. Questa versione richiede lo schema 010: le versioni precedenti non devono aprire il database aggiornato.

I test verificano lettura e modifica da entrambi gli editor, creazione di prodotti, salvataggi ripetuti, conservazione delle altre impostazioni, ricerca, dashboard, report, eliminazione di prodotti senza storico e rollback delle scritture. Le prove di migrazione coprono schemi precedenti e successivi alla sincronizzazione 007, soglie presenti solo in anagrafica, conflitti, zero, massimo intero, valori invalidi, aggiornamenti ripetuti e un errore simulato dopo la rimozione della colonna: vengono ripristinati schema, valori e trigger precedenti. Lo script dei dati dimostrativi usa anch’esso `minimum_stock` per le soglie.

Nel ramo corretto `AdvancedStatsPanel.loadSalesData` e `loadProductsData` filtravano già gli ordini `Completed`. Un nuovo scenario verifica tutti e tre i periodi selezionabili, esclude righe di ordini nuovi, in corso, annullati e fuori periodo, confronta ricavi e quantità e controlla che una vendita successivamente annullata sparisca dai grafici dopo l’aggiornamento.

L’errore «Missing PDF font: DejaVuSans.ttf» indica un avvio senza il font nel classpath. `PdfWriter` cerca prima le risorse incorporate, poi la cartella `resources/fonts` del progetto e infine Liberation Sans, già inclusa nella libreria PDFBox. Il font alternativo è incorporato nel PDF e usa il peso normale anche per i titoli; caratteri non rappresentabili continuano a causare un errore esplicito, senza alterare il PDF precedente. Ant verifica la presenza dei due font DejaVu e della licenza prima della compilazione e nei JAR normale e offuscato; è stata rimossa la duplicazione delle risorse nel JAR. Tre processi Java separati verificano l’esportazione da JAR con font, da JAR senza font con cartella risorse disponibile e da JAR senza font fuori dal progetto. I controlli leggono nuovamente accenti, nomi polacchi, quantità e importi e verificano il font effettivamente incorporato.

## Collegamento ai rilievi dell’audit

La gravità riportata è quella del problema originario. “Intervento” descrive il comportamento introdotto, comprese le mitigazioni e i limiti sopra indicati.

| Rilievo | Gravità originaria | File/classi e metodi principali | Intervento |
| --- | --- | --- | --- |
| R01 | CRITICO | StockManager.deleteOrder; OrderDialog.saveOrder | Storno dei dati precedenti e applicazione del nuovo stato nella stessa transazione; il salvataggio ripetuto non moltiplica gli scarichi. |
| R02 | CRITICO | StockManager.deleteOrder; OrderDialog.saveOrder | Lo storno usa prodotti e quantità persistiti prima della modifica. Verificate tutte le 16 transizioni di stato cliente. |
| R03 | CRITICO | StockManager.requireManualMovement; WarehouseMovementDialog.saveMovement | I movimenti legati a ordini, acquisti e rettifiche si modificano dal documento di origine; tolto ORDER dai tipi manuali nuovi. |
| R04 | CRITICO | BackupManager.cleanOldBackups | La conservazione agisce soltanto sulle copie registrate come gestite da WareStat. |
| R05 | CRITICO | BackupManager.importBackup, deleteBackup | Rifiuto del database attivo, anche tramite alias del file; importazione in una copia indipendente. |
| R06 | CRITICO | DatabaseIntegrity.validate; DatabaseManager.initDatabase | Verifiche di struttura, integrità, foreign key e domini numerici; errore esplicito all’avvio. Un file esistente vuoto non viene inizializzato come nuovo archivio. |
| R07 | CRITICO | BackupManager.snapshot, importDatabase | Snapshot SQLite con VACUUM INTO: inclusi i dati confermati nel WAL. |
| R08 | ALTO | InvoiceDialog.productIdForRow, saveInvoice | Identificativo prodotto conservato nelle righe, risoluzione verificata e nessuna riga saltata per una cache incompleta. |
| R09 | ALTO | SupplierOrderDialog.productIdForRow, saveOrder | Le righe conservano il prodotto originario anche dopo la modifica del fornitore in anagrafica. |
| R10 | ALTO | Numbers.positiveQuantity; editor dei documenti | Righe con quantità intere positive; rifiuto di zero, negativi, campi vuoti, frazioni e overflow. |
| R11 | ALTO | Numbers; DatabaseIntegrity.installGuards | Rifiuto di NaN, infinito, importi negativi, IVA fuori 0-100 e precisione non conservabile; protezioni anche nelle scritture SQL. |
| R12 | ALTO | Numbers.commitEdits; editor di ordini e fatture | Chiusura dell’editor della cella, validazione e ricalcolo prima della persistenza. |
| R13 | ALTO | StockManager.getStockEffectQuantity | Un ordine annullato non aggiunge disponibilità virtuale al controllo delle scorte. |
| R14 | ALTO | SupplierPricing.price; SupplierOrderDialog; MultiSupplierOrderDialog | Listino scelto per fornitore, prodotto, data ordine e quantità. Apply Price List ricalcola i prezzi dopo modifiche di data/quantità, previa conferma della sostituzione dei prezzi inseriti. |
| R15 | ALTO | SupplierPriceListWindow.loadPriceListItem, deleteSelectedPrice | Selezione, modifica e cancellazione basate sull’ID della voce, non su nome e codice fornitore. |
| R16 | ALTO | Numbers.lineTotal; ProductSelectionDialog; editor dei documenti | Conservata la precisione del prezzo unitario; arrotondamento dell’importo di riga. Verificati 1.000 pezzi a 0,004 e 3 pezzi a 0,335. |
| R17 | ALTO | Numbers.tax; InvoiceDialog; InvoicePDFGenerator | Aliquota conservata senza troncamento e IVA arrotondata per riga in modo coerente con il totale salvato. |
| R18 | ALTO | BackupManager.startAutomaticBackups, isAutomaticBackupDue; MainWindow | Timer durante l’esecuzione dell’applicazione, intervallo dall’ultimo successo, esclusione delle sovrapposizioni e ritardo dei nuovi tentativi dopo un errore. |
| R19 | ALTO | SalesReportPanel; AdvancedStatsPanel; DashboardPanel | Vendite degli ordini limitate a Completed; fatturato mensile calcolato sugli imponibili, IVA esclusa, delle fatture Issued/Paid. |
| R20 | ALTO | DashboardPanel.loadABCAnalysis | La classificazione considera la quota cumulata prima di aggiungere il prodotto: il prodotto dominante rimane A. |
| R21 | ALTO | AdvancedStatsPanel.loadProductsData, drawProductsChart | Denominatore esteso a tutti i prodotti; quota residua esplicita nel grafico. L’ordinamento viene ricollegato al modello dopo il ricaricamento. |
| R22 | ALTO | DatabaseMigrationManager.normalizeStockThresholds; ProductDialog; MinimumStockDialog; ProductsPanel; DashboardPanel; WarehousePanel; WarehouseReportPanel | Soglia unica in minimum_stock con quantità di riordino e lead time. Migrazione 010 elimina la colonna in products e i trigger di sincronizzazione, preservando le impostazioni. Zero o riga assente disattivano la soglia. |
| R23 | ALTO | DateUtils.sqlDate, installSqlFunctions; WarehouseReportPanel | Date dei report convertite nello stesso fuso Java usato dall’interfaccia, per timestamp numerici e testo SQLite. |
| R24 | ALTO | DatabaseMigrationManager.migrateSupplierData | Aggiunta del riferimento fornitore senza eliminare la tabella prodotti; mapping ambiguo rifiutato, riferimenti ai prodotti preservati. |
| R25 | ALTO | InvoiceSnapshots; InvoiceDialog; InvoicesPanel; InvoicePDFGenerator | Copia dei dati di cliente, azienda, logo, prodotti e valuta per la fattura. Le bozze aggiornano i dati al salvataggio; le fatture pregresse migrate sono identificate come legacy. |
| R26 | ALTO | ApplicationLock; DatabaseManager.beginTransaction; OrderDialog | Una sola istanza sull’archivio; transazioni IMMEDIATE prima del controllo delle scorte. Connessione chiusa se BEGIN fallisce e lascia lo stato JDBC non riutilizzabile. |
| R27 | ALTO | PdfWriter; InvoicePDFGenerator.generatePDF | Interruzioni di pagina, intestazioni ripetute, descrizioni complete e totali leggibili. Verificata una fattura di 50 righe. |
| R28 | ALTO | PdfWriter; resources/fonts; build.xml | Font Unicode incorporati con licenza inclusa e controlli di packaging; recupero dalla cartella risorse o da PDFBox se manca il font nel classpath. Verificati nomi polacchi e accenti anche nel caso alternativo. Caratteri non supportati producono errore senza sostituire il PDF precedente. |
| R29 | MEDIO | Numbers.quantity; StockManager; DatabaseIntegrity | Interi verificati, somme lette senza cambio silenzioso di segno e rollback degli incrementi oltre il limite. |
| R30 | MEDIO | SalesReportPanel.loadReportData | Rimosso il limite implicito di 1.000 ordini; verificato un report con 1.005 ordini completati. |
| R31 | ALTO | I18n.localize, tr | Traduzione dei testi dell’interfaccia e degli enum dichiarati; codici, nomi e altri valori aziendali restano letterali. |
| R32 | MEDIO | DashboardPanel.loadKPIData; WarehouseReportPanel.loadProductsData; Numbers.stockValue | Valorizzazione coerente al costo corrente di catalogo, per prodotti attivi con giacenza positiva, con arrotondamento per prodotto. Deficit esclusi dal valore e segnalati separatamente; in dashboard anche valore al prezzo di vendita e avviso sui costi zero. |
| R33 | MEDIO | DashboardPanel.loadKPIData | Margine potenziale ponderato sul valore della merce presente. Costi tutti zero e valore di vendita positivo danno 100%; valore di vendita zero dà un margine non disponibile. Le perdite restano negative. |
| R34 | ALTO | DatabaseCurrency; SettingsPanel; BackupManager | Valuta legata al database, modifica bloccata in presenza di importi e richiesta della valuta originale per gli archivi che ne sono privi. |
| R35 | MEDIO | WarehousePanel.loadNotificationsData, markSelectedNotifications | ID della notifica conservato nel modello e usato nell’aggiornamento, anche con testi identici. |
| R36 | MEDIO | DateUtils.parseDate | Parsing completo e non permissivo: rifiuto di giorni impossibili, testo residuo e formati incompleti. |
| R37 | MEDIO | TableSorterUtil.enableSorting | Confronto numerico per quantità, percentuali e importi; verificati importi formattati in locale italiano e statunitense. |
| R38 | MEDIO | Csv.cell, finish; esportatori CSV | Virgolette raddoppiate, delimitatori e ritorni a capo preservati; errore di scrittura rilevato prima del messaggio di successo. |
| R39 | MEDIO | MainWindow.showPanel; pannelli e report | Ricaricamento entrando nelle schermate e prima delle esportazioni; un filtro non valido impedisce l’esportazione con intestazioni incoerenti. |
| R40 | MEDIO | ProductDialog.saveProduct | Rettifica registrata per la sola differenza rispetto alla quantità persistita, nella stessa transazione; salvataggio invariato senza nuovi movimenti. |
| R41 | ALTO | BackupManager.importDatabase | Preparazione e verifica su file temporaneo, copia di sicurezza conservata e sostituzione atomica. Su filesystem senza sostituzione atomica l’operazione fallisce. |
| R42 | ALTO | DatabaseMigrationManager.migrateReleaseIntegrity; CustomersPanel | Foreign key della fattura verso l’ordine ricostruita; scollegamento esplicito delle fatture prima della cancellazione degli ordini del cliente. |
| R43 | MEDIO | SuppliersPanel.deleteSelectedSupplier | Considerati anche i prodotti associati al fornitore e indicata la loro disassociazione nel percorso di cancellazione. |
| R44 | BASSO | ReportPDFGenerator.generatePDF | Report vuoto con una pagina e indicazione dell’assenza di dati. |
| R45 | BASSO | SwingWebView.pollDocumentState | Pulsanti basati sulle capacità di navigazione effettivamente esposte. Restano disabilitati nei motori senza Navigation API; prova nativa ancora necessaria. |
| R46 | MEDIO | PdfWriter; InvoicePDFGenerator.generatePDF | Codici stampati integralmente; verificato EAN-13 di 13 cifre, senza ellissi. |
| R47 | ALTO | I18n.tr; resources/i18n | Eliminate le traduzioni che cambiavano il significato del rischio. Quattro avvisi critici ritradotti nelle sei lingue; per le altre chiavi mancanti resta il testo inglese completo. |
| R48 | ALTO | DocumentNumbers.nextSupplierNumber; migrazione 007 | Progressivo estratto dopo il prefisso anno, assegnato nella transazione e protetto da indice univoco. Duplicati storici richiedono riconciliazione. |
| R49 | ALTO | DashboardPanel.loadKPIData; DateUtils | Confini mensili azzerati ai millisecondi e confronto sul giorno normalizzato; inclusa la fattura al primo istante del mese. |

## Ripetere le verifiche

Requisiti: JDK 21 e le librerie già presenti in `lib`. Su macOS/Linux, dalla cartella del progetto:

```bash
bash tests/run-regressions.sh
```

Lo script usa `JAVA_HOME` se impostato, altrimenti `java` e `javac` nel PATH. Crea una cartella distinta in `build`, isola la directory utente e conserva log e PDF sintetici. Termina con errore se fallisce un gruppo.

È disponibile anche il target `ant test`, con gli stessi test e risorse. Il target è stato aggiunto a `build.xml`; in questo ambiente è stata eseguita la versione Bash, mentre Ant non era installato. Il packaging include i font e la loro licenza.

I file `tests/RegressionTest.java`, `tests/ExtendedRegressionTest.java` e `tests/OperationalRegressionTest.java` contengono gli scenari; `tests/PdfPackagingProbe.java` verifica l’esportazione PDF nei processi Java isolati. L’uso di `Unsafe` è limitato alla suite per costruire i dialoghi senza finestre native; JDK 21 emette tre avvisi in compilazione.
