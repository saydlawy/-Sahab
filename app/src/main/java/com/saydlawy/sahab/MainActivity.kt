package com.saydlawy.sahab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import com.saydlawy.sahab.ui.AppRoot
import com.saydlawy.sahab.ui.LocalContainer
import com.saydlawy.sahab.ui.LocalIsTv
import com.saydlawy.sahab.ui.theme.SahabTheme
import com.saydlawy.sahab.util.isTelevision

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as SahabApp).container
        val tv = isTelevision(this)
        setContent {
            SahabTheme {
                CompositionLocalProvider(
                    LocalContainer provides container,
                    LocalIsTv provides tv,
                ) {
                    AppRoot(container)
                }
            }
        }
    }
}
