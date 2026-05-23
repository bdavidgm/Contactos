package com.bdavidgm.contactos.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bdavidgm.contactos.di.AppContainer

private const val ROUTE_LIST = "list"
private const val ROUTE_EDIT = "edit/{contactId}"

@Composable
fun ContactNavHost(
    appContainer: AppContainer,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = ROUTE_LIST,
        modifier = modifier,
    ) {
        composable(ROUTE_LIST) {
            ContactListScreen(
                repository = appContainer.contactRepository,
                onAddContact = { navController.navigate("edit/0") },
                onEditContact = { id -> navController.navigate("edit/$id") },
            )
        }
        composable(
            route = ROUTE_EDIT,
            arguments = listOf(
                navArgument("contactId") { type = NavType.LongType },
            ),
        ) { entry ->
            val id = entry.arguments!!.getLong("contactId")
            ContactEditScreen(
                repository = appContainer.contactRepository,
                contactId = id,
                onClose = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }
    }
}
