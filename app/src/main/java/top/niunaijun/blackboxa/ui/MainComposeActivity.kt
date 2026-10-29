package top.niunaijun.blackboxa.ui

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackboxa.ui.screens.AboutScreen
import top.niunaijun.blackboxa.ui.screens.AddAppScreen
import top.niunaijun.blackboxa.ui.screens.AppSettingsScreen
import top.niunaijun.blackboxa.ui.screens.AppsScreen
import top.niunaijun.blackboxa.ui.screens.BatteryScreen
import top.niunaijun.blackboxa.ui.screens.GmsScreen
import top.niunaijun.blackboxa.ui.screens.OnboardingScreen
import top.niunaijun.blackboxa.ui.screens.ProfilesScreen
import top.niunaijun.blackboxa.ui.screens.SettingsScreen
import top.niunaijun.blackboxa.ui.theme.AiabTheme
import top.niunaijun.blackboxa.util.InjectionUtil

/** Interfaz principal del host (Compose + Material 3). */
class MainComposeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        try {
            BlackBoxCore.get().onBeforeMainActivityOnCreate(this)
        } catch (e: Throwable) {
            Log.e(TAG, "onBeforeMainActivityOnCreate: ${e.message}")
        }
        // Precarga la lista de apps instaladas para que "Añadir app" abra rápido.
        try {
            InjectionUtil.getListFactory()
        } catch (_: Throwable) { }

        setContent { AiabTheme { AiabApp(this) } }

        try {
            BlackBoxCore.get().onAfterMainActivityOnCreate(this)
        } catch (e: Throwable) {
            Log.e(TAG, "onAfterMainActivityOnCreate: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "MainComposeActivity"
        private const val PREFS = "aiab_ui"
        private const val KEY_ONBOARDED = "onboarded"

        fun isOnboarded(a: ComponentActivity) =
            a.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_ONBOARDED, false)

        fun setOnboarded(a: ComponentActivity) {
            a.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ONBOARDED, true).apply()
        }
    }
}

@Composable
private fun AiabApp(activity: MainComposeActivity) {
    val vm: HostViewModel = viewModel()
    val nav = rememberNavController()
    val start = if (MainComposeActivity.isOnboarded(activity)) "profiles" else "onboarding"
    var ioWarning by remember {
        mutableStateOf(
            try { !BlackBoxCore.get().isIoRedirectHealthy } catch (_: Throwable) { false }
        )
    }

    NavHost(navController = nav, startDestination = start) {
        composable("onboarding") {
            OnboardingScreen(onFinish = {
                MainComposeActivity.setOnboarded(activity)
                nav.navigate("profiles") { popUpTo("onboarding") { inclusive = true } }
            })
        }
        composable("profiles") {
            ProfilesScreen(
                vm = vm,
                ioRedirectWarning = ioWarning,
                onOpenProfile = { nav.navigate("apps/$it") },
                onSettings = { nav.navigate("settings") },
            )
        }
        val userArg = navArgument("userId") { type = NavType.IntType }
        composable("apps/{userId}", listOf(userArg)) { e ->
            val id = e.arguments?.getInt("userId") ?: 0
            AppsScreen(
                vm = vm,
                userId = id,
                onBack = { nav.popBackStack() },
                onAdd = { nav.navigate("add/$id") },
                onAppSettings = { pkg -> nav.navigate("appsettings/$id/$pkg") },
            )
        }
        composable("add/{userId}", listOf(userArg)) { e ->
            val id = e.arguments?.getInt("userId") ?: 0
            AddAppScreen(vm, id, onBack = {
                vm.loadApps(id)
                nav.popBackStack()
            })
        }
        composable(
            "appsettings/{userId}/{pkg}",
            listOf(userArg, navArgument("pkg") { type = NavType.StringType }),
        ) { e ->
            AppSettingsScreen(
                vm,
                e.arguments?.getInt("userId") ?: 0,
                e.arguments?.getString("pkg").orEmpty(),
                onBack = { nav.popBackStack() },
            )
        }
        composable("settings") {
            SettingsScreen(
                vm,
                onBack = { nav.popBackStack() },
                onGms = { nav.navigate("gms") },
                onBattery = { nav.navigate("battery") },
                onAbout = { nav.navigate("about") },
            )
        }
        composable("gms") { GmsScreen(vm, onBack = { nav.popBackStack() }) }
        composable("battery") { BatteryScreen(onBack = { nav.popBackStack() }) }
        composable("about") { AboutScreen(onBack = { nav.popBackStack() }) }
    }
}
