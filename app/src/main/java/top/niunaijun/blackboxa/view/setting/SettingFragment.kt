package top.niunaijun.blackboxa.view.setting

import android.net.VpnService
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackboxa.R
import top.niunaijun.blackboxa.app.AppManager
import top.niunaijun.blackbox.core.system.BackgroundSettings
import top.niunaijun.blackboxa.util.PowerGuide
import top.niunaijun.blackboxa.util.toast
import top.niunaijun.blackboxa.view.gms.GmsManagerActivity

class SettingFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.setting, rootKey)

        initGms()
        initBackground()

        invalidHideState {
            val rootHidePreference: Preference = (findPreference("root_hide")!!)
            val hideRoot = AppManager.mBlackBoxLoader.hideRoot()
            rootHidePreference.setDefaultValue(hideRoot)
            rootHidePreference
        }

        invalidHideState {
            val daemonPreference: Preference = (findPreference("daemon_enable")!!)
            val mDaemonEnable = AppManager.mBlackBoxLoader.daemonEnable()
            daemonPreference.setDefaultValue(mDaemonEnable)
            daemonPreference
        }

        invalidHideState {
            val vpnPreference: Preference = (findPreference("use_vpn_network")!!)
            val mUseVpnNetwork = AppManager.mBlackBoxLoader.useVpnNetwork()
            vpnPreference.setDefaultValue(mUseVpnNetwork)
            vpnPreference
        }

        invalidHideState {
            val disableFlagSecurePreference: Preference = (findPreference("disable_flag_secure")!!)
            val mDisableFlagSecure = AppManager.mBlackBoxLoader.disableFlagSecure()
            disableFlagSecurePreference.setDefaultValue(mDisableFlagSecure)
            disableFlagSecurePreference
        }

        invalidHideState {
            val titlePrefixPreference: Preference = (findPreference("notification_title_prefix")!!)
            titlePrefixPreference.setDefaultValue(AppManager.mBlackBoxLoader.notificationTitlePrefix())
            titlePrefixPreference
        }

    }

    override fun onResume() {
        super.onResume()
        refreshBatteryState()
    }

    private fun refreshBatteryState() {
        val pref: Preference = findPreference("battery_exemption") ?: return
        val ok = PowerGuide.isIgnoringBatteryOptimizations(requireContext())
        pref.summary = getString(if (ok) R.string.battery_exempt_yes else R.string.battery_exempt_no)
    }

    private fun initBackground() {
        val ctx = requireContext()
        findPreference<Preference>("battery_exemption")?.setOnPreferenceClickListener {
            PowerGuide.requestIgnoreBatteryOptimizations(ctx)
            true
        }
        findPreference<Preference>("rom_autostart")?.setOnPreferenceClickListener {
            if (PowerGuide.openAutostartSettings(ctx) == PowerGuide.Opened.NONE) {
                toast(R.string.rom_autostart_manual)
            }
            true
        }
        findPreference<androidx.preference.SwitchPreferenceCompat>("start_on_boot")?.apply {
            // El ajuste es un fichero marcador compartido con :black (el receptor de arranque lo lee alli)
            isPersistent = false
            isChecked = BackgroundSettings.isStartOnBoot(ctx.filesDir)
            setOnPreferenceChangeListener { _, value ->
                BackgroundSettings.setStartOnBoot(ctx.filesDir, value as Boolean)
                true
            }
        }
    }

    private fun initGms() {
        val gmsManagerPreference: Preference = (findPreference("gms_manager")!!)

        if (BlackBoxCore.get().isSupportGms) {

            gmsManagerPreference.setOnPreferenceClickListener {
                GmsManagerActivity.start(requireContext())
                true
            }
        } else {
            gmsManagerPreference.summary = getString(R.string.no_gms)
            gmsManagerPreference.isEnabled = false
        }
    }

    private val vpnPermissionResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    private fun requestVpnPermission() {
        try {
            VpnService.prepare(requireContext())?.let { vpnPermissionResult.launch(it) }
        } catch (e: Exception) {
            Log.e("SettingFragment", "Error requesting VPN permission: ${e.message}")
        }
    }

    private fun invalidHideState(block: () -> Preference) {
        val pref = block()
        pref.setOnPreferenceChangeListener { preference, newValue ->
            val tmpHide = (newValue == true)
            when (preference.key) {
                "root_hide" -> {

                    AppManager.mBlackBoxLoader.invalidHideRoot(tmpHide)
                }
                "daemon_enable" -> {
                    AppManager.mBlackBoxLoader.invalidDaemonEnable(tmpHide)
                }
                "use_vpn_network" -> {
                    AppManager.mBlackBoxLoader.invalidUseVpnNetwork(tmpHide)
                    if (tmpHide) {
                        requestVpnPermission()
                    }
                }
                "disable_flag_secure" -> {
                    AppManager.mBlackBoxLoader.invalidDisableFlagSecure(tmpHide)
                }
                "notification_title_prefix" -> {
                    AppManager.mBlackBoxLoader.invalidNotificationTitlePrefix(tmpHide)
                }
            }

            toast(R.string.restart_module)
            return@setOnPreferenceChangeListener true
        }
    }
}
