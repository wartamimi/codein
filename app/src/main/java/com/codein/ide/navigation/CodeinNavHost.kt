package com.codein.ide.navigation

import android.net.Uri
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.codein.ide.home.HomeScreen
import com.codein.ide.settings.SettingsScreen
import com.codein.ide.wizard.NewProjectWizard
import com.codein.ide.workspace.WorkspaceScreen

/** Route table for the app. Project paths are URL-encoded into the workspace route. */
object Routes {
    const val HOME = "home"
    const val WIZARD = "wizard"
    const val SETTINGS = "settings"
    const val WORKSPACE_ARG = "path"
    const val WORKSPACE = "workspace/{$WORKSPACE_ARG}"

    fun workspace(path: String): String = "workspace/${Uri.encode(path)}"
}

@Composable
fun CodeinNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        enterTransition = { fadeIn() + slideInHorizontally { it / 12 } },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { fadeOut() + slideOutHorizontally { it / 12 } },
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenProject = { path -> navController.navigate(Routes.workspace(path)) { launchSingleTop = true } },
                onNewProject = { navController.navigate(Routes.WIZARD) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
            )
        }
        composable(Routes.WIZARD) {
            NewProjectWizard(
                onBack = { navController.popBackStack() },
                onCreated = { path ->
                    navController.navigate(Routes.workspace(path)) {
                        popUpTo(Routes.HOME)
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(
            Routes.WORKSPACE,
            arguments = listOf(navArgument(Routes.WORKSPACE_ARG) { type = NavType.StringType }),
        ) { entry ->
            val path = Uri.decode(entry.arguments?.getString(Routes.WORKSPACE_ARG).orEmpty())
            WorkspaceScreen(
                projectPath = path,
                onOpenSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                onCloseProject = {
                    if (!navController.popBackStack(Routes.HOME, inclusive = false)) {
                        navController.navigate(Routes.HOME) { popUpTo(0) }
                    }
                },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
