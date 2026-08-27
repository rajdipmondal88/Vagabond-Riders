package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.webkit.WebView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.CredentialRepository
import com.example.data.SavedCredential
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject

class PasswordManagerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: CredentialRepository
    private val prefs = application.getSharedPreferences("vr_password_prefs", Context.MODE_PRIVATE)

    val savedCredentials: StateFlow<List<SavedCredential>>

    private val _autoFillOnPageLoad = MutableStateFlow(
        prefs.getBoolean("pref_autofill_on_pageload", true)
    )
    val autoFillOnPageLoad: StateFlow<Boolean> = _autoFillOnPageLoad.asStateFlow()

    private val _lastAutofilledAccount = MutableStateFlow<String?>(null)
    val lastAutofilledAccount: StateFlow<String?> = _lastAutofilledAccount.asStateFlow()

    init {
        val db = AppDatabase.getDatabase(application)
        repository = CredentialRepository(db.credentialDao())
        savedCredentials = repository.allCredentials.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    fun setAutoFillOnPageLoad(enabled: Boolean) {
        _autoFillOnPageLoad.value = enabled
        prefs.edit().putBoolean("pref_autofill_on_pageload", enabled).apply()
    }

    fun saveCredential(
        accountLabel: String,
        username: String,
        password: String,
        role: String = "USER",
        autoSubmit: Boolean = false,
        notes: String = "",
        existingId: Long = 0L
    ) {
        viewModelScope.launch {
            val credential = SavedCredential(
                id = existingId,
                accountLabel = accountLabel.ifBlank { username },
                username = username.trim(),
                password = password,
                role = role,
                autoSubmit = autoSubmit,
                lastUsedTimestamp = System.currentTimeMillis(),
                notes = notes.trim()
            )
            repository.saveCredential(credential)
        }
    }

    fun deleteCredential(credential: SavedCredential) {
        viewModelScope.launch {
            repository.deleteCredential(credential)
        }
    }

    fun deleteCredentialById(id: Long) {
        viewModelScope.launch {
            repository.deleteById(id)
        }
    }

    /**
     * Auto-fill credentials directly into the WebView input fields (Username & Password boxes)
     * When triggerSubmit is false, the username & password remain filled in the boxes ready for 1-tap "Login".
     */
    fun autofillIntoWebView(
        credential: SavedCredential,
        webView: WebView?,
        triggerSubmit: Boolean = false,
        onSuccess: () -> Unit = {}
    ) {
        if (webView == null) return

        viewModelScope.launch {
            repository.markLastUsed(credential.id)
            _lastAutofilledAccount.value = credential.accountLabel

            val safeUser = JSONObject.quote(credential.username)
            val safePass = JSONObject.quote(credential.password)
            val autoClick = if (triggerSubmit) "true" else "false"

            // Comprehensive JS Form Injector that covers all web login form input elements
            val js = """
                (function() {
                    try {
                        var usernameValue = $safeUser;
                        var passwordValue = $safePass;
                        var shouldSubmit = $autoClick;

                        // Find username field
                        var userField = document.querySelector('input[type="text"], input[type="email"], input[name="username"], input[name="user"], input[name="email"], input[name="userid"], input[name="login"], input[id*="user"], input[id*="email"], input[id*="login"], input[placeholder*="user" i], input[placeholder*="email" i], input[placeholder*="login" i]');
                        
                        // Find password field
                        var passField = document.querySelector('input[type="password"], input[name="password"], input[name="pass"], input[name="pwd"], input[id*="pass"], input[id*="pwd"], input[placeholder*="pass" i]');

                        if (userField) {
                            userField.focus();
                            userField.value = usernameValue;
                            userField.dispatchEvent(new Event('input', { bubbles: true }));
                            userField.dispatchEvent(new Event('change', { bubbles: true }));
                            userField.dispatchEvent(new KeyboardEvent('keyup', { bubbles: true }));
                        }

                        if (passField) {
                            passField.focus();
                            passField.value = passwordValue;
                            passField.dispatchEvent(new Event('input', { bubbles: true }));
                            passField.dispatchEvent(new Event('change', { bubbles: true }));
                            passField.dispatchEvent(new KeyboardEvent('keyup', { bubbles: true }));
                        }

                        if (shouldSubmit) {
                            setTimeout(function() {
                                var submitBtn = document.querySelector('button[type="submit"], input[type="submit"], button.btn-primary, button.login-btn, input.login-btn, #submit, #login-btn, button:not([type="button"])');
                                if (submitBtn) {
                                    submitBtn.click();
                                } else {
                                    var form = (passField && passField.form) || (userField && userField.form) || document.querySelector('form');
                                    if (form) {
                                        form.submit();
                                    }
                                }
                            }, 250);
                        }
                        return "SUCCESS";
                    } catch(e) {
                        return "ERROR: " + e.message;
                    }
                })();
            """.trimIndent()

            webView.evaluateJavascript(js) {
                onSuccess()
            }
        }
    }

    /**
     * Auto-fills the most recently used / primary account onto the login screen on page load or after logout.
     */
    fun performAutoFillOnLoginLoad(webView: WebView?) {
        if (webView == null || !_autoFillOnPageLoad.value) return

        val credentials = savedCredentials.value
        if (credentials.isEmpty()) return

        // Pick the most recently used account
        val targetAccount = credentials.maxByOrNull { it.lastUsedTimestamp } ?: credentials.first()

        viewModelScope.launch {
            // Immediate fill
            autofillIntoWebView(targetAccount, webView, triggerSubmit = false)
            // Retry after 400ms for dynamic DOMs
            delay(400)
            autofillIntoWebView(targetAccount, webView, triggerSubmit = false)
            // Final check after 1000ms
            delay(600)
            autofillIntoWebView(targetAccount, webView, triggerSubmit = false)
        }
    }
}
