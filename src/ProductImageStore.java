import javax.imageio.*;
import javax.imageio.stream.ImageOutputStream;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.List;

/**
 * Product JPEGs live in SQLite BLOBs. Uncommitted photos are held in memory
 * until the enclosing product transaction succeeds. Only small, bounded,
 * derived thumbnails may be cached in RAM.
 */
public final class ProductImageStore {
    public record ProductImage(int id, int productId, String filename, boolean primary, int sortOrder) {}

    public static final int MAX_IMAGES_PER_PRODUCT = 3;
    public static final int MAX_LONG_SIDE = 800;
    public static final int THUMB_LONG_SIDE = 180;
    public static final int MAX_IMAGE_BYTES = 150 * 1024;

    private static final ProductImageStore INSTANCE = new ProductImageStore();
    private final Map<String, byte[]> pending = new HashMap<>();
    private final Map<String, BufferedImage> thumbnailCache = new LinkedHashMap<>(128, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> eldest) {
            return size() > 128;
        }
    };

    private ProductImageStore() {}
    public static ProductImageStore getInstance() { return INSTANCE; }

    public List<ProductImage> list(int productId) throws SQLException {
        return listWithConnection(DatabaseManager.getInstance().getConnection(), productId);
    }

    public String primaryFilename(int productId) throws SQLException {
        Connection c = DatabaseManager.getInstance().getConnection();
        try (PreparedStatement p = c.prepareStatement(
                "SELECT filename FROM product_images WHERE product_id=? ORDER BY is_primary DESC,sort_order,id LIMIT 1")) {
            p.setInt(1, productId);
            try (ResultSet r = p.executeQuery()) { return r.next() ? r.getString(1) : null; }
        }
    }

    public List<String> filenamesForProduct(Connection c, int productId) throws SQLException {
        List<String> names = new ArrayList<>();
        try (PreparedStatement p = c.prepareStatement(
                "SELECT filename FROM product_images WHERE product_id=? ORDER BY sort_order,id")) {
            p.setInt(1, productId);
            try (ResultSet r = p.executeQuery()) { while (r.next()) names.add(r.getString(1)); }
        }
        return names;
    }

    public String storeOptimizedImage(File source) throws IOException {
        if (source == null || !source.isFile()) throw new IOException("Image file not found");
        BufferedImage decoded = ImageIO.read(source);
        if (decoded == null || decoded.getWidth() <= 0 || decoded.getHeight() <= 0)
            throw new IOException("Unsupported or invalid image");
        BufferedImage oriented = applyOrientation(scaleToLongSide(decoded, MAX_LONG_SIDE),
            readExifOrientation(source));
        byte[] encoded = encodeOptimized(toRgb(oriented), MAX_IMAGE_BYTES);
        String filename = UUID.randomUUID() + ".jpg";
        synchronized (this) { pending.put(filename, encoded); }
        return filename;
    }

    public ProductImage addImage(int productId, File source, boolean primary) throws Exception {
        String filename = storeOptimizedImage(source);
        try {
            Connection c = DatabaseManager.getInstance().getConnection();
            DatabaseManager.beginTransaction(c);
            try {
                ProductImage result = attachStoredImage(c, productId, filename, primary);
                c.commit();
                return result;
            } catch (Exception e) {
                c.rollback();
                throw e;
            } finally { c.setAutoCommit(true); }
        } finally { deleteFiles(filename); }
    }

    public ProductImage attachStoredImage(Connection c, int productId, String filename, boolean primary)
            throws SQLException {
        byte[] data = storedOrPending(c, filename);
        int count, nextSort;
        try (PreparedStatement p = c.prepareStatement(
                "SELECT COUNT(*),COALESCE(MAX(sort_order),-1)+1 FROM product_images WHERE product_id=?")) {
            p.setInt(1, productId);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) throw new SQLException("Unable to count product images");
                count = r.getInt(1);
                nextSort = r.getInt(2);
            }
        }
        if (count >= MAX_IMAGES_PER_PRODUCT) throw new SQLException("Maximum three product photos");
        boolean makePrimary = primary || count == 0;
        if (makePrimary) {
            try (PreparedStatement p = c.prepareStatement("UPDATE product_images SET is_primary=0 WHERE product_id=?")) {
                p.setInt(1, productId); p.executeUpdate();
            }
        }
        int id;
        try (PreparedStatement p = c.prepareStatement(
                "INSERT INTO product_images(product_id,filename,is_primary,sort_order,image_data) VALUES(?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            p.setInt(1, productId);
            p.setString(2, safeName(filename));
            p.setInt(3, makePrimary ? 1 : 0);
            p.setInt(4, nextSort);
            p.setBytes(5, data);
            p.executeUpdate();
            try (ResultSet keys = p.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Missing product image identifier");
                id = keys.getInt(1);
            }
        }
        return new ProductImage(id, productId, filename, makePrimary, nextSort);
    }

    public void attachStoredImages(Connection c, int productId, List<String> filenames, String primaryFilename)
            throws SQLException {
        if (filenames == null || filenames.isEmpty()) return;
        if (filenames.size() > MAX_IMAGES_PER_PRODUCT) throw new SQLException("Maximum three product photos");
        String primary = primaryFilename == null ? filenames.get(0) : primaryFilename;
        for (String filename : filenames) attachStoredImage(c, productId, filename, filename.equals(primary));
    }

    public void replaceStoredImages(Connection c, int productId, List<String> filenames,
            String primaryFilename) throws SQLException {
        List<String> ordered = filenames == null ? List.of() : new ArrayList<>(filenames);
        if (ordered.size() > MAX_IMAGES_PER_PRODUCT) throw new SQLException("Maximum three product photos");
        Map<String, byte[]> contents = new LinkedHashMap<>();
        for (String name : ordered) {
            String safe = safeName(name);
            if (contents.putIfAbsent(safe, storedOrPending(c, safe)) != null)
                throw new SQLException("Duplicate product image");
        }
        String primary = ordered.isEmpty() ? null :
            (primaryFilename != null && contents.containsKey(primaryFilename) ? primaryFilename : ordered.get(0));
        try (PreparedStatement p = c.prepareStatement("DELETE FROM product_images WHERE product_id=?")) {
            p.setInt(1, productId); p.executeUpdate();
        }
        try (PreparedStatement p = c.prepareStatement(
                "INSERT INTO product_images(product_id,filename,is_primary,sort_order,image_data) VALUES(?,?,?,?,?)")) {
            for (int i=0; i<ordered.size(); i++) {
                String filename = safeName(ordered.get(i));
                p.setInt(1, productId);
                p.setString(2, filename);
                p.setInt(3, filename.equals(primary) ? 1 : 0);
                p.setInt(4, i);
                p.setBytes(5, contents.get(filename));
                p.addBatch();
            }
            p.executeBatch();
        }
    }

    private synchronized byte[] pendingBytes(String filename) { return pending.get(filename); }

    public byte[] imageBytes(String filename) throws SQLException {
        if (filename == null || filename.isBlank()) return null;
        Connection c = DatabaseManager.getInstance().getConnection();
        return storedOrPending(c, filename);
    }

    private byte[] storedOrPending(Connection c, String filename) throws SQLException {
        filename = safeName(filename);
        byte[] bytes = pendingBytes(filename);
        if (bytes != null) return bytes;
        try (PreparedStatement p = c.prepareStatement("SELECT image_data FROM product_images WHERE filename=? LIMIT 1")) {
            p.setString(1, filename);
            try (ResultSet r = p.executeQuery()) {
                if (r.next() && r.getBytes(1) != null) return r.getBytes(1);
            }
        }
        throw new SQLException("Stored product image is missing: " + filename);
    }

    public void setPrimary(int productId, int imageId) throws SQLException {
        Connection c = DatabaseManager.getInstance().getConnection();
        DatabaseManager.beginTransaction(c);
        try {
            try (PreparedStatement check = c.prepareStatement(
                    "SELECT 1 FROM product_images WHERE id=? AND product_id=?")) {
                check.setInt(1,imageId); check.setInt(2,productId);
                try (ResultSet r = check.executeQuery()) {
                    if (!r.next()) throw new SQLException("Product image not found");
                }
            }
            try (PreparedStatement clear = c.prepareStatement("UPDATE product_images SET is_primary=0 WHERE product_id=?")) {
                clear.setInt(1,productId); clear.executeUpdate();
            }
            try (PreparedStatement set = c.prepareStatement(
                    "UPDATE product_images SET is_primary=1 WHERE id=? AND product_id=?")) {
                set.setInt(1,imageId); set.setInt(2,productId);
                if (set.executeUpdate() != 1) throw new SQLException("Product image not found");
            }
            c.commit();
        } catch (SQLException e) { c.rollback(); throw e; }
        finally { c.setAutoCommit(true); }
    }

    public void move(int productId, int imageId, int direction) throws SQLException {
        if (direction != -1 && direction != 1) throw new IllegalArgumentException("Direction must be -1 or 1");
        Connection c = DatabaseManager.getInstance().getConnection();
        DatabaseManager.beginTransaction(c);
        try {
            List<ProductImage> images = listWithConnection(c, productId);
            int index=-1;
            for (int i=0; i<images.size(); i++) if(images.get(i).id()==imageId) index=i;
            int other=index+direction;
            if (index<0 || other<0 || other>=images.size()) { c.rollback(); return; }
            ProductImage a=images.get(index), b=images.get(other);
            try (PreparedStatement p=c.prepareStatement("UPDATE product_images SET sort_order=? WHERE id=?")) {
                p.setInt(1,b.sortOrder()); p.setInt(2,a.id()); p.executeUpdate();
                p.setInt(1,a.sortOrder()); p.setInt(2,b.id()); p.executeUpdate();
            }
            c.commit();
        } catch(SQLException e){ c.rollback(); throw e; }
        finally{ c.setAutoCommit(true); }
    }

    private List<ProductImage> listWithConnection(Connection c,int productId) throws SQLException {
        List<ProductImage> result=new ArrayList<>();
        try(PreparedStatement p=c.prepareStatement(
                "SELECT id,product_id,filename,is_primary,sort_order FROM product_images WHERE product_id=? ORDER BY sort_order,id")){
            p.setInt(1,productId);
            try(ResultSet r=p.executeQuery()){
                while(r.next()) result.add(new ProductImage(
                    r.getInt(1),r.getInt(2),r.getString(3),r.getInt(4)==1,r.getInt(5)));
            }
        }
        return result;
    }

    public void deleteImage(int productId,int imageId) throws SQLException {
        Connection c=DatabaseManager.getInstance().getConnection();
        String filename=null;
        boolean wasPrimary=false;
        DatabaseManager.beginTransaction(c);
        try {
            try(PreparedStatement p=c.prepareStatement("SELECT filename,is_primary FROM product_images WHERE id=? AND product_id=?")){
                p.setInt(1,imageId);p.setInt(2,productId);
                try(ResultSet r=p.executeQuery()){
                    if(!r.next()){ c.rollback(); return; }
                    filename=r.getString(1); wasPrimary=r.getInt(2)==1;
                }
            }
            try(PreparedStatement p=c.prepareStatement("DELETE FROM product_images WHERE id=? AND product_id=?")){
                p.setInt(1,imageId);p.setInt(2,productId);p.executeUpdate();
            }
            normalizeSortOrder(c,productId);
            if(wasPrimary){
                try(PreparedStatement p=c.prepareStatement(
                    "UPDATE product_images SET is_primary=1 WHERE id=(SELECT id FROM product_images WHERE product_id=? ORDER BY sort_order,id LIMIT 1)")){
                    p.setInt(1,productId);p.executeUpdate();
                }
            }
            c.commit();
        }catch(SQLException e){ c.rollback();throw e; }
        finally{ c.setAutoCommit(true); }
        if(filename!=null) deleteFiles(filename);
    }

    private void normalizeSortOrder(Connection c,int productId) throws SQLException {
        List<Integer> ids=new ArrayList<>();
        try(PreparedStatement p=c.prepareStatement("SELECT id FROM product_images WHERE product_id=? ORDER BY sort_order,id")){
            p.setInt(1,productId);
            try(ResultSet r=p.executeQuery()){ while(r.next()) ids.add(r.getInt(1)); }
        }
        try(PreparedStatement p=c.prepareStatement("UPDATE product_images SET sort_order=? WHERE id=?")){
            for(int i=0;i<ids.size();i++){p.setInt(1,i);p.setInt(2,ids.get(i));p.addBatch();}
            p.executeBatch();
        }
    }

    /** Release only temporary in-memory images and derived thumbnails; BLOB deletion is transactional SQL. */
    public void deleteNamedFiles(Collection<String> filenames) {
        if (filenames == null) return;
        for(String filename:filenames) deleteFiles(filename);
    }

    public synchronized void deleteFiles(String filename) {
        pending.remove(filename);
        thumbnailCache.remove(filename);
    }

    public ImageIcon icon(String filename,int maxSide){
        if(filename==null||filename.isBlank()||maxSide<=0)return null;
        try {
            BufferedImage image = decode(filename, maxSide<=THUMB_LONG_SIDE);
            if(image==null)return null;
            return new ImageIcon(scaleToLongSide(image,maxSide));
        }catch(Exception e){return null;}
    }

    public ImageIcon iconCover(String filename,int width,int height){
        if(filename==null||filename.isBlank()||width<=0||height<=0)return null;
        try{
            BufferedImage source=decode(filename,true);
            if(source==null)return null;
            double scale=Math.max(width/(double)source.getWidth(),height/(double)source.getHeight());
            int sw=Math.max(width,(int)Math.ceil(source.getWidth()*scale));
            int sh=Math.max(height,(int)Math.ceil(source.getHeight()*scale));
            BufferedImage scaled=new BufferedImage(sw,sh,BufferedImage.TYPE_INT_RGB);
            Graphics2D sg=scaled.createGraphics();
            try{
                sg.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                sg.drawImage(source,0,0,sw,sh,null);
            }finally{sg.dispose();}
            BufferedImage cropped=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
            Graphics2D cg=cropped.createGraphics();
            try{cg.drawImage(scaled,-(sw-width)/2,-(sh-height)/2,null);}
            finally{cg.dispose();}
            return new ImageIcon(cropped);
        }catch(Exception e){return null;}
    }

    private BufferedImage decode(String filename,boolean thumbnail) throws IOException,SQLException{
        if(thumbnail) {
            synchronized(this){ BufferedImage cached=thumbnailCache.get(filename);if(cached!=null)return cached; }
        }
        byte[] bytes=imageBytes(filename);
        if(bytes==null)return null;
        BufferedImage decoded=ImageIO.read(new ByteArrayInputStream(bytes));
        if(decoded==null)throw new IOException("Invalid JPEG BLOB: "+filename);
        if(!thumbnail)return decoded;
        BufferedImage small=toRgb(scaleToLongSide(decoded,THUMB_LONG_SIDE));
        synchronized(this){ thumbnailCache.put(filename,small); }
        return small;
    }

    public synchronized void rebuildThumbnails(){ thumbnailCache.clear(); }
    public synchronized void removeOrphanFiles(){ thumbnailCache.clear(); }

    public Set<String> referencedFilenames() throws SQLException {
        Set<String> names=new LinkedHashSet<>();
        Connection c=DatabaseManager.getInstance().getConnection();
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery(
                "SELECT filename FROM product_images ORDER BY product_id,sort_order,id")){
            while(r.next())names.add(safeName(r.getString(1)));
        }
        return names;
    }

    /**
     * Upgrade a legacy database whose product_images rows reference external JPEGs.
     * Call after adding image_data in a staging database, never on unprotected live data.
     */
    public static void embedLegacyImages(Connection c,Path directory) throws SQLException,IOException {
        List<Map.Entry<Integer,String>> missing=new ArrayList<>();
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery(
                "SELECT id,filename FROM product_images WHERE image_data IS NULL")){
            while(r.next()) missing.add(Map.entry(r.getInt(1),safeName(r.getString(2))));
        }
        try(PreparedStatement p=c.prepareStatement("UPDATE product_images SET image_data=? WHERE id=?")){
            for(var entry:missing){
                Path photo=directory.resolve(entry.getValue()).normalize();
                if(!photo.startsWith(directory.normalize())||!Files.isRegularFile(photo))
                    throw new IOException("Missing legacy product photo: "+entry.getValue());
                byte[] data=Files.readAllBytes(photo);
                if(data.length<4||data.length>10*1024*1024||
                        (data[0]&255)!=0xff||(data[1]&255)!=0xd8)
                    throw new IOException("Invalid legacy JPEG: "+entry.getValue());
                p.setBytes(1,data);p.setInt(2,entry.getKey());p.addBatch();
            }
            p.executeBatch();
        }
    }

    public static void validateEmbeddedImages(Connection c) throws SQLException {
        try(Statement s=c.createStatement();
            ResultSet r=s.executeQuery("SELECT COUNT(*) FROM product_images WHERE image_data IS NULL OR length(image_data)=0")){
            if(r.next()&&r.getInt(1)!=0)throw new SQLException("Database contains product images without JPEG data");
        }
    }

    private static BufferedImage scaleToLongSide(BufferedImage source, int maxLongSide) {
        int longSide = Math.max(source.getWidth(), source.getHeight());
        if (longSide <= maxLongSide) return source;
        double factor = maxLongSide / (double) longSide;
        int width = Math.max(1, (int)Math.round(source.getWidth() * factor));
        int height = Math.max(1, (int)Math.round(source.getHeight() * factor));
        BufferedImage target = new BufferedImage(width, height,
            source.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(source, 0, 0, width, height, null);
        } finally { g.dispose(); }
        return target;
    }

    private static BufferedImage toRgb(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_RGB) return source;
        BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            g.drawImage(source, 0, 0, null);
        } finally { g.dispose(); }
        return rgb;
    }

    private static byte[] encodeOptimized(BufferedImage source, int maxBytes) throws IOException {
        BufferedImage working = source;
        for (int resize = 0; resize < 7; resize++) {
            for (float quality = resize == 0 ? 0.72f : 0.64f; quality >= 0.48f; quality -= 0.04f) {
                byte[] bytes = encodeJpeg(working, quality);
                if (bytes.length <= maxBytes) return bytes;
            }
            int longSide = Math.max(working.getWidth(), working.getHeight());
            if (longSide <= 480) break;
            working = scaleToLongSide(working, Math.max(480, (int)Math.round(longSide * 0.90)));
        }
        byte[] result = encodeJpeg(working, 0.44f);
        if (result.length > maxBytes)
            throw new IOException("Image cannot be compressed below 150 KB without excessive quality loss");
        return result;
    }

    private static byte[] encodeJpeg(BufferedImage image, float quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) throw new IOException("JPEG encoder unavailable");
        ImageWriter writer = writers.next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(output);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(Math.max(0.0f, Math.min(1.0f, quality)));
            }
            writer.write(null, new IIOImage(toRgb(image), null, null), param);
        } finally { writer.dispose(); }
        return bytes.toByteArray();
    }

    private static String safeName(String name) {
        if (name == null) throw new IllegalArgumentException("Missing image filename");
        String base = Path.of(name).getFileName().toString();
        if (base.isBlank() || !base.equals(name) || base.contains(".."))
            throw new IllegalArgumentException("Unsafe image filename");
        return base;
    }

    private static int readExifOrientation(File file) {
        String lower = file.getName().toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".jpg") && !lower.endsWith(".jpeg")) return 1;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            if (in.readUnsignedShort() != 0xFFD8) return 1;
            while (true) {
                int markerStart;
                do { markerStart = in.readUnsignedByte(); } while (markerStart != 0xFF);
                int marker;
                do { marker = in.readUnsignedByte(); } while (marker == 0xFF);
                if (marker == 0xD9 || marker == 0xDA) return 1;
                int length = in.readUnsignedShort();
                if (length < 2) return 1;
                byte[] payload = in.readNBytes(length - 2);
                if (marker == 0xE1 && payload.length >= 14 &&
                        payload[0]=='E' && payload[1]=='x' && payload[2]=='i' && payload[3]=='f' &&
                        payload[4]==0 && payload[5]==0) {
                    int orientation = parseExifOrientation(payload);
                    if (orientation >= 1 && orientation <= 8) return orientation;
                }
            }
        } catch (Exception ignored) { return 1; }
    }

    private static int parseExifOrientation(byte[] exif) {
        try {
            int tiff = 6;
            boolean little = exif[tiff] == 'I' && exif[tiff + 1] == 'I';
            boolean big = exif[tiff] == 'M' && exif[tiff + 1] == 'M';
            if (!little && !big) return 1;
            ByteOrder order = little ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
            ByteBuffer b = ByteBuffer.wrap(exif).order(order);
            int ifd = tiff + b.getInt(tiff + 4);
            if (ifd < tiff || ifd + 2 > exif.length) return 1;
            int entries = Short.toUnsignedInt(b.getShort(ifd));
            for (int i = 0; i < entries; i++) {
                int pos = ifd + 2 + i * 12;
                if (pos + 12 > exif.length) return 1;
                int tag = Short.toUnsignedInt(b.getShort(pos));
                if (tag == 0x0112) return Short.toUnsignedInt(b.getShort(pos + 8));
            }
        } catch (RuntimeException ignored) {}
        return 1;
    }

    private static BufferedImage applyOrientation(BufferedImage source, int orientation) {
        if (orientation <= 1 || orientation > 8) return source;
        int w = source.getWidth(), h = source.getHeight();
        boolean swap = orientation >= 5;
        BufferedImage target = new BufferedImage(swap ? h : w, swap ? w : h,
            source.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int dx=x, dy=y;
                switch (orientation) {
                    case 2 -> { dx = w - 1 - x; dy = y; }
                    case 3 -> { dx = w - 1 - x; dy = h - 1 - y; }
                    case 4 -> { dx = x; dy = h - 1 - y; }
                    case 5 -> { dx = y; dy = x; }
                    case 6 -> { dx = h - 1 - y; dy = x; }
                    case 7 -> { dx = h - 1 - y; dy = w - 1 - x; }
                    case 8 -> { dx = y; dy = w - 1 - x; }
                    default -> {}
                }
                target.setRGB(dx, dy, source.getRGB(x, y));
            }
        }
        return target;
    }
}
