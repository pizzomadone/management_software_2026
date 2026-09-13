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

/** Stores product photos as optimized JPEG files while keeping only metadata in SQLite. */
public final class ProductImageStore {
    public record ProductImage(int id, int productId, String filename, boolean primary, int sortOrder) {}

    public static final int MAX_IMAGES_PER_PRODUCT = 3;
    public static final int MAX_LONG_SIDE = 800;
    public static final int THUMB_LONG_SIDE = 180;
    public static final int MAX_IMAGE_BYTES = 150 * 1024;

    private static final ProductImageStore INSTANCE = new ProductImageStore();

    private ProductImageStore() {}

    public static ProductImageStore getInstance() { return INSTANCE; }

    public Path directory() {
        Path dir = AppConstants.getProductImagesDirectory();
        try { Files.createDirectories(dir); }
        catch (IOException e) { throw new IllegalStateException("Unable to create product image directory", e); }
        return dir;
    }

    public Path imagePath(String filename) { return directory().resolve(safeName(filename)); }
    public Path thumbnailPath(String filename) { return directory().resolve("thumb_" + safeName(filename)); }

    public List<ProductImage> list(int productId) throws SQLException {
        List<ProductImage> result = new ArrayList<>();
        Connection c = DatabaseManager.getInstance().getConnection();
        try (PreparedStatement p = c.prepareStatement(
                "SELECT id,product_id,filename,is_primary,sort_order FROM product_images WHERE product_id=? ORDER BY sort_order,id")) {
            p.setInt(1, productId);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) result.add(new ProductImage(
                    r.getInt("id"), r.getInt("product_id"), r.getString("filename"),
                    r.getInt("is_primary") == 1, r.getInt("sort_order")));
            }
        }
        return result;
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

        BufferedImage scaled = scaleToLongSide(decoded, MAX_LONG_SIDE);
        int orientation = readExifOrientation(source);
        BufferedImage oriented = applyOrientation(scaled, orientation);
        BufferedImage rgb = toRgb(oriented);
        byte[] encoded = encodeOptimized(rgb, MAX_IMAGE_BYTES);

        String filename = UUID.randomUUID() + ".jpg";
        Path target = imagePath(filename);
        Files.write(target, encoded, StandardOpenOption.CREATE_NEW);

        try {
            createThumbnail(filename, rgb);
        } catch (IOException e) {
            Files.deleteIfExists(target);
            throw e;
        }
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
            } finally {
                c.setAutoCommit(true);
            }
        } catch (Exception e) {
            deleteFiles(filename);
            throw e;
        }
    }

    public ProductImage attachStoredImage(Connection c, int productId, String filename, boolean primary)
            throws SQLException {
        filename = safeName(filename);
        if (!Files.isRegularFile(imagePath(filename))) throw new SQLException("Stored product image is missing");

        int count;
        int nextSort;
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
                "INSERT INTO product_images(product_id,filename,is_primary,sort_order) VALUES(?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            p.setInt(1, productId);
            p.setString(2, filename);
            p.setInt(3, makePrimary ? 1 : 0);
            p.setInt(4, nextSort);
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

    public void setPrimary(int productId, int imageId) throws SQLException {
        Connection c = DatabaseManager.getInstance().getConnection();
        DatabaseManager.beginTransaction(c);
        try {
            try (PreparedStatement check = c.prepareStatement(
                    "SELECT 1 FROM product_images WHERE id=? AND product_id=?")) {
                check.setInt(1, imageId); check.setInt(2, productId);
                try (ResultSet r = check.executeQuery()) {
                    if (!r.next()) throw new SQLException("Product image not found");
                }
            }
            try (PreparedStatement clear = c.prepareStatement("UPDATE product_images SET is_primary=0 WHERE product_id=?")) {
                clear.setInt(1, productId); clear.executeUpdate();
            }
            try (PreparedStatement set = c.prepareStatement(
                    "UPDATE product_images SET is_primary=1 WHERE id=? AND product_id=?")) {
                set.setInt(1, imageId); set.setInt(2, productId);
                if (set.executeUpdate() != 1) throw new SQLException("Product image not found");
            }
            c.commit();
        } catch (SQLException e) {
            c.rollback();
            throw e;
        } finally { c.setAutoCommit(true); }
    }

    public void move(int productId, int imageId, int direction) throws SQLException {
        if (direction != -1 && direction != 1) throw new IllegalArgumentException("Direction must be -1 or 1");
        Connection c = DatabaseManager.getInstance().getConnection();
        DatabaseManager.beginTransaction(c);
        try {
            List<ProductImage> images = listWithConnection(c, productId);
            int index = -1;
            for (int i = 0; i < images.size(); i++) if (images.get(i).id() == imageId) index = i;
            int other = index + direction;
            if (index < 0 || other < 0 || other >= images.size()) { c.rollback(); return; }
            ProductImage a = images.get(index), b = images.get(other);
            try (PreparedStatement p = c.prepareStatement("UPDATE product_images SET sort_order=? WHERE id=?")) {
                p.setInt(1, b.sortOrder()); p.setInt(2, a.id()); p.executeUpdate();
                p.setInt(1, a.sortOrder()); p.setInt(2, b.id()); p.executeUpdate();
            }
            c.commit();
        } catch (SQLException e) {
            c.rollback();
            throw e;
        } finally { c.setAutoCommit(true); }
    }

    private List<ProductImage> listWithConnection(Connection c, int productId) throws SQLException {
        List<ProductImage> result = new ArrayList<>();
        try (PreparedStatement p = c.prepareStatement(
                "SELECT id,product_id,filename,is_primary,sort_order FROM product_images WHERE product_id=? ORDER BY sort_order,id")) {
            p.setInt(1, productId);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) result.add(new ProductImage(
                    r.getInt(1), r.getInt(2), r.getString(3), r.getInt(4) == 1, r.getInt(5)));
            }
        }
        return result;
    }

    public void deleteImage(int productId, int imageId) throws SQLException {
        Connection c = DatabaseManager.getInstance().getConnection();
        String filename = null;
        boolean wasPrimary = false;
        DatabaseManager.beginTransaction(c);
        try {
            try (PreparedStatement p = c.prepareStatement(
                    "SELECT filename,is_primary FROM product_images WHERE id=? AND product_id=?")) {
                p.setInt(1, imageId); p.setInt(2, productId);
                try (ResultSet r = p.executeQuery()) {
                    if (!r.next()) { c.rollback(); return; }
                    filename = r.getString(1);
                    wasPrimary = r.getInt(2) == 1;
                }
            }
            try (PreparedStatement p = c.prepareStatement("DELETE FROM product_images WHERE id=? AND product_id=?")) {
                p.setInt(1, imageId); p.setInt(2, productId); p.executeUpdate();
            }
            normalizeSortOrder(c, productId);
            if (wasPrimary) {
                try (PreparedStatement p = c.prepareStatement(
                        "UPDATE product_images SET is_primary=1 WHERE id=(SELECT id FROM product_images WHERE product_id=? ORDER BY sort_order,id LIMIT 1)")) {
                    p.setInt(1, productId); p.executeUpdate();
                }
            }
            c.commit();
        } catch (SQLException e) {
            c.rollback();
            throw e;
        } finally { c.setAutoCommit(true); }
        if (filename != null) deleteFiles(filename);
    }

    private void normalizeSortOrder(Connection c, int productId) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        try (PreparedStatement p = c.prepareStatement(
                "SELECT id FROM product_images WHERE product_id=? ORDER BY sort_order,id")) {
            p.setInt(1, productId);
            try (ResultSet r = p.executeQuery()) { while (r.next()) ids.add(r.getInt(1)); }
        }
        try (PreparedStatement p = c.prepareStatement("UPDATE product_images SET sort_order=? WHERE id=?")) {
            for (int i = 0; i < ids.size(); i++) {
                p.setInt(1, i); p.setInt(2, ids.get(i)); p.addBatch();
            }
            p.executeBatch();
        }
    }

    public void deleteNamedFiles(Collection<String> filenames) {
        if (filenames == null) return;
        for (String filename : filenames) deleteFiles(filename);
    }

    public void deleteFiles(String filename) {
        try {
            Files.deleteIfExists(imagePath(filename));
            Files.deleteIfExists(thumbnailPath(filename));
        } catch (IOException e) {
            System.err.println("Unable to delete product image " + filename + ": " + e.getMessage());
        }
    }

    public ImageIcon icon(String filename, int maxSide) {
        if (filename == null || filename.isBlank()) return null;
        try {
            Path preferred = maxSide <= THUMB_LONG_SIDE ? thumbnailPath(filename) : imagePath(filename);
            if (!Files.isRegularFile(preferred) && maxSide <= THUMB_LONG_SIDE) {
                ensureThumbnail(filename);
                preferred = thumbnailPath(filename);
            }
            if (!Files.isRegularFile(preferred)) return null;
            BufferedImage image = ImageIO.read(preferred.toFile());
            if (image == null) return null;
            BufferedImage scaled = scaleToLongSide(image, maxSide);
            return new ImageIcon(scaled);
        } catch (Exception e) {
            return null;
        }
    }

    public void ensureThumbnail(String filename) throws IOException {
        Path thumb = thumbnailPath(filename);
        if (Files.isRegularFile(thumb)) return;
        BufferedImage original = ImageIO.read(imagePath(filename).toFile());
        if (original == null) throw new IOException("Invalid stored image " + filename);
        createThumbnail(filename, original);
    }

    public void rebuildThumbnails() throws IOException {
        Path dir = directory();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "thumb_*")) {
            for (Path file : stream) Files.deleteIfExists(file);
        }
        Set<String> referenced;
        try {
            referenced = referencedFilenames();
        } catch (SQLException e) {
            throw new IOException("Unable to read product image references", e);
        }
        for (String name : referenced) if (Files.isRegularFile(imagePath(name))) ensureThumbnail(name);
    }

    public void removeOrphanFiles() {
        try {
            Set<String> referenced = referencedFilenames();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory())) {
                for (Path file : stream) {
                    if (!Files.isRegularFile(file)) continue;
                    String name = file.getFileName().toString();
                    String original = name.startsWith("thumb_") ? name.substring(6) : name;
                    if (!referenced.contains(original)) Files.deleteIfExists(file);
                }
            }
        } catch (Exception e) {
            System.err.println("Unable to clean orphan product images: " + e.getMessage());
        }
    }

    public Set<String> referencedFilenames() throws SQLException {
        Set<String> names = new LinkedHashSet<>();
        Connection c = DatabaseManager.getInstance().getConnection();
        try (Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT filename FROM product_images ORDER BY product_id,sort_order,id")) {
            while (r.next()) names.add(safeName(r.getString(1)));
        }
        return names;
    }

    public void copyAllFilesTo(Path destination) throws IOException {
        if (Files.exists(destination)) deleteDirectory(destination);
        Files.createDirectories(destination);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory())) {
            for (Path file : stream) if (Files.isRegularFile(file))
                Files.copy(file, destination.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public void replaceAllFilesFrom(Path source) throws IOException {
        Path dir = directory();
        deleteDirectory(dir);
        Files.createDirectories(dir);
        if (Files.isDirectory(source)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(source)) {
                for (Path file : stream) if (Files.isRegularFile(file)) {
                    String name = file.getFileName().toString();
                    if (name.startsWith("thumb_") || name.toLowerCase(Locale.ROOT).endsWith(".jpg"))
                        Files.copy(file, dir.resolve(safeName(name)), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    public void replaceOriginalsFrom(Path source) throws IOException {
        Path dir = directory();
        deleteDirectory(dir);
        Files.createDirectories(dir);
        if (Files.isDirectory(source)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(source)) {
                for (Path file : stream) if (Files.isRegularFile(file)) {
                    String name = safeName(file.getFileName().toString());
                    if (!name.toLowerCase(Locale.ROOT).endsWith(".jpg"))
                        throw new IOException("Unsupported product image in backup: " + name);
                    Files.copy(file, dir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        rebuildThumbnails();
    }

    private static void deleteDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (var walk = Files.walk(dir)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    private void createThumbnail(String filename, BufferedImage source) throws IOException {
        BufferedImage thumb = toRgb(scaleToLongSide(source, THUMB_LONG_SIDE));
        Files.write(thumbnailPath(filename), encodeJpeg(thumb, 0.64f),
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
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
