// UID del llamante para las apps invitadas.
//
// En Android 12+ Binder.getCallingUid() es @CriticalNative y el hook JNI del motor no lo intercepta.
// Se engancha IPCThreadState::getCallingUid() de libbinder con Dobby: si la llamada llega del servidor
// del box (<host>:black, que hace de system_server dentro del box), el invitado ve su propio UID virtual,
// que es lo que las apps tratan como "yo mismo" (p. ej. GMS: callingUid == myUid()).
// El reemplazo no hace llamadas JNI: puede ejecutarse desde un método @CriticalNative.

#include <jni.h>
#include <atomic>
#include <mutex>
#include <cstdint>
#include <unistd.h>
#include <android/log.h>
#include "../xdl.h"
#include "../Dobby/dobby.h"

#define CU_TAG "CallingUidHook"
#define CU_LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, CU_TAG, __VA_ARGS__)

static std::atomic<int32_t> g_host_uid{-1};
static std::atomic<int32_t> g_server_pid{-1};
static std::atomic<int32_t> g_self_uid{-1};
static int32_t g_self_pid = -1;
static std::once_flag g_install_once;
static bool g_installed = false;

static int32_t (*orig_getCallingUid)(const void *self) = nullptr;
static int32_t (*orig_getCallingPid)(const void *self) = nullptr;

static int32_t my_getCallingUid(const void *self) {
    int32_t uid = orig_getCallingUid(self);
    int32_t selfUid = g_self_uid.load(std::memory_order_relaxed);
    if (selfUid > 0 && uid == g_host_uid.load(std::memory_order_relaxed) && orig_getCallingPid != nullptr) {
        int32_t serverPid = g_server_pid.load(std::memory_order_relaxed);
        int32_t callingPid = orig_getCallingPid(self);
        // En llamadas oneway el PID llega como 0 (p. ej. IAccountAuthenticator): no se puede distinguir el
        // origen, y se trata igual que el servidor. Todas las apps del box comparten ya el UID del host.
        // Llamada dentro del propio proceso (o tras clearCallingIdentity): Process.myUid() del invitado
        // devuelve su UID virtual, y getCallingUid() debe coincidir, como en Android.
        if (callingPid == g_self_pid) {
            return selfUid;
        }
        if ((serverPid > 0 && callingPid == serverPid) || callingPid == 0) {
            return selfUid;
        }
    }
    return uid;
}

static void install_hook() {
    void *handle = xdl_open("libbinder.so", XDL_DEFAULT);
    if (handle == nullptr) {
        CU_LOGD("libbinder.so no disponible");
        return;
    }
    void *uidFn = xdl_sym(handle, "_ZNK7android14IPCThreadState13getCallingUidEv", nullptr);
    void *pidFn = xdl_sym(handle, "_ZNK7android14IPCThreadState13getCallingPidEv", nullptr);
    if (uidFn == nullptr || pidFn == nullptr) {
        CU_LOGD("símbolos de IPCThreadState no encontrados (uid=%p pid=%p)", uidFn, pidFn);
        xdl_close(handle);
        return;
    }
    orig_getCallingPid = (int32_t (*)(const void *)) pidFn;
    if (DobbyHook(uidFn, (void *) my_getCallingUid, (void **) &orig_getCallingUid) == 0) {
        g_installed = true;
        CU_LOGD("hook de IPCThreadState::getCallingUid instalado");
    } else {
        CU_LOGD("DobbyHook de getCallingUid falló");
    }
    xdl_close(handle);
}

jboolean setCallingUidMapping(JNIEnv *env, jclass clazz, jint hostUid, jint serverPid, jint selfUid) {
    g_host_uid.store(hostUid);
    g_server_pid.store(serverPid);
    g_self_uid.store(selfUid);
    g_self_pid = getpid();
    std::call_once(g_install_once, install_hook);
    CU_LOGD("mapeo host=%d servidor=%d propio=%d instalado=%d", hostUid, serverPid, selfUid, g_installed);
    return g_installed ? JNI_TRUE : JNI_FALSE;
}
