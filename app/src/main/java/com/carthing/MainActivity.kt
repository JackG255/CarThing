package com.carthing

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.carthing.ui.CarThingTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { CarThingTheme { HomePlaceholder() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomePlaceholder() {
    Scaffold(topBar = { TopAppBar(title = { Text("CarThing") }) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Text("No vehicles yet")
        }
    }
}
