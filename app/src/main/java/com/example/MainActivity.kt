package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.webkit.CookieManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.auth.GoogleOAuthHelper
import com.example.notifications.PushAlarmReceiver
import com.example.notifications.PushNotificationManager
import com.example.ui.VRAppScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
        const val MAIN_PORTAL_HOST = "app.vagabondriders.com"
        const val MAIN_PORTAL_URL = "https://app.vagabondriders.com/index.php"
        const val MEMBERSHIP_PROFILE_URL = "https://membership.vagabondriders.com/profile.php"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        PushNotificationManager.init(this)
        handleIntent(intent)

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    VRAppScreen()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        GoogleOAuthHelper.onAppResumed()
        PushNotificationManager.startPeriodicPushSync(this)
        PushAlarmReceiver.scheduleNextAlarm(this, 1000L)
    }

    override fun onPause() {
        super.onPause()
        // Ensure background alarm loop continues checking notifications while app is minimized
        PushAlarmReceiver.scheduleNextAlarm(this, 5000L)
    }

    override fun onStop() {
        super.onStop()
        // Arm alarm for reliable background reception when completely hidden/minimized
        PushAlarmReceiver.scheduleNextAlarm(this, 10_000L)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        val dataUri = intent.data
        val extraTargetUrl = intent.getStringExtra("extra_target_url")
        val targetUrl = extraTargetUrl ?: dataUri?.toString()

        // 1. Direct cookie extraction and injection across membership and app domains
        extractAndInjectCookies(intent, dataUri, targetUrl)

        // 2. Delegate to GoogleOAuthHelper to resolve destination and broadcast to active WebView
        if (!targetUrl.isNullOrBlank()) {
            GoogleOAuthHelper.handleIncomingRedirect(targetUrl)
        }
    }

    /**
     * Extracts all session cookies (PHPSESSID, token, auth_token, etc.) from Intent extras,
     * Uri query parameters, and Uri fragments, and directly injects them into CookieManager
     * targeting membership.vagabondriders.com and app.vagabondriders.com.
     */
    private fun extractAndInjectCookies(intent: Intent, uri: Uri?, rawUrl: String?) {
        try {
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)

            val cookiesToSet = mutableMapOf<String, String>()

            // Extract from Intent extras if present
            intent.extras?.keySet()?.forEach { key ->
                val lowerKey = key.lowercase()
                val value = intent.getStringExtra(key)
                if (!value.isNullOrBlank()) {
                    if (lowerKey.contains("phpsessid") || lowerKey.contains("session") || lowerKey.contains("token") || lowerKey.contains("jwt") || lowerKey.contains("cookie")) {
                        cookiesToSet[key] = value
                    }
                }
            }

            // Extract from Uri query parameters & fragment
            if (uri != null) {
                if (uri.isHierarchical) {
                    uri.queryParameterNames.forEach { name ->
                        val value = uri.getQueryParameter(name)
                        if (!value.isNullOrBlank()) {
                            cookiesToSet[name] = value
                        }
                    }
                }

                // Check fragment (e.g. #PHPSESSID=xxx&token=yyy)
                uri.fragment?.let { fragment ->
                    fragment.split("&").forEach { pair ->
                        val parts = pair.split("=", limit = 2)
                        if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                            cookiesToSet[parts[0]] = parts[1]
                        }
                    }
                }
            }

            // Target domains to ensure full session availability across subdomains and base domain
            val targetDomains = listOf(
                "https://membership.vagabondriders.com",
                "https://membership.vagabondriders.com/",
                "http://membership.vagabondriders.com",
                "https://app.vagabondriders.com",
                "https://app.vagabondriders.com/",
                "http://app.vagabondriders.com",
                "https://vagabondriders.com"
            )

            // Inject PHP session cookie and any auth tokens
            cookiesToSet.forEach { (name, value) ->
                val isSessionOrAuth = name.equals("PHPSESSID", ignoreCase = true) ||
                        name.equals("session_id", ignoreCase = true) ||
                        name.equals("sessionId", ignoreCase = true) ||
                        name.equals("session", ignoreCase = true) ||
                        name.equals("token", ignoreCase = true) ||
                        name.equals("auth_token", ignoreCase = true) ||
                        name.equals("vr_auth_token", ignoreCase = true) ||
                        name.equals("jwt", ignoreCase = true) ||
                        name.equals("access_token", ignoreCase = true) ||
                        name.equals("user_id", ignoreCase = true)

                if (isSessionOrAuth) {
                    val cookieName = if (name.equals("phpsessid", ignoreCase = true) || name.equals("session_id", ignoreCase = true) || name.equals("sessionId", ignoreCase = true)) {
                        "PHPSESSID"
                    } else {
                        name
                    }

                    targetDomains.forEach { domainUrl ->
                        // Set standard subdomain cookie
                        cookieManager.setCookie(
                            domainUrl,
                            "$cookieName=$value; Path=/; Domain=membership.vagabondriders.com; Secure; SameSite=Lax"
                        )
                        // Set parent wildcard domain cookie
                        cookieManager.setCookie(
                            domainUrl,
                            "$cookieName=$value; Path=/; Domain=.vagabondriders.com; Secure; SameSite=Lax"
                        )
                        // Set host-only cookie
                        cookieManager.setCookie(
                            domainUrl,
                            "$cookieName=$value; Path=/"
                        )
                    }
                }
            }

            cookieManager.flush()
            Log.d(TAG, "Successfully injected ${cookiesToSet.size} cookies into CookieManager")
        } catch (e: Exception) {
            Log.e(TAG, "Error injecting cookies into CookieManager", e)
        }
    }
}
