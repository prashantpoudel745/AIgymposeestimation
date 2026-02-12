package com.pragyan.frontendandroid.presentation.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.pragyan.frontendandroid.ui.theme.FrontendAndroidTheme


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopNavBarWithIcon(title: String, navController: NavController) {
    CenterAlignedTopAppBar(
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
        ),
        title = {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            IconButton(onClick = {
                navController.popBackStack()
            }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Navigate to previous screen",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        },
        scrollBehavior = null,
    )

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopNavBar(title: String) {
    CenterAlignedTopAppBar(
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
        ),
        title = {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        scrollBehavior = null,
    )

}

@Preview
@Composable
fun TopBarPreview() {
    FrontendAndroidTheme {
        TopNavBar("Title")
    }
}

@Preview
@Composable
fun TopBarIconPreview() {
    FrontendAndroidTheme {
        val navController = rememberNavController()
        TopNavBarWithIcon("Title", navController)
    }
}