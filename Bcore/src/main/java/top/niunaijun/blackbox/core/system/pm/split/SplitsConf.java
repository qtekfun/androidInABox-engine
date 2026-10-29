package top.niunaijun.blackbox.core.system.pm.split;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistencia de los splits instalados en {@code data/user_app/<perfil>/<pkg>/splits.conf}: texto UTF-8,
 * una línea por split y en orden, {@code nombre<TAB>fichero}. El fichero es relativo al
 * directorio de la app (o absoluto). Vive aparte de {@code package.conf} para no cambiar su
 * formato parcelable: si falta, el paquete se trata como sin splits.
 */
public class SplitsConf {
    public final List<String> names = new ArrayList<>();
    /** Rutas absolutas ya resueltas contra el directorio de la app. */
    public final List<String> codePaths = new ArrayList<>();

    public static void write(File conf, List<String> names, List<String> files) throws IOException {
        if (names.size() != files.size()) {
            throw new IOException("names y files deben tener la misma longitud");
        }
        File tmp = new File(conf.getParentFile(), conf.getName() + ".tmp");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
            for (int i = 0; i < names.size(); i++) {
                String n = names.get(i);
                String f = files.get(i);
                if (n.isEmpty() || f.isEmpty() || hasSeparator(n) || hasSeparator(f)) {
                    throw new IOException("Entrada de split inválida: " + n);
                }
                w.write(n);
                w.write('\t');
                w.write(f);
                w.write('\n');
            }
        }
        Files.move(tmp.toPath(), conf.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private static boolean hasSeparator(String s) {
        return s.indexOf('\t') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0;
    }

    /**
     * Lee el fichero. Devuelve una lista vacía si no existe o es ilegible. Ignora las líneas mal
     * formadas y los splits cuyo fichero ya no existe.
     */
    public static SplitsConf read(File conf, File appDir) {
        SplitsConf out = new SplitsConf();
        if (conf == null || !conf.isFile()) {
            return out;
        }
        try {
            for (String line : Files.readAllLines(conf.toPath(), StandardCharsets.UTF_8)) {
                int tab = line.indexOf('\t');
                if (tab <= 0 || tab == line.length() - 1) continue;
                String name = line.substring(0, tab);
                File f = new File(line.substring(tab + 1));
                if (!f.isAbsolute()) f = new File(appDir, f.getPath());
                if (!f.isFile() || out.names.contains(name)) continue;
                out.names.add(name);
                out.codePaths.add(f.getAbsolutePath());
            }
        } catch (IOException | RuntimeException e) {
            return new SplitsConf();
        }
        return out;
    }
}
