package de.saarmesh.waechter

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.AndroidEntryPoint
import de.saarmesh.waechter.ui.detail.DetailScreen
import de.saarmesh.waechter.ui.list.ListScreen
import de.saarmesh.waechter.ui.settings.SettingsScreen
import de.saarmesh.waechter.ui.theme.WaechterTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val pendingPk = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingPk.value = intent?.getStringExtra(EXTRA_PK)
        setContent {
            WaechterTheme {
                val nav = rememberNavController()
                LaunchedEffect(pendingPk.value) {
                    pendingPk.value?.let {
                        nav.navigate("detail/$it") { launchSingleTop = true }
                        pendingPk.value = null
                    }
                }
                NavHost(nav, startDestination = "list") {
                    composable("list") {
                        ListScreen(
                            onOpen = { nav.navigate("detail/$it") },
                            onSettings = { nav.navigate("settings") },
                        )
                    }
                    composable("detail/{pk}", arguments = listOf(navArgument("pk") { type = NavType.StringType })) {
                        DetailScreen(onBack = { nav.popBackStack() }, onOpen = { nav.navigate("detail/$it") })
                    }
                    composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }) }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_PK)?.let { pendingPk.value = it }
    }

    companion object {
        const val EXTRA_PK = "pk"
    }
}
