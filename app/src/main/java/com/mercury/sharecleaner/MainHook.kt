package com.mercury.sharecleaner

import android.net.Uri
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class MainHook : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        XposedBridge.log("[$TAG] Loaded in ${lpparam.packageName}")
        hookWeChatSDK(lpparam.classLoader)
        hookQQSDK(lpparam.classLoader)
    }

    private fun hookWeChatSDK(classLoader: ClassLoader) {
        try {
            val wxApiClass = XposedHelpers.findClass(
                "com.tencent.mm.opensdk.openapi.BaseWXApiImplV10", classLoader
            )
            XposedHelpers.findAndHookMethod(wxApiClass, "sendReq",
                XposedHelpers.findClass("com.tencent.mm.opensdk.modelbase.BaseReq", classLoader),
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val req = param.args[0] ?: return
                        if (req.javaClass.name.contains("SendMessageToWX")) {
                            cleanWXMessage(req)
                        }
                    }
                }
            )
            XposedBridge.log("[$TAG] WeChat SDK hooked")
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] WeChat SDK not found: ${e.message}")
        }
    }

    private fun hookQQSDK(classLoader: ClassLoader) {
        try {
            val tencentClass = XposedHelpers.findClass("com.tencent.tauth.Tencent", classLoader)
            val listenerClass = XposedHelpers.findClass("com.tencent.tauth.IUiListener", classLoader)

            XposedHelpers.findAndHookMethod(tencentClass, "shareToQQ",
                android.app.Activity::class.java, android.os.Bundle::class.java, listenerClass,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        (param.args[1] as? android.os.Bundle)?.let { cleanQQBundle(it) }
                    }
                }
            )
            XposedHelpers.findAndHookMethod(tencentClass, "shareToQzone",
                android.app.Activity::class.java, android.os.Bundle::class.java, listenerClass,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        (param.args[1] as? android.os.Bundle)?.let { cleanQQBundle(it) }
                    }
                }
            )
            XposedBridge.log("[$TAG] QQ SDK hooked")
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] QQ SDK not found: ${e.message}")
        }
    }

    private fun cleanWXMessage(req: Any) {
        try {
            val msg = XposedHelpers.getObjectField(req, "msg") ?: return
            val mediaObject = XposedHelpers.getObjectField(msg, "mediaObject") ?: return
            val className = mediaObject.javaClass.name

            if (className.contains("WXWebpageObject") || className.contains("WXMiniProgramObject")) {
                val url = XposedHelpers.getObjectField(mediaObject, "webpageUrl") as? String
                if (url != null) {
                    val cleaned = cleanUrl(url)
                    if (cleaned != url) {
                        XposedHelpers.setObjectField(mediaObject, "webpageUrl", cleaned)
                        XposedBridge.log("[$TAG] WX: $url → $cleaned")
                    }
                }
            }

            val desc = XposedHelpers.getObjectField(msg, "description") as? String
            if (desc != null && desc.contains("http")) {
                val cleaned = cleanTextUrls(desc)
                if (cleaned != desc) XposedHelpers.setObjectField(msg, "description", cleaned)
            }
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] Error cleaning WX message: ${e.message}")
        }
    }

    private fun cleanQQBundle(bundle: android.os.Bundle) {
        val url = bundle.getString("targetUrl") ?: return
        val cleaned = cleanUrl(url)
        if (cleaned != url) {
            bundle.putString("targetUrl", cleaned)
            XposedBridge.log("[$TAG] QQ: $url → $cleaned")
        }
    }

    // ========== URL Cleaning Engine ==========

    private fun cleanUrl(url: String): String {
        val uri = try { Uri.parse(url) } catch (_: Exception) { return url }
        val host = uri.host?.lowercase() ?: return url
        val rule = RULES.find { r -> r.matches(host) } ?: return url
        return when (rule.mode) {
            Mode.BLACKLIST -> removeParams(uri, rule.params)
            Mode.WHITELIST -> keepOnlyParams(uri, rule.params)
        }
    }

    private fun cleanTextUrls(text: String): String {
        return Regex("https?://\\S+").replace(text) { m -> cleanUrl(m.value) }
    }

    private fun removeParams(uri: Uri, params: Set<String>): String {
        val builder = uri.buildUpon().clearQuery()
        for (name in uri.queryParameterNames) {
            if (name.lowercase() !in params) {
                builder.appendQueryParameter(name, uri.getQueryParameter(name))
            }
        }
        return builder.build().toString()
    }

    private fun keepOnlyParams(uri: Uri, params: Set<String>): String {
        val builder = uri.buildUpon().clearQuery()
        for (name in uri.queryParameterNames) {
            if (name.lowercase() in params) {
                builder.appendQueryParameter(name, uri.getQueryParameter(name))
            }
        }
        return builder.build().toString()
    }

    // ========== Rules ==========

    private enum class Mode { BLACKLIST, WHITELIST }

    private data class Rule(val hostPattern: String, val mode: Mode, val params: Set<String>) {
        fun matches(host: String): Boolean {
            if (hostPattern == "*.") return true // universal fallback
            if (hostPattern.startsWith("*.")) return host.endsWith(hostPattern.removePrefix("*"))
            return host == hostPattern || host.endsWith(".$hostPattern")
        }
    }

    companion object {
        private const val TAG = "ShareCleaner"

        private val RULES = listOf(
            Rule("xiaohongshu.com", Mode.BLACKLIST, setOf(
                "xsec_token", "xsec_source", "app_platform", "app_version",
                "share_from_user_hidden", "xhsshare", "appuid", "apptime")),
            Rule("bilibili.com", Mode.BLACKLIST, setOf(
                "spm_id_from", "from_source", "from_spmid", "from",
                "share_source", "share_medium", "share_plat", "share_session_id",
                "share_tag", "share_times", "timestamp", "bbid", "ts",
                "unique_k", "vd_source", "csource", "seid", "rt", "is_story_h5")),
            Rule("douyin.com", Mode.BLACKLIST, setOf(
                "previous_page", "app_id", "use_new_share", "share_token",
                "timestamp", "sec_uid", "utm_source", "utm_medium",
                "utm_campaign", "is_copy_url")),
            Rule("taobao.com", Mode.WHITELIST, setOf("id")),
            Rule("tmall.com", Mode.WHITELIST, setOf("id")),
            Rule("jd.com", Mode.BLACKLIST, setOf(
                "utm_source", "utm_medium", "utm_campaign", "utm_term",
                "ad_od", "gx", "gca")),
            Rule("pinduoduo.com", Mode.BLACKLIST, setOf(
                "share_uin", "refer_share_id", "refer_share_uin",
                "share_uid", "utm_source", "utm_medium")),
            Rule("zhihu.com", Mode.BLACKLIST, setOf(
                "utm_source", "utm_medium", "utm_campaign",
                "utm_content", "utm_id", "utm_oi")),
            Rule("weibo.com", Mode.BLACKLIST, setOf(
                "from", "wm", "sourcetype", "luicode", "lfid", "featurecode")),
            Rule("weibo.cn", Mode.BLACKLIST, setOf(
                "from", "wm", "sourcetype", "luicode", "lfid", "featurecode")),
            Rule("douban.com", Mode.BLACKLIST, setOf(
                "dt_dapp", "dt_platform", "from")),
            Rule("163.com", Mode.BLACKLIST, setOf(
                "userid", "app_version", "uct2", "dlt", "uct")),
            // Universal UTM fallback
            Rule("*.", Mode.BLACKLIST, setOf(
                "utm_source", "utm_medium", "utm_campaign",
                "utm_term", "utm_content", "utm_id",
                "fbclid", "gclid", "gclsrc", "_ga", "_gl")),
        )
    }
}
