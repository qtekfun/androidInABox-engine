package top.niunaijun.blackbox.core.system.usb;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;

import java.util.List;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.core.system.user.BUserInfo;

/**
 * Receptor de USB_ACCESSORY_ATTACHED en el host (spec 07, T07.4). El sistema real solo entrega este
 * evento a una actividad del paquete real, asi que el host lo recoge (sin interfaz) y lo reenvia a la
 * actividad del box cuyo filtro encaje (normalmente Android Auto). El permiso del accesorio queda
 * concedido al UID del host, que comparten todas las apps invitadas.
 */
public class UsbAccessoryActivity extends Activity {
    public static final String TAG = "UsbAccessoryActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            forward(getIntent());
        } catch (Throwable e) {
            Log.e(TAG, "no se pudo reenviar el accesorio USB al box", e);
        }
        finish();
    }

    private void forward(Intent original) {
        if (original == null || !UsbManager.ACTION_USB_ACCESSORY_ATTACHED.equals(original.getAction())) {
            return;
        }
        Intent query = new Intent(UsbManager.ACTION_USB_ACCESSORY_ATTACHED);
        List<BUserInfo> users = BlackBoxCore.get().getUsers();
        for (BUserInfo user : users) {
            List<ResolveInfo> resolves = BlackBoxCore.getBPackageManager()
                    .queryIntentActivities(query, 0, null, user.id);
            if (resolves == null || resolves.isEmpty()) {
                continue;
            }
            ActivityInfo ai = resolves.get(0).activityInfo;
            // Se conserva el extra UsbManager.EXTRA_ACCESSORY del intent original
            Intent target = new Intent(original);
            target.setClassName(ai.packageName, ai.name);
            target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Log.i(TAG, "accesorio USB reenviado a " + ai.packageName + "/" + ai.name + " (perfil " + user.id + ")");
            BlackBoxCore.get().startActivity(target, user.id);
            return;
        }
        Log.w(TAG, "ninguna app del box declara un filtro USB_ACCESSORY_ATTACHED");
    }
}
