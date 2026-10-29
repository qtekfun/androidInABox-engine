package black.android.companion;

import android.os.IBinder;
import android.os.IInterface;

import top.niunaijun.blackreflection.annotation.BClassName;
import top.niunaijun.blackreflection.annotation.BStaticMethod;

@BClassName("android.companion.ICompanionDeviceManager")
public interface ICompanionDeviceManager {
    @BClassName("android.companion.ICompanionDeviceManager$Stub")
    interface Stub {
        @BStaticMethod
        IInterface asInterface(IBinder IBinder0);
    }
}
