package com.charrlie.omleaf

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.charrlie.omleaf.ui.AppRoot
import com.charrlie.omleaf.ui.theme.OmLeafTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val repository = (application as OmLeafApp).repository
        setContent {
            OmLeafTheme {
                AppRoot(repository)
            }
        }
    }
}
