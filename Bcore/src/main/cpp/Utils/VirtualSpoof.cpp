#include <sys/system_properties.h>
#include <cstring>
#include "./xdl.h"
#include <android/log.h>
#include <atomic>
#include <mutex>
#include <dlfcn.h>
#include "Dobby/dobby.h"


#define LOG_TAG "VirtualSpoof"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

struct SpoofedProp {
    const char* key;
    const char* value;
};

SpoofedProp spoofed_props[] = {
        {"ro.product.model", "Pixel 6"},
        {"ro.product.brand", "google"},
        {"ro.product.manufacturer", "Google"},
        {"ro.product.device", "oriole"},
        {"ro.build.fingerprint", "google/oriole/oriole:12/SP1A.210812.015/7679548:user/release-keys"},
        {"ro.build.version.release", "12"},
        {"ro.build.version.security_patch", "2022-01-05"},
        {"ro.serialno", "1A2B3C4D5E6F"},
        {"ro.hardware", "qcom"},
        {"ro.boot.hardware", "qcom"},
        {"ro.product.board", "lahaina"},
        {"ro.product.cpu.abi", "arm64-v8a"},
        {"ro.build.type", "user"},
        {"ro.build.tags", "release-keys"},
        {"ro.kernel.qemu", "0"},
        {"ro.kernel.android.qemud", ""},
        {"ro.hardware.egl", "adreno"},
        {"ro.boot.qemu", "0"},
    {nullptr, nullptr} 
};


// Interruptor por proceso. Por defecto apagado: el perfil solo se aplica si la
// app invitada lo tiene activado (BActivityThread.handleBindApplication).
static std::atomic<bool> g_spoof_active{false};
static std::once_flag g_install_once;
static bool g_installed = false;

static int (*orig_system_property_get)(const char *name, char *value) = nullptr;


int my_system_property_get(const char *name, char *value) {
    if (!g_spoof_active.load(std::memory_order_relaxed)) {
        return orig_system_property_get ? orig_system_property_get(name, value) : 0;
    }
    for (int i = 0; spoofed_props[i].key != nullptr; ++i) {
        if (strcmp(name, spoofed_props[i].key) == 0) {
            strcpy(value, spoofed_props[i].value);
             LOGD("[spoof] %s = %s", name, value);
            return strlen(value);
        }
    }
    if (orig_system_property_get) {
        return orig_system_property_get(name, value);
    }
    value[0] = '\0';
    return 0;
}

void install_property_get_hook() {
    void* handle = xdl_open("libc.so", XDL_DEFAULT);
    void* target = xdl_dsym(handle, "__system_property_get", nullptr);
    if (target) {
        if (DobbyHook(target, (void*)my_system_property_get, (void**)&orig_system_property_get) == 0) {
            g_installed = true;
            LOGD("Spoof installed successfully");
        } else {
            LOGD("Spoof hook failed");
        }
        xdl_close(handle);
    } else{
        xdl_close(handle);
    }

}


// Activa o desactiva el perfil falso en este proceso. El hook de Dobby se instala
// una sola vez, la primera vez que se activa; nunca desde un constructor.
bool virtual_spoof_enable(bool on)
{
    if (on) {
        std::call_once(g_install_once, install_property_get_hook);
        if (!g_installed) {
            return false;
        }
    }
    g_spoof_active.store(on, std::memory_order_relaxed);
    LOGD("VirtualSpoof: activo=%d", on ? 1 : 0);
    return true;
}
