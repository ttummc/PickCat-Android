package dao3.pickcat.core.network

import android.content.Context
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import org.json.JSONArray
import org.json.JSONObject

/**
 * OkHttp and WebView have completely separate cookie jars. This jar is the native
 * side: it persists every Set-Cookie the API sends, and [syncFromWebView] copies
 * the cookies that the official login page produced into the same store.
 */
class PersistentCookieJar(context: Context) : CookieJar {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val lock = Any()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        synchronized(lock) {
            val all = readAll().toMutableMap()
            for (cookie in cookies) {
                all[keyOf(cookie)] = cookie
            }
            writeAll(all.values)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val cookies = readAll()
            return cookies.values.filter { cookie ->
                (cookie.expiresAt == Long.MAX_VALUE || cookie.expiresAt > now) &&
                    (!cookie.secure || url.isHttps) &&
                    domainMatches(url.host, cookie)
            }.filter { cookie ->
                pathMatches(url.encodedPath, cookie.path)
            }.sortedByDescending { it.path.length }
        }
    }

    /**
     * Copies cookie name/value pairs exposed by Android's WebView CookieManager into
     * the OkHttp jar. WebView doesn't expose HttpOnly flags/expiry here, so the
     * copies are session cookies scoped to the exact PickCat host.
     */
    fun syncFromWebView(cookieHeader: String?, host: String = "pickcat.dao3.fun") {
        if (cookieHeader.isNullOrBlank()) return
        val cookies = cookieHeader.split(';').mapNotNull { part ->
            val raw = part.trim()
            val index = raw.indexOf('=')
            if (index <= 0) return@mapNotNull null
            val name = raw.substring(0, index).trim()
            val value = raw.substring(index + 1).trim()
            if (name.isEmpty()) return@mapNotNull null
            runCatching {
                Cookie.Builder()
                    .name(name)
                    .value(value)
                    .hostOnlyDomain(host)
                    .path("/")
                    .expiresAt(Long.MAX_VALUE)
                    .secure()
                    .httpOnly()
                    .build()
            }.getOrNull()
        }
        if (cookies.isEmpty()) return
        synchronized(lock) {
            val all = readAll().toMutableMap()
            for (cookie in cookies) {
                // WebView only exposes name/value pairs. Replace any native cookie
                // with the same name so the freshly observed login session wins.
                all.entries.removeAll { (_, existing) -> existing.name == cookie.name }
                all[keyOf(cookie)] = cookie
            }
            writeAll(all.values)
        }
    }

    fun peekSessionCookie(): Cookie? {
        synchronized(lock) {
            return readAll().values.firstOrNull {
                it.name.contains("session", ignoreCase = true) ||
                    it.name.equals("pickcat_session", ignoreCase = true)
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            prefs.edit().remove(KEY_COOKIES).apply()
        }
    }

    private fun keyOf(cookie: Cookie): String =
        "${cookie.name}|${cookie.domain}|${cookie.path}|${cookie.hostOnly}"

    private fun readAll(): Map<String, Cookie> {
        val raw = prefs.getString(KEY_COOKIES, null) ?: return emptyMap()
        return runCatching {
            val array = JSONArray(raw)
            val result = LinkedHashMap<String, Cookie>(array.length())
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val cookie = runCatching {
                    val builder = Cookie.Builder()
                        .name(item.getString("name"))
                        .value(item.getString("value"))
                        .path(item.optString("path", "/"))
                    val domain = item.optString("domain", "pickcat.dao3.fun")
                    if (item.optBoolean("hostOnly", true)) builder.hostOnlyDomain(domain)
                    else builder.domain(domain)
                    val expiresAt = item.optLong("expiresAt", Long.MAX_VALUE)
                    if (expiresAt > 0) builder.expiresAt(expiresAt)
                    if (item.optBoolean("secure", true)) builder.secure()
                    if (item.optBoolean("httpOnly", true)) builder.httpOnly()
                    builder.build()
                }.getOrNull() ?: continue
                result[keyOf(cookie)] = cookie
            }
            result
        }.getOrDefault(emptyMap())
    }

    private fun writeAll(cookies: Collection<Cookie>) {
        val array = JSONArray()
        for (cookie in cookies) {
            array.put(
                JSONObject()
                    .put("name", cookie.name)
                    .put("value", cookie.value)
                    .put("domain", cookie.domain)
                    .put("path", cookie.path)
                    .put("hostOnly", cookie.hostOnly)
                    .put("expiresAt", cookie.expiresAt)
                    .put("secure", cookie.secure)
                    .put("httpOnly", cookie.httpOnly),
            )
        }
        prefs.edit().putString(KEY_COOKIES, array.toString()).apply()
    }

    private fun domainMatches(host: String, cookie: Cookie): Boolean {
        if (cookie.hostOnly) return host.equals(cookie.domain, ignoreCase = true)
        val domain = cookie.domain.removePrefix(".")
        return host.equals(domain, ignoreCase = true) ||
            host.endsWith(".$domain", ignoreCase = true)
    }

    private fun pathMatches(requestPath: String, cookiePath: String): Boolean {
        if (requestPath == cookiePath) return true
        if (!requestPath.startsWith(cookiePath)) return false
        return cookiePath.endsWith("/") || requestPath[cookiePath.length] == '/'
    }

    private companion object {
        const val PREFS = "pickcat_web_cookies"
        const val KEY_COOKIES = "cookies_json"
    }
}
