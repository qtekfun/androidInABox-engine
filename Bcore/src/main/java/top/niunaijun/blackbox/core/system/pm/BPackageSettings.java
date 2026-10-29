package top.niunaijun.blackbox.core.system.pm;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.AtomicFile;

import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import top.niunaijun.blackbox.core.env.BEnvironment;
import top.niunaijun.blackbox.core.system.user.BUserHandle;
import top.niunaijun.blackbox.entity.pm.InstallOption;
import top.niunaijun.blackbox.utils.CloseUtils;
import top.niunaijun.blackbox.utils.FileUtils;


public class BPackageSettings implements Parcelable {
    public BPackage pkg;
    public int appId;
    public InstallOption installOption;
    public Map<Integer, BPackageUserState> userState = new HashMap<>();
    static final BPackageUserState DEFAULT_USER_STATE = new BPackageUserState();

    public BPackageSettings() {
    }

    public List<BPackageUserState> getUserState() {
        return new ArrayList<>(userState.values());
    }

    public List<Integer> getUserIds() {
        return new ArrayList<>(userState.keySet());
    }

    public void setInstalled(boolean inst, int userId) {
        modifyUserState(userId).installed = inst;
    }

    public boolean getInstalled(int userId) {
        return readUserState(userId).installed;
    }

    public boolean getStopped(int userId) {
        return readUserState(userId).stopped;
    }

    public void setStopped(boolean stop, int userId) {
        modifyUserState(userId).stopped = stop;
    }

    public boolean getHidden(int userId) {
        return readUserState(userId).hidden;
    }

    public void setHidden(boolean hidden, int userId) {
        modifyUserState(userId).hidden = hidden;
    }

    public void removeUser(int userId) {
        userState.remove(userId);
    }

    public BPackageUserState readUserState(int userId) {
        BPackageUserState state = userState.get(userId);
        if (state == null) {
            state = new BPackageUserState();
        }
        state = new BPackageUserState(state);
        

        if (userId == BUserHandle.USER_ALL) {
            state.installed = true;
        }
        return state;
    }

    private BPackageUserState modifyUserState(int userId) {
        BPackageUserState state = userState.get(userId);
        if (state == null) {
            state = new BPackageUserState();
            userState.put(userId, state);
        }
        return state;
    }

    public boolean save() {
        synchronized (this) {
            Parcel parcel = Parcel.obtain();
            AtomicFile atomicFile = new AtomicFile(BEnvironment.getPackageConf(pkg.packageName));
            FileOutputStream fileOutputStream = null;
            try {
                writeToParcel(parcel, 0);
                parcel.setDataPosition(0);
                fileOutputStream = atomicFile.startWrite();
                FileUtils.writeParcelToOutput(parcel, fileOutputStream);
                atomicFile.finishWrite(fileOutputStream);
                return true;
            } catch (Throwable e) {
                e.printStackTrace();
                atomicFile.failWrite(fileOutputStream);
                return false;
            } finally {
                parcel.recycle();
                CloseUtils.close(fileOutputStream);
            }
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Formato v1: cabecera, appId, installOption, estados de usuario y, AL FINAL, el BPackage.
     * Así, si el BPackage ya no se puede leer, el resto de datos sigue recuperable.
     * Nuevos campos solo al final y protegidos por version (ver PersistFormat).
     */
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        PersistFormat.writeVersion(dest);
        dest.writeInt(this.appId);
        dest.writeParcelable(this.installOption, flags);
        dest.writeInt(this.userState.size());
        for (Map.Entry<Integer, BPackageUserState> entry : this.userState.entrySet()) {
            dest.writeValue(entry.getKey());
            dest.writeParcelable(entry.getValue(), flags);
        }
        dest.writeParcelable(this.pkg, flags);
    }

    /** Error al leer el BPackage (si es null, se leyó entero). Solo con {@link #readLenient}. */
    public transient Throwable pkgError;
    /** Versión del formato leído. */
    public transient int formatVersion = PersistFormat.VERSION_ACTUAL;

    protected BPackageSettings(Parcel in) {
        readFrom(in, false);
    }

    /**
     * Lee lo que se pueda: si el BPackage falla, devuelve el resto de datos con {@code pkg == null}
     * y {@link #pkgError} relleno. En formato legacy (BPackage primero) no se recupera nada más.
     */
    public static BPackageSettings readLenient(Parcel in) {
        BPackageSettings ps = new BPackageSettings();
        ps.readFrom(in, true);
        return ps;
    }

    private void readFrom(Parcel in, boolean lenient) {
        int version = PersistFormat.readVersion(in);
        this.formatVersion = version;
        if (version == PersistFormat.VERSION_LEGACY) {
            try {
                this.pkg = in.readParcelable(BPackage.class.getClassLoader());
                readRest(in);
            } catch (Throwable t) {
                if (!lenient) throw t;
                this.pkg = null;
                this.pkgError = t;
            }
            return;
        }
        readRest(in);
        try {
            this.pkg = in.readParcelable(BPackage.class.getClassLoader());
        } catch (Throwable t) {
            if (!lenient) throw t;
            this.pkg = null;
            this.pkgError = t;
        }
    }

    private void readRest(Parcel in) {
        this.appId = in.readInt();
        this.installOption = in.readParcelable(InstallOption.class.getClassLoader());
        int userStateSize = in.readInt();
        this.userState = new HashMap<Integer, BPackageUserState>(userStateSize);
        for (int i = 0; i < userStateSize; i++) {
            Integer key = (Integer) in.readValue(Integer.class.getClassLoader());
            BPackageUserState value = in.readParcelable(BPackageUserState.class.getClassLoader());
            this.userState.put(key, value);
        }
    }

    public static final Creator<BPackageSettings> CREATOR = new Creator<BPackageSettings>() {
        @Override
        public BPackageSettings createFromParcel(Parcel source) {
            return new BPackageSettings(source);
        }

        @Override
        public BPackageSettings[] newArray(int size) {
            return new BPackageSettings[size];
        }
    };
}
