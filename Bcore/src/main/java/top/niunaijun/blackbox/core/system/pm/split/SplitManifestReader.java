package top.niunaijun.blackbox.core.system.pm.split;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Lee del {@code AndroidManifest.xml} binario de un APK los atributos de split, sin depender
 * de {@code PackageParser} ni de {@code AssetManager} (Java puro, comprobable en JVM).
 */
public class SplitManifestReader {
    private static final int CHUNK_STRING_POOL = 0x0001;
    private static final int CHUNK_START_ELEMENT = 0x0102;
    private static final int FLAG_UTF8 = 1 << 8;

    private static final int TYPE_STRING = 0x03;
    private static final int TYPE_INT_DEC = 0x10;
    private static final int TYPE_INT_HEX = 0x11;
    private static final int TYPE_INT_BOOLEAN = 0x12;

    private static final int MAX_MANIFEST_SIZE = 16 * 1024 * 1024;

    public static SplitInfo read(File apk) throws IOException {
        try (ZipFile zip = new ZipFile(apk)) {
            ZipEntry entry = zip.getEntry("AndroidManifest.xml");
            if (entry == null) {
                throw new IOException("Sin AndroidManifest.xml: " + apk.getName());
            }
            if (entry.getSize() > MAX_MANIFEST_SIZE) {
                throw new IOException("AndroidManifest.xml demasiado grande: " + apk.getName());
            }
            byte[] data;
            try (InputStream in = zip.getInputStream(entry)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    if (out.size() > MAX_MANIFEST_SIZE) {
                        throw new IOException("AndroidManifest.xml demasiado grande: " + apk.getName());
                    }
                }
                data = out.toByteArray();
            }
            SplitInfo info = parse(data);
            info.file = apk;
            return info;
        }
    }

    public static SplitInfo parse(byte[] axml) throws IOException {
        try {
            return doParse(axml);
        } catch (IndexOutOfBoundsException | IllegalArgumentException | java.nio.BufferUnderflowException e) {
            throw new IOException("Manifiesto binario inválido", e);
        }
    }

    private static SplitInfo doParse(byte[] axml) throws IOException {
        ByteBuffer bb = ByteBuffer.wrap(axml).order(ByteOrder.LITTLE_ENDIAN);
        if (axml.length < 8 || bb.getShort(0) != 0x0003) {
            throw new IOException("No es un XML binario");
        }
        int pos = bb.getShort(2) & 0xFFFF;
        String[] strings = null;
        while (pos + 8 <= axml.length) {
            int type = bb.getShort(pos) & 0xFFFF;
            int headerSize = bb.getShort(pos + 2) & 0xFFFF;
            int size = bb.getInt(pos + 4);
            if (size < 8 || pos + size > axml.length) {
                throw new IOException("Chunk inválido");
            }
            if (type == CHUNK_STRING_POOL) {
                strings = readStringPool(bb, pos);
            } else if (type == CHUNK_START_ELEMENT) {
                if (strings == null) {
                    throw new IOException("Elemento antes del pool de strings");
                }
                // header(16) ; attributeStart(u16) attributeSize(u16) attributeCount(u16) ...
                int ext = pos + headerSize;
                int nameIdx = bb.getInt(ext + 4);
                if (!"manifest".equals(str(strings, nameIdx))) {
                    throw new IOException("El elemento raíz no es manifest");
                }
                int attrStart = bb.getShort(ext + 8) & 0xFFFF;
                int attrSize = bb.getShort(ext + 10) & 0xFFFF;
                int attrCount = bb.getShort(ext + 12) & 0xFFFF;
                return readManifestAttrs(bb, strings, ext + attrStart, attrSize, attrCount);
            }
            pos += size;
        }
        throw new IOException("Manifiesto sin elemento raíz");
    }

    private static SplitInfo readManifestAttrs(ByteBuffer bb, String[] strings, int start, int attrSize, int count) {
        SplitInfo info = new SplitInfo();
        for (int i = 0; i < count; i++) {
            int a = start + i * attrSize;
            int nameIdx = bb.getInt(a + 4);
            int rawIdx = bb.getInt(a + 8);
            int dataType = bb.get(a + 15) & 0xFF;
            int data = bb.getInt(a + 16);
            String name = str(strings, nameIdx);
            if (name == null) continue;
            switch (name) {
                case "package":
                    info.packageName = stringValue(strings, rawIdx, dataType, data);
                    break;
                case "split":
                    info.splitName = stringValue(strings, rawIdx, dataType, data);
                    break;
                case "configForSplit":
                    info.configForSplit = stringValue(strings, rawIdx, dataType, data);
                    break;
                case "isFeatureSplit":
                    info.isFeatureSplit = boolValue(strings, rawIdx, dataType, data);
                    break;
                case "isSplitRequired":
                    info.isSplitRequired = boolValue(strings, rawIdx, dataType, data);
                    break;
                case "isolatedSplits":
                    info.isolatedSplits = boolValue(strings, rawIdx, dataType, data);
                    break;
                case "versionCode":
                    if (dataType == TYPE_INT_DEC || dataType == TYPE_INT_HEX) {
                        info.versionCode = data & 0xFFFFFFFFL;
                    } else {
                        try {
                            info.versionCode = Long.parseLong(stringValue(strings, rawIdx, dataType, data));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    break;
                default:
                    break;
            }
        }
        if (info.splitName != null && info.splitName.isEmpty()) info.splitName = null;
        if (info.configForSplit != null && info.configForSplit.isEmpty()) info.configForSplit = null;
        return info;
    }

    private static String stringValue(String[] strings, int rawIdx, int dataType, int data) {
        if (rawIdx >= 0) return str(strings, rawIdx);
        if (dataType == TYPE_STRING) return str(strings, data);
        return null;
    }

    private static boolean boolValue(String[] strings, int rawIdx, int dataType, int data) {
        if (dataType == TYPE_INT_BOOLEAN) return data != 0;
        if (rawIdx >= 0) return "true".equals(str(strings, rawIdx));
        return false;
    }

    private static String str(String[] strings, int idx) {
        if (idx < 0 || idx >= strings.length) return null;
        return strings[idx];
    }

    private static String[] readStringPool(ByteBuffer bb, int pos) throws IOException {
        int headerSize = bb.getShort(pos + 2) & 0xFFFF;
        int stringCount = bb.getInt(pos + 8);
        int flags = bb.getInt(pos + 16);
        int stringsStart = bb.getInt(pos + 20);
        if (stringCount < 0 || stringCount > 1_000_000) {
            throw new IOException("Pool de strings inválido");
        }
        boolean utf8 = (flags & FLAG_UTF8) != 0;
        String[] out = new String[stringCount];
        int offsetsAt = pos + headerSize;
        int base = pos + stringsStart;
        for (int i = 0; i < stringCount; i++) {
            int off = base + bb.getInt(offsetsAt + i * 4);
            out[i] = utf8 ? readUtf8(bb, off) : readUtf16(bb, off);
        }
        return out;
    }

    private static String readUtf16(ByteBuffer bb, int off) {
        int len = bb.getShort(off) & 0xFFFF;
        int p = off + 2;
        if ((len & 0x8000) != 0) {
            len = ((len & 0x7FFF) << 16) | (bb.getShort(p) & 0xFFFF);
            p += 2;
        }
        char[] chars = new char[len];
        for (int i = 0; i < len; i++) {
            chars[i] = (char) (bb.getShort(p + i * 2) & 0xFFFF);
        }
        return new String(chars);
    }

    private static String readUtf8(ByteBuffer bb, int off) {
        int p = off;
        int u16len = bb.get(p++) & 0xFF;
        if ((u16len & 0x80) != 0) p++;
        int len = bb.get(p++) & 0xFF;
        if ((len & 0x80) != 0) {
            len = ((len & 0x7F) << 8) | (bb.get(p++) & 0xFF);
        }
        byte[] b = new byte[len];
        for (int i = 0; i < len; i++) b[i] = bb.get(p + i);
        return new String(b, StandardCharsets.UTF_8);
    }
}
