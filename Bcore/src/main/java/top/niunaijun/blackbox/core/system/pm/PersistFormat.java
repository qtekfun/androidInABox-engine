package top.niunaijun.blackbox.core.system.pm;

import android.os.Parcel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import top.niunaijun.blackbox.entity.pm.InstallOption;

/**
 * Versionado del formato persistido de los parcelables (package.conf y anidados).
 *
 * <p>REGLA: los campos nuevos se añaden SOLO al final de cada parcelable y protegidos por
 * versión ({@code if (version >= N)}), con un valor por defecto cuando se lee una versión
 * anterior. Nunca reordenar, quitar ni cambiar el tipo de un campo existente. Subir
 * {@link #VERSION_ACTUAL} al añadir un campo. Los ficheros anteriores al versionado
 * ({@link #VERSION_LEGACY}) empiezan directamente por los datos, sin cabecera.
 *
 * <p>Si aun así un fichero no se puede leer, NUNCA se borra el paquete: se reconstruye
 * desde el APK instalado (ver {@code Settings}).
 */
public final class PersistFormat {
    /** Marca al inicio de un parcelable versionado: no puede ser un tamaño ni un flag reales. */
    public static final int MAGIC = 0x42425046;
    public static final int VERSION_LEGACY = 0;
    /** Primera versión con cabecera. */
    public static final int VERSION_1 = 1;
    /** v2 (ADR-0008): la copia guarda el perfil dueño. */
    public static final int VERSION_ACTUAL = 2;

    private PersistFormat() {
    }

    /** Valida la versión leída tras la marca. Versiones futuras se aceptan (solo se añaden campos al final). */
    public static int checkVersion(int raw) {
        if (raw < VERSION_1) {
            throw new IllegalStateException("version de formato invalida: " + raw);
        }
        return raw;
    }

    /** Versión efectiva a partir del primer entero leído; el segundo solo se consume si hay marca. */
    public static int resolveVersion(int first, int second) {
        return first == MAGIC ? checkVersion(second) : VERSION_LEGACY;
    }

    public static boolean hasHeader(int first) {
        return first == MAGIC;
    }

    /** Lee la cabecera si existe; si no, deja el cursor donde estaba y devuelve VERSION_LEGACY. */
    public static int readVersion(Parcel in) {
        int pos = in.dataPosition();
        if (in.dataAvail() >= 8 && hasHeader(in.readInt())) {
            return checkVersion(in.readInt());
        }
        in.setDataPosition(pos);
        return VERSION_LEGACY;
    }

    public static void writeVersion(Parcel out) {
        out.writeInt(MAGIC);
        out.writeInt(VERSION_ACTUAL);
    }

    /** Banderas de instalación cuando no se pueden leer: APK propio en el box => copia; si no, host. */
    public static int recoverInstallFlags(boolean apkInAppDir, boolean hostHasPackage) {
        if (apkInAppDir) {
            return InstallOption.FLAG_STORAGE;
        }
        return hostHasPackage ? InstallOption.FLAG_SYSTEM : 0;
    }

    /**
     * Usuarios para los que se asume instalado el paquete: los candidatos con directorio de datos.
     * Si ninguno lo tiene, el usuario 0 (instalación sin datos aún).
     */
    public static List<Integer> assumeInstalledUsers(Collection<Integer> candidates, java.util.function.Predicate<Integer> hasData) {
        List<Integer> out = new ArrayList<>();
        for (Integer id : candidates) {
            if (hasData.test(id)) out.add(id);
        }
        if (out.isEmpty()) out.add(0);
        return out;
    }
}
