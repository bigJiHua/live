package com.live.finance

import android.app.Application
import com.live.finance.di.AppGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class App : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { graph.init() }
    }

    companion object {
        fun of(context: android.content.Context): App =
            context.applicationContext as App
    }
}
