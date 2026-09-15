package com.live.vant.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.live.vant.feedback.LocalVanToastController
import com.live.vant.feedback.VanToastHost
import com.live.vant.feedback.rememberVanToastController
import com.live.vant.theme.VantTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VantTheme {
                val toastController = rememberVanToastController()
                CompositionLocalProvider(LocalVanToastController provides toastController) {
                    DemoApp()
                    // Toast 宿主：任意组件内 LocalVanToastController.current.success("...") 即可弹出
                    VanToastHost(toastController)
                }
            }
        }
    }
}

@Composable
fun DemoApp() {
    DemoShell()
}
