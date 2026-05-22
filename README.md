# ShareCleaner

LSPosed/Xposed 模块，在 App 分享链接到微信/QQ 时自动清理追踪参数。

## 原理

Hook 各 App 内集成的**微信 OpenSDK** 和 **QQ 互联 SDK** 的分享接口，在链接发送到微信/QQ 之前清理 URL 中的追踪参数。

### Hook 点

| SDK | 类 | 方法 | 作用 |
|-----|---|------|------|
| 微信 | `WXApiImplV10` | `sendReq()` | 拦截所有微信分享请求 |
| QQ | `Tencent` | `shareToQQ()` | 拦截 QQ 分享 |
| QQ | `Tencent` | `shareToQzone()` | 拦截 QZone 分享 |

### 清理对象

- `WXWebpageObject.webpageUrl` — 网页分享链接
- `WXMiniProgramObject.webpageUrl` — 小程序兜底链接
- `WXMediaMessage.description` — 描述中嵌入的 URL
- QQ Bundle `targetUrl` — QQ 分享目标链接

## 内置规则

| App | 模式 | 清理参数 |
|-----|------|---------|
| 小红书 | 黑名单 | xsec_token, xsec_source, app_platform... |
| 哔哩哔哩 | 黑名单 | spm_id_from, share_source, vd_source... |
| 抖音 | 黑名单 | share_token, utm_source, sec_uid... |
| 淘宝/天猫 | 白名单 | 仅保留 id |
| 京东 | 黑名单 | utm_*, ad_od, gx... |
| 拼多多 | 黑名单 | share_uin, refer_share_id... |
| 知乎 | 黑名单 | utm_* |
| 微博 | 黑名单 | from, wm, luicode... |
| 豆瓣 | 黑名单 | dt_dapp, dt_platform... |
| 网易云 | 黑名单 | userid, app_version... |
| 通用兜底 | 黑名单 | utm_*, fbclid, gclid, _ga... |

## 作用域

模块需要在 LSPosed 中对**源 App**（分享发起方）启用，不需要对微信/QQ 启用。

默认推荐作用域：
- 小红书、抖音、哔哩哔哩、微博、知乎、豆瓣
- 淘宝、天猫、京东、拼多多
- 网易云音乐、QQ音乐、今日头条

## 安装

1. 从 Actions 下载 APK
2. 安装并在 LSPosed 中启用
3. 勾选需要清理的 App 作用域
4. 强制停止对应 App 后重新打开

## 编译

```bash
./gradlew assembleDebug
```

## 要求

- Android 8.0+
- LSPosed / Xposed 框架
