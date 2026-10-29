package top.niunaijun.blackbox.core.system.pm.split;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Convierte un directorio de staging (APK sueltos y/o bundles) en un {@link SplitSet} validado:
 * detecta por contenido, comprueba el espacio libre antes de extraer, extrae con límites y lee
 * los manifiestos. No toca nada fuera del directorio de staging.
 */
public class SplitPreparer {
    /** Margen sobre el tamaño descomprimido (R01.7). */
    private static final double SPACE_MARGIN = 1.10;

    public static SplitSet prepare(File stagingDir, long freeBytes) throws InvalidSplitSetException, IOException {
        File[] inputs = stagingDir.listFiles(File::isFile);
        if (inputs == null || inputs.length == 0) {
            throw new InvalidSplitSetException("No hay ficheros para instalar");
        }
        Arrays.sort(inputs, Comparator.comparing(File::getName));

        List<File> apks = new ArrayList<>();
        List<File> bundles = new ArrayList<>();
        long needed = 0;
        for (File f : inputs) {
            switch (BundleDetector.detect(f)) {
                case APK:
                    apks.add(f);
                    break;
                case BUNDLE:
                    bundles.add(f);
                    needed += SafeUnzip.declaredApkSize(f);
                    break;
                default:
                    throw new InvalidSplitSetException("El fichero " + f.getName() + " no es un APK ni un bundle");
            }
        }
        if ((long) (needed * SPACE_MARGIN) > freeBytes) {
            throw new InvalidSplitSetException("Espacio insuficiente: se necesitan " + (needed >> 20)
                    + " MiB más un 10 % de margen");
        }

        SafeUnzip unzip = new SafeUnzip();
        int n = 0;
        for (File bundle : bundles) {
            File out = new File(stagingDir, "extracted-" + (n++));
            List<File> extracted = unzip.extractApks(bundle, out);
            if (extracted.isEmpty()) {
                throw new InvalidSplitSetException("El bundle " + bundle.getName() + " no contiene APK");
            }
            apks.addAll(extracted);
        }

        List<SplitInfo> infos = new ArrayList<>();
        for (File apk : apks) {
            try {
                infos.add(SplitManifestReader.read(apk));
            } catch (IOException e) {
                throw new InvalidSplitSetException("APK ilegible " + apk.getName() + ": " + e.getMessage());
            }
        }
        return SplitSet.of(infos);
    }
}
