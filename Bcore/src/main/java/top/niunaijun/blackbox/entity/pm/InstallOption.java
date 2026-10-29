package top.niunaijun.blackbox.entity.pm;

import android.os.Parcel;
import android.os.Parcelable;


public class InstallOption implements Parcelable {
    public static final int FLAG_SYSTEM = 1;
    public static final int FLAG_STORAGE = 1 << 1;

    public static final int FLAG_URI_FILE = 1 << 3;
    /** {@code file} es un directorio de staging con uno o varios APK / bundles. (1 << 2 queda libre.) */
    public static final int FLAG_SPLIT_SET = 1 << 4;
    /** El paquete ya está en el box: solo se habilita para este perfil, sin copiar ni tocar su APK. */
    public static final int FLAG_EXISTING_USER = 1 << 5;

    public static InstallOption installExistingForUser() {
        InstallOption installOption = new InstallOption();
        installOption.flags = installOption.flags | FLAG_EXISTING_USER;
        return installOption;
    }

    /**
     * El paquete se expone dentro del box como app de sistema privilegiada (GMS, GSF, Play Store).
     * No implica copiar del host: eso es {@link #FLAG_SYSTEM}.
     */
    public static final int FLAG_PRIVILEGED_VIRTUAL = 1 << 5;

    public int flags = 0;

    public static InstallOption installBySystem() {
        InstallOption installOption = new InstallOption();
        installOption.flags = installOption.flags | FLAG_SYSTEM;
        return installOption;
    }

    public static InstallOption installByStorage() {
        InstallOption installOption = new InstallOption();
        installOption.flags = installOption.flags | FLAG_STORAGE;
        return installOption;
    }



    public InstallOption makeUriFile() {
        this.flags |= FLAG_URI_FILE;
        return this;
    }

    public InstallOption makeSplitSet() {
        this.flags |= FLAG_SPLIT_SET;
        return this;
    }

    public InstallOption makePrivilegedVirtual() {
        this.flags |= FLAG_PRIVILEGED_VIRTUAL;
        return this;
    }

    public boolean isFlag(int flag) {
        return (flags & flag) != 0;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.flags);
    }

    public InstallOption() {
    }

    protected InstallOption(Parcel in) {
        this.flags = in.readInt();
    }

    public static final Parcelable.Creator<InstallOption> CREATOR = new Parcelable.Creator<InstallOption>() {
        @Override
        public InstallOption createFromParcel(Parcel source) {
            return new InstallOption(source);
        }

        @Override
        public InstallOption[] newArray(int size) {
            return new InstallOption[size];
        }
    };
}
