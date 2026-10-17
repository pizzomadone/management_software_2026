import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/**
 * Opt-in, reproducible WareStat load benchmark. NEVER run against a real profile.
 * Launch only via tests/run-load-benchmark.sh (which creates an isolated user.home).
 *
 * Example dataset at 20,000 products:
 * 20,000 independent JPEG BLOB rows, 1,000 suppliers, 5,000 customers,
 * 30,000 customer orders, 15,000 supplier orders, 20,000 draft invoices,
 * 100,000 historical stock movements, 40,000 FIFO lots and 60,000 consumptions.
 *
 * The SQL dataset is structurally valid, but intentionally synthetic; the orders
 * and invoice drafts are not a business-flow correctness / accounting test.
 */
public final class WareStatLoadBenchmark {
    private static final long BASE_TIME = 1760000000000L;
    private static final int CHUNK = 250;
    private static final int IMAGE_VARIANTS = 16;
    private static final List<String> CSV = new ArrayList<>();
    private static final Random SAMPLE = new Random(90354);

    private WareStatLoadBenchmark() {}

    @FunctionalInterface interface SQLAction { void run() throws Exception; }

    private static double record(String name, long operations, SQLAction task) throws Exception {
        long start = System.nanoTime();
        task.run();
        double ms = (System.nanoTime() - start) / 1_000_000.0;
        String row = String.format(Locale.ROOT, "%s,%d,%.3f,%.5f",
            name, operations, ms, operations == 0 ? 0.0 : ms / operations);
        CSV.add(row);
        System.out.println("METRIC " + row);
        return ms;
    }

    private static long scalar(Connection c, String query) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(query)) {
            if (!r.next()) throw new SQLException("No result from " + query);
            return r.getLong(1);
        }
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new IllegalStateException(message);
    }

    private static void flush(Connection c, PreparedStatement... statements) throws SQLException {
        for (PreparedStatement statement : statements) statement.executeBatch();
        c.commit();
    }

    private static void dimensions(Connection c, int suppliers, int customers) throws SQLException {
        c.setAutoCommit(false);
        try (PreparedStatement sp = c.prepareStatement(
                 "INSERT INTO suppliers(id,company_name,vat_number) VALUES(?,?,?)");
             PreparedStatement cu = c.prepareStatement(
                 "INSERT INTO customers(id,first_name,last_name,email) VALUES(?,?,?,?)")) {
            for (int i = 1; i <= suppliers; i++) {
                sp.setInt(1, i);
                sp.setString(2, "Benchmark Supplier " + i);
                sp.setString(3, "LOAD-VAT-" + i);
                sp.addBatch();
                if (i % CHUNK == 0) { sp.executeBatch(); c.commit(); }
            }
            sp.executeBatch(); c.commit();
            for (int i = 1; i <= customers; i++) {
                cu.setInt(1, i);
                cu.setString(2, "Test");
                cu.setString(3, "Customer " + i);
                cu.setString(4, "customer" + i + "@example.invalid");
                cu.addBatch();
                if (i % CHUNK == 0) { cu.executeBatch(); c.commit(); }
            }
            cu.executeBatch(); c.commit();
        } finally { c.setAutoCommit(true); }
    }

    // Real JPEGs, not arbitrary binary arrays: at most 800px and 150KiB.
    // Templates are reused, but all 20,000 product_images rows and filenames differ.
    private static byte[][] photoTemplates() throws IOException {
        byte[][] output = new byte[IMAGE_VARIANTS][];
        for (int variant = 0; variant < IMAGE_VARIANTS; variant++) {
            Random rng = new Random(1200L + variant * 9829L);
            BufferedImage img = new BufferedImage(600, 450, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    int shade = rng.nextInt(256);
                    int red = (shade + x / 4) & 255;
                    int green = (shade + y / 3) & 255;
                    int blue = (shade + (variant * 15)) & 255;
                    img.setRGB(x, y, (red << 16) | (green << 8) | blue);
                }
            }
            float quality = 0.40f + (variant % 4) * 0.025f;
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                 MemoryCacheImageOutputStream outputStream = new MemoryCacheImageOutputStream(bytes)) {
                writer.setOutput(outputStream);
                ImageWriteParam params = writer.getDefaultWriteParam();
                params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                params.setCompressionQuality(quality);
                writer.write(null, new IIOImage(img, null, null), params);
                outputStream.flush();
                output[variant] = bytes.toByteArray();
            } finally { writer.dispose(); }
            check(output[variant].length > 30 * 1024 && output[variant].length <= 150 * 1024,
                "Synthetic JPEG exceeds WareStat size limits: " + output[variant].length);
        }
        return output;
    }

    private static void stockAndProducts(Connection c, int products, int photos, int suppliers,
                                         byte[][] jpeg) throws SQLException {
        c.setAutoCommit(false);
        try (PreparedStatement product = c.prepareStatement(
                 "INSERT INTO products(id,code,name,description,sale_price_ex_vat,sale_price_inc_vat," +
                 "quantity,acquisition_cost,vat_rate,category,warehouse_position) VALUES(?,?,?,?,?,?,?,?,?,?,?)");
             PreparedStatement image = c.prepareStatement(
                 "INSERT INTO product_images(product_id,filename,is_primary,sort_order,image_data) VALUES(?,?,1,0,?)");
             PreparedStatement preferred = c.prepareStatement(
                 "INSERT INTO product_suppliers(product_id,supplier_id,purchase_price,is_preferred) VALUES(?,?,?,1)");
             PreparedStatement minStock = c.prepareStatement(
                 "INSERT INTO minimum_stock(product_id,minimum_quantity,reorder_quantity) VALUES(?,10,25)");
             PreparedStatement move = c.prepareStatement(
                 "INSERT INTO warehouse_movements(id,product_id,date,type,quantity,reason,document_type) " +
                 "VALUES(?,?,?,?,?,'LOAD_TEST','BENCHMARK')");
             PreparedStatement lot = c.prepareStatement(
                 "INSERT INTO inventory_lots(id,product_id,supplier_id,warehouse_movement_id,received_date," +
                 "initial_quantity,remaining_quantity,unit_cost,source_type) VALUES(?,?,?,?,?,?,?,?,?)");
             PreparedStatement consumed = c.prepareStatement(
                 "INSERT INTO inventory_lot_consumptions(lot_id,warehouse_movement_id,quantity,unit_cost) VALUES(?,?,5,12.0)")) {
            for (int id = 1; id <= products; id++) {
                int supplier = ((id - 1) % suppliers) + 1;
                double net = 20.0 + id % 150;
                product.setInt(1, id);
                product.setString(2, String.format(Locale.ROOT, "LOAD-%07d", id));
                product.setString(3, "Synthetic stock item " + id);
                product.setString(4, "Generated merchandise for WareStat load test");
                product.setDouble(5, net);
                product.setDouble(6, Math.round(net * 122.0) / 100.0);
                product.setInt(7, 65); // lot 1: 40-15; lot 2: 40
                product.setDouble(8, 12.0);
                product.setDouble(9, 22.0);
                product.setString(10, "Test Category " + id % 30);
                product.setString(11, "A-" + (id / 100) + "-" + id % 100);
                product.addBatch();

                preferred.setInt(1, id);
                preferred.setInt(2, supplier);
                preferred.setDouble(3, 12.0);
                preferred.addBatch();
                minStock.setInt(1, id);
                minStock.addBatch();

                if (id <= photos) {
                    image.setInt(1, id);
                    image.setString(2, String.format(Locale.ROOT, "load-photo-%07d.jpg", id));
                    image.setBytes(3, jpeg[id % jpeg.length]);
                    image.addBatch();
                }

                long day = BASE_TIME - ((long) id % 150) * 86_400_000L;
                int firstMove = 5 * (id - 1) + 1;
                for (int m = 0; m < 5; m++) {
                    move.setInt(1, firstMove + m);
                    move.setInt(2, id);
                    move.setLong(3, day + 86_400_000L * m);
                    move.setString(4, m < 2 ? "INWARD" : "OUTWARD");
                    move.setInt(5, m < 2 ? 40 : 5);
                    move.addBatch();
                }
                int firstLot = 2 * (id - 1) + 1;
                for (int layer = 0; layer < 2; layer++) {
                    lot.setInt(1, firstLot + layer);
                    lot.setInt(2, id);
                    lot.setInt(3, supplier);
                    lot.setInt(4, firstMove + layer);
                    lot.setLong(5, day + layer * 86_400_000L);
                    lot.setInt(6, 40);
                    lot.setInt(7, layer == 0 ? 25 : 40);
                    lot.setDouble(8, 12.0);
                    lot.setString(9, "MANUAL");
                    lot.addBatch();
                }
                for (int outbound = 2; outbound < 5; outbound++) {
                    consumed.setInt(1, firstLot);
                    consumed.setInt(2, firstMove + outbound);
                    consumed.addBatch();
                }

                if (id % CHUNK == 0 || id == products) {
                    flush(c, product, preferred, minStock, image, move, lot, consumed);
                    if (id % 2000 == 0 || id == products)
                        System.out.println("SEEDED products="+id+"/"+products);
                }
            }
        } finally { c.setAutoCommit(true); }
    }

    private static void documents(Connection c, int products, int suppliers, int customers,
                                  int customerOrders, int supplierOrders, int invoices) throws SQLException {
        c.setAutoCommit(false);
        try (PreparedStatement orders = c.prepareStatement(
                 "INSERT INTO orders(id,customer_id,order_date,status,total_ex_vat,total_inc_vat) VALUES(?,?,?,?,?,?)");
             PreparedStatement orderDetails = c.prepareStatement(
                 "INSERT INTO order_details(order_id,product_id,quantity,sale_unit_price_ex_vat," +
                 "sale_unit_price_inc_vat,vat_rate) VALUES(?,?,?,?,?,22)");
             PreparedStatement supply = c.prepareStatement(
                 "INSERT INTO supplier_orders(id,supplier_id,number,order_date,status,total) VALUES(?,?,?,?,?,?)");
             PreparedStatement supplyDetails = c.prepareStatement(
                 "INSERT INTO supplier_order_details(order_id,product_id,quantity,unit_price,total) VALUES(?,?,10,12,120)");
             PreparedStatement bills = c.prepareStatement(
                 "INSERT INTO invoices(id,number,date,customer_id,taxable_amount,vat,total,status,currency_code)" +
                 " VALUES(?,?,?,?,?,?,?,'Draft','EUR')");
             PreparedStatement billDetails = c.prepareStatement(
                 "INSERT INTO invoice_details(invoice_id,product_id,quantity,sale_unit_price_ex_vat," +
                 "sale_unit_price_inc_vat,vat_rate,total,product_code,product_name) VALUES(?,?,1,20,24.4,22,24.4,?,?)")) {
            for (int id = 1; id <= customerOrders; id++) {
                int product = (id - 1) % products + 1;
                orders.setInt(1,id);
                orders.setInt(2,(id - 1) % customers + 1);
                orders.setLong(3, BASE_TIME - ((long) id % 365) * 86_400_000L);
                orders.setString(4,"New");
                orders.setDouble(5,20);
                orders.setDouble(6,24.4);
                orders.addBatch();
                orderDetails.setInt(1,id);
                orderDetails.setInt(2,product);
                orderDetails.setInt(3,1);
                orderDetails.setDouble(4,20);
                orderDetails.setDouble(5,24.4);
                orderDetails.addBatch();
                if(id % CHUNK == 0 || id == customerOrders) flush(c,orders,orderDetails);
            }
            for (int id = 1; id <= supplierOrders; id++) {
                supply.setInt(1,id);
                supply.setInt(2,(id - 1) % suppliers + 1);
                supply.setString(3,String.format(Locale.ROOT,"LOAD-PO-%07d",id));
                supply.setLong(4,BASE_TIME - ((long) id % 365) * 86_400_000L);
                supply.setString(5,"New");
                supply.setDouble(6,120);
                supply.addBatch();
                supplyDetails.setInt(1,id);
                supplyDetails.setInt(2,(id - 1) % products + 1);
                supplyDetails.addBatch();
                if(id % CHUNK == 0 || id == supplierOrders) flush(c,supply,supplyDetails);
            }
            for (int id = 1; id <= invoices; id++) {
                int product=(id - 1) % products + 1;
                bills.setInt(1,id);
                bills.setString(2,String.format(Locale.ROOT,"LOAD-DRAFT-%07d",id));
                bills.setLong(3,BASE_TIME - ((long) id % 365) * 86_400_000L);
                bills.setInt(4,(id - 1) % customers + 1);
                bills.setDouble(5,20);
                bills.setDouble(6,4.4);
                bills.setDouble(7,24.4);
                bills.addBatch();
                billDetails.setInt(1,id);
                billDetails.setInt(2,product);
                billDetails.setString(3,String.format(Locale.ROOT,"LOAD-%07d",product));
                billDetails.setString(4,"Synthetic stock item " + product);
                billDetails.addBatch();
                if(id % CHUNK == 0 || id == invoices) flush(c,bills,billDetails);
            }
        } finally { c.setAutoCommit(true); }
    }

    private static void verify(Connection c, int p, int photos, int suppliers, int customers,
                               int customerOrders, int supplierOrders, int invoices) throws SQLException {
        check(scalar(c,"SELECT COUNT(*) FROM products")==p,"Wrong product count");
        check(scalar(c,"SELECT COUNT(*) FROM product_images")==photos,"Wrong photo count");
        check(scalar(c,"SELECT COUNT(*) FROM suppliers")==suppliers,"Wrong supplier count");
        check(scalar(c,"SELECT COUNT(*) FROM customers")==customers,"Wrong customer count");
        check(scalar(c,"SELECT COUNT(*) FROM orders")==customerOrders,"Wrong customer order count");
        check(scalar(c,"SELECT COUNT(*) FROM supplier_orders")==supplierOrders,"Wrong supplier order count");
        check(scalar(c,"SELECT COUNT(*) FROM invoices")==invoices,"Wrong invoice count");
        check(scalar(c,"SELECT COUNT(*) FROM warehouse_movements")==p*5L,"Wrong movement count");
        check(scalar(c,"SELECT COUNT(*) FROM inventory_lots")==p*2L,"Wrong FIFO lot count");
        check(scalar(c,"SELECT COUNT(*) FROM inventory_lot_consumptions")==p*3L,"Wrong FIFO consumption count");
        check(scalar(c,"SELECT COUNT(*) FROM products WHERE quantity!=65")==0,"Unexpected stock");
        ProductImageStore.validateEmbeddedImages(c);
        DatabaseIntegrity.validate(c,false);
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("PRAGMA foreign_key_check")){
            check(!r.next(),"Foreign-key violation");
        }
    }

    private static void lookup(Connection c, int p, int samples) throws SQLException {
        try (PreparedStatement q = c.prepareStatement("SELECT id,name,quantity FROM products WHERE code=?")) {
            for (int i=0; i<samples; i++) {
                int id=SAMPLE.nextInt(p)+1;
                q.setString(1,String.format(Locale.ROOT,"LOAD-%07d",id));
                try(ResultSet r=q.executeQuery()){check(r.next()&&r.getInt(1)==id,"Product lookup failed");}
            }
        }
    }

    private static void catalog(Connection c, int p, int samples) throws SQLException {
        try (PreparedStatement q = c.prepareStatement(
                "SELECT p.id,p.code,p.name,p.quantity," +
                "(SELECT filename FROM product_images WHERE product_id=p.id ORDER BY is_primary DESC,sort_order LIMIT 1) " +
                "FROM products p ORDER BY p.name LIMIT 100 OFFSET ?")) {
            for(int i=0;i<samples;i++){
                q.setInt(1,SAMPLE.nextInt(Math.max(1,p-100)));
                try(ResultSet r=q.executeQuery()){int count=0;while(r.next())count++;check(count>0,"Empty catalogue page");}
            }
        }
    }

    private static void search(Connection c, int samples) throws SQLException {
        try (PreparedStatement q = c.prepareStatement(
                "SELECT id FROM products WHERE name LIKE ? ORDER BY name LIMIT 100")) {
            for(int i=0;i<samples;i++){
                q.setString(1,"%stock item "+((i % 10) + 1)+"%");
                try(ResultSet r=q.executeQuery()){check(r.next(),"Search returned no products");}
            }
        }
    }

    private static void updates(Connection c, int p, int samples) throws SQLException {
        try(PreparedStatement q=c.prepareStatement("UPDATE products SET warehouse_position=? WHERE id=?")){
            for(int i=0;i<samples;i++){
                q.setString(1,"BENCH-"+i);
                q.setInt(2,SAMPLE.nextInt(p)+1);
                check(q.executeUpdate()==1,"Product update failed");
            }
        }
    }

    private static void thumbnails(int photos,int samples) {
        ProductImageStore images=ProductImageStore.getInstance();
        for(int i=0;i<samples;i++){
            String name=String.format(Locale.ROOT,"load-photo-%07d.jpg", (i%photos)+1);
            check(images.icon(name,120)!=null,"Could not render thumbnail: "+name);
        }
    }

    private static void dashboard(Connection c, int samples) throws SQLException {
        String[] queries = {
            "SELECT COUNT(*),SUM(quantity*acquisition_cost) FROM products",
            "SELECT status,COUNT(*),SUM(total_inc_vat) FROM orders GROUP BY status",
            "SELECT strftime('%Y-%m',date/1000,'unixepoch'),SUM(total) FROM invoices GROUP BY 1 ORDER BY 1 DESC LIMIT 24",
            "SELECT supplier_id,COUNT(*),SUM(purchase_price) FROM product_suppliers GROUP BY supplier_id ORDER BY 2 DESC LIMIT 20"
        };
        for (int i=0;i<samples;i++){
            try (Statement s=c.createStatement();ResultSet r=s.executeQuery(queries[i%queries.length])){
                while(r.next()) r.getObject(1);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if(args.length!=4)throw new IllegalArgumentException(
            "Expected: <products> <photos> <run_backups:true|false> <restore:true|false>");
        int products=Integer.parseInt(args[0]);
        int photos=Integer.parseInt(args[1]);
        boolean backups=Boolean.parseBoolean(args[2]);
        boolean restore=Boolean.parseBoolean(args[3]);
        check(products>=100&&products<=100000&&photos>=1&&photos<=products,"Invalid benchmark dimensions");
        check(!restore||backups,"Restore requires backups");

        Path home=Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();
        check(Files.isRegularFile(home.resolve("warestat-load-test.marker")),
            "Refusing to run outside a fresh isolated user.home created by run-load-benchmark.sh");
        Path output=Path.of(System.getProperty("test.output")).toAbsolutePath().normalize();
        Files.createDirectories(output);
        Path database=AppConstants.getDatabasePath();
        check(!Files.exists(database),"Refusing to overwrite an existing database: "+database);

        int suppliers=Math.max(1,products/20);
        int customers=Math.max(1,products/4);
        int customerOrders=products*3/2;
        int supplierOrders=products*3/4;
        int invoices=products;
        CSV.add("operation,operations,elapsed_ms,ms_per_operation");
        System.out.printf(Locale.ROOT,"CONFIG products=%d photos=%d suppliers=%d customers=%d orders=%d supplier_orders=%d invoices=%d backups=%s restore=%s%n",
            products,photos,suppliers,customers,customerOrders,supplierOrders,invoices,backups,restore);
        System.out.println("Started "+Instant.now());
        byte[][] jpeg=photoTemplates();
        System.out.println("JPEG sample sizes (bytes): "+jpeg[0].length+", "+jpeg[1].length+", "+jpeg[2].length);
        record("create_schema",1,()->DatabaseManager.createEmptyDatabase(database,"EUR"));
        DatabaseManager manager=DatabaseManager.getInstance();
        record("open_exclusive_database",1,manager::initDatabase);
        Connection c=manager.getConnection();
        final Connection db=c;
        record("seed_customers_suppliers",suppliers+customers,()->dimensions(db,suppliers,customers));
        record("seed_products_photos_lots_movements",products,()->stockAndProducts(db,products,photos,suppliers,jpeg));
        record("seed_orders_invoices",customerOrders+supplierOrders+invoices,
            ()->documents(db,products,suppliers,customers,customerOrders,supplierOrders,invoices));
        record("initial_integrity",1,()->verify(db,products,photos,suppliers,customers,
            customerOrders,supplierOrders,invoices));
        System.out.println("DATABASE_BYTES "+Files.size(database));
        record("find_product_by_code",1000,()->lookup(db,products,1000));
        record("catalog_page_100_rows",100,()->catalog(db,products,100));
        record("search_product_by_name",100,()->search(db,100));
        record("update_product_position",1000,()->updates(db,products,1000));
        record("dashboard_queries",100,()->dashboard(db,100));
        record("first_200_thumbnail_previews",Math.min(photos,200),
            ()->thumbnails(photos,Math.min(photos,200)));
        record("cached_100_thumbnail_previews",Math.min(photos,100),
            ()->thumbnails(photos,Math.min(photos,100)));

        Path backup=null;
        if(backups){
            Path backupFolder=output.resolve("snapshots");
            Files.createDirectories(backupFolder);
            BackupManager b=BackupManager.getInstance();
            b.setBackupDirectory(backupFolder.toString());
            final Path[] file=new Path[1];
            record("backup_with_photos",1,()->file[0]=b.performBackup(true));
            backup=file[0];
            check(WareStatFileHeader.isSealed(backup),"Full backup was not sealed");
            System.out.println("BACKUP_BYTES "+Files.size(backup));
            record("backup_without_photos",1,()->file[0]=b.performBackup(false));
            check(WareStatFileHeader.isSealed(file[0]),"Photo-free backup was not sealed");
            System.out.println("NO_PHOTO_BACKUP_BYTES "+Files.size(file[0]));
            final Path fullBackup=backup;
            record("validate_full_backup",1,()->b.validateDatabase(fullBackup));
            if(restore){
                final Path input=backup;
                record("restore_full_backup",1,()->b.importDatabase(input));
                c=manager.getConnection();
                final Connection restored=c;
                record("integrity_after_restore",1,()->verify(restored,products,photos,
                    suppliers,customers,customerOrders,supplierOrders,invoices));
            }
        }
        record("shutdown_and_seal",1,manager::shutdownAndSeal);
        check(WareStatFileHeader.isSealed(database),"Live database did not seal");
        record("reopen_sealed_database",1,manager::initDatabase);
        check(!WareStatFileHeader.isSealed(database),"Database header not restored");
        check(scalar(manager.getConnection(),"SELECT COUNT(*) FROM products")==products,
            "Reopening lost product records");
        record("final_shutdown",1,manager::shutdownAndSeal);
        System.out.println("FINAL_DATABASE_BYTES "+Files.size(database));
        Runtime mem=Runtime.getRuntime();
        System.out.println("HEAP_USED_BYTES "+(mem.totalMemory()-mem.freeMemory()));
        Files.write(output.resolve("results.csv"),CSV,StandardCharsets.UTF_8);
        Files.writeString(output.resolve("dataset.txt"),String.format(Locale.ROOT,
            "products=%d%nphotos=%d%nsuppliers=%d%ncustomers=%d%norders=%d%nsupplier_orders=%d%ninvoices=%d%nmovements=%d%nlots=%d%nconsumptions=%d%ndatabase_bytes=%d%n",
            products,photos,suppliers,customers,customerOrders,supplierOrders,invoices,
            products*5,products*2,products*3,Files.size(database)));
        System.out.println("PASSED WareStat load benchmark "+Instant.now());
        System.out.println("REPORT "+output.resolve("results.csv"));
    }
}
