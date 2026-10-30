package com.live.finance

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.decode.SvgDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
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
        // Coil 2.7 默认不解码 SVG，而银行 logo 全是 .svg ⇒ 注册 SVG 解码器。
        // 同时显式开 内存(25%)+磁盘(256MB) 缓存，并忽略远端 `no-store`：
        //   web 浏览器对静态资源有 HTTP 缓存 + 同源单飞；原生要等价就得靠 Coil 内存/磁盘缓存，
        //   否则「流水列表/卡片流水/报表/账单/卡管理/快速切借记·信用卡」里同源 logo 会被反复拉取。
        // ⚠ 2026-10-10 修：原先包在 `ImageLoaderFactory { this }` 里，`this` 语义含糊（易拿不到真正的
        //   Application context），缓存目录/内存缓存可能没真正建起来 ⇒ 同源 logo 仍反复拉。这里直接以
        //   `this@App` 构建并 `setImageLoader(ImageLoader)`，context 明确无歧义。
        graph = AppGraph(this)
        Coil.setImageLoader(
            ImageLoader.Builder(this@App)
                .components { add(SvgDecoder.Factory()) }
                .memoryCache { MemoryCache.Builder(this@App).maxSizePercent(0.25).build() }
                .diskCache {
                    DiskCache.Builder()
                        .directory(this@App.cacheDir.resolve("image_cache")) // 持久化，冷启动也不再重拉
                        .maxSizeBytes(256L * 1024 * 1024)   // 256MB
                        .build()
                }
                .respectCacheHeaders(false)                 // 银行 logo / 静态资源照缓存，跳过服务端 no-store
                // 🔔 玻璃 backdrop 脏信号（2026-10-23）：图片加载完成是「非滚动类内容变化」，
                //   玻璃的背景 Picture 不会因子页重绘自动重录，必须在加载完成时显式失效一次
                //   （只 bump 计数 ⇒ 只触发底栏玻璃重绘，不触发页面重组）。
                .eventListener(object : coil.EventListener {
                    override fun onSuccess(request: coil.request.ImageRequest, result: coil.request.SuccessResult) {
                        graph.bumpGlassBackdrop()
                    }
                    override fun onError(request: coil.request.ImageRequest, result: coil.request.ErrorResult) {
                        graph.bumpGlassBackdrop()
                    }
                })
                .build(),
        )
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            // 🔒 安全整改 P1-28（2026-10-08）：启动时算自身签名 SHA-256，
            //   存 AppConfig 供 DeviceProfile 每请求上报（API 端 appSignatureGuard 白名单比对）。
            //   PackageManager 首调可能慢 ⇒ 放 IO 协程；失败为 null，请求头不上报。
            com.live.finance.core.AppConfig.appSignSha256 =
                com.live.finance.core.AppConfig.computeSelfSignature(this@App)

            // 🔧 站点分发适配（2026-10-14）：登录页/设置页保存过的地址优先于构建期默认值。
            //   APK 打一份包即可适配所有部署——用户填入自己站点的 API 地址与分享 URL（Web 端），
            //   存本地（SiteStore），此后每次启动读出来覆盖 AppConfig.baseUrl / siteUrl。
            //   网络层每请求动态读 AppConfig.url()，此处赋值早于 graph.init() 的首批请求。
            val siteStore = com.live.finance.core.store.SiteStore(this@App)
            siteStore.current()?.let {
                com.live.finance.core.AppConfig.baseUrl = it
            }
            // 分享 URL（Web 端地址）：只认本机保存值。
            // 🚫 2026-10-17：不再回落构建期 SITE_URL —— 构建期无任何占位地址；
            //   未填写时 AppConfig.siteUrl 保持空串，分享入口直接阻断（DiaryDetailScreen）。
            val savedShare = siteStore.shareUrl()
            com.live.finance.core.AppConfig.siteUrl = savedShare ?: ""

            graph.init()
        }
    }

    companion object {
        fun of(context: android.content.Context): App =
            context.applicationContext as App
    }
}
