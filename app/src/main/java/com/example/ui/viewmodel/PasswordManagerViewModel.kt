package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.webkit.WebView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.auth.SavePasswordPromptRequest
import com.example.auth.VRAuthJavascriptBridge
import com.example.data.AppDatabase
import com.example.data.CredentialRepository
import com.example.data.SavedCredential
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    val pendingSaveRequest: StateFlow<SavePasswordPromptRequest?> = VRAuthJavascriptBridge.pendingSaveRequest
    val hasLoginFormDetected: StateFlow<Boolean> = VRAuthJavascriptBridge.hasLoginFormDetected

    init {
        val db = AppDatabase.getDatabase(application)
        repository = CredentialRepository(db.credentialDao())
        savedCredentials = repository.allCredentials.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Clean up any legacy template accounts
        viewModelScope.launch {
            try {
                val all = repository.allCredentials
                // Remove pre-installed dummy/template accounts if they exist
                repository.allCredentials.collect { creds ->
                    creds.filter { 
                        it.username == "rider@vagabondriders.com" || 
                        it.username == "admin@vagabondriders.com" ||
                        it.username.contains("bhagavanriders.com", ignoreCase = true)
                    }.forEach {
                        repository.deleteCredential(it)
                    }
                }
            } catch (_: Exception) {}
        }
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
            VRAuthJavascriptBridge.clearPendingSaveRequest()
        }
    }

    fun confirmPendingSaveRequest(
        accountLabel: String,
        role: String = "USER",
        autoSubmit: Boolean = false,
        notes: String = ""
    ) {
        val req = pendingSaveRequest.value ?: return
        saveCredential(
            accountLabel = accountLabel.ifBlank { req.appName.ifBlank { req.username } },
            username = req.username,
            password = req.password,
            role = role,
            autoSubmit = autoSubmit,
            notes = notes
        )
    }

    fun dismissPendingSaveRequest() {
        VRAuthJavascriptBridge.clearPendingSaveRequest()
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

            // Comprehensive JS Form Injector with Framework compatibility (React, Vue, Angular, Native HTML)
            val js = """
                (function() {
                    try {
                        var usernameValue = $safeUser;
                        var passwordValue = $safePass;
                        var shouldSubmit = $autoClick;

                        function setNativeValue(element, value) {
                            if (!element) return;
                            try {
                                var valueSetter = Object.getOwnPropertyDescriptor(element, 'value') ? 
                                                  Object.getOwnPropertyDescriptor(element, 'value').set : null;
                                var prototype = Object.getPrototypeOf(element);
                                var prototypeValueSetter = Object.getOwnPropertyDescriptor(prototype, 'value') ? 
                                                          Object.getOwnPropertyDescriptor(prototype, 'value').set : null;
                                
                                if (prototypeValueSetter && valueSetter !== prototypeValueSetter) {
                                    prototypeValueSetter.call(element, value);
                                } else if (valueSetter) {
                                    valueSetter.call(element, value);
                                } else {
                                    element.value = value;
                                }
                            } catch(ex) {
                                element.value = value;
                            }
                            
                            element.dispatchEvent(new Event('input', { bubbles: true }));
                            element.dispatchEvent(new Event('change', { bubbles: true }));
                            element.dispatchEvent(new KeyboardEvent('keydown', { bubbles: true, key: 'a' }));
                            element.dispatchEvent(new KeyboardEvent('keyup', { bubbles: true, key: 'a' }));
                        }

                        // Find password field first
                        var passField = document.querySelector('input[type="password"], input[name="password" i], input[name="pass" i], input[name="pwd" i], input[id*="pass" i], input[id*="pwd" i], input[placeholder*="pass" i]');
                        
                        // Find username field (prioritize within same form if password field is found)
                        var form = passField ? passField.closest('form') : null;
                        var root = form || document;

                        var userField = root.querySelector('input[type="text"], input[type="email"], input[name="username" i], input[name="user" i], input[name="email" i], input[name="userid" i], input[name="login" i], input[id*="user" i], input[id*="email" i], input[id*="login" i], input[placeholder*="user" i], input[placeholder*="email" i], input[placeholder*="login" i], input[placeholder*="mobile" i], input[placeholder*="phone" i]');
                        
                        if (!userField && root !== document) {
                            userField = document.querySelector('input[type="text"], input[type="email"], input[name="username" i], input[name="user" i], input[name="email" i], input[id*="user" i], input[id*="email" i], input[id*="login" i]');
                        }

                        var filledAny = false;

                        if (userField) {
                            userField.focus();
                            setNativeValue(userField, usernameValue);
                            userField.style.backgroundColor = '#FFF7ED';
                            userField.style.borderColor = '#EA580C';
                            filledAny = true;
                        }

                        if (passField) {
                            passField.focus();
                            setNativeValue(passField, passwordValue);
                            passField.style.backgroundColor = '#FFF7ED';
                            passField.style.borderColor = '#EA580C';
                            filledAny = true;
                        }

                        if (shouldSubmit) {
                            setTimeout(function() {
                                var submitBtn = (form && form.querySelector('button[type="submit"], input[type="submit"], button.btn-primary, button.login-btn, input.login-btn, #submit, #login-btn, button:not([type="button"])')) ||
                                                document.querySelector('button[type="submit"], input[type="submit"], button.btn-primary, button.login-btn, input.login-btn, #submit, #login-btn, [data-action="login"], [data-action="signin"], button.signin, button.login');
                                if (submitBtn) {
                                    submitBtn.click();
                                } else if (form) {
                                    form.submit();
                                } else if (passField) {
                                    // Dispatch Enter key event as fallback
                                    passField.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true }));
                                    passField.dispatchEvent(new KeyboardEvent('keypress', { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true }));
                                    passField.dispatchEvent(new KeyboardEvent('keyup', { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true }));
                                }
                            }, 250);
                        }
                        return filledAny ? "SUCCESS" : "NO_FIELDS_FOUND";
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
