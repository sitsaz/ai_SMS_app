package com.example.data.drive

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Base64
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

sealed interface DriveAccountTokenOutcome {
    data class Success(val accessToken: String, val accountEmail: String) : DriveAccountTokenOutcome
    data class Recoverable(val recoveryIntent: Intent, val accountEmail: String) : DriveAccountTokenOutcome
    data class Failure(val message: String, val isConfigurationError: Boolean = false) : DriveAccountTokenOutcome
}

data class PkceSession(
    val codeVerifier: String,
    val state: String,
    val clientId: String,
    val redirectUri: String,
    val authUrl: String
)

object GoogleDriveAuthHelper {

    private const val OAUTH_SCOPE_STRING =
        "oauth2:${GoogleDriveBackupManager.SCOPE_DRIVE_FILE} ${GoogleDriveBackupManager.SCOPE_DRIVE_APPDATA}"

    private val oauthHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build()
    }

    fun getAppPackageName(context: Context): String = context.packageName

    @Suppress("DEPRECATION")
    fun getAppSigningSha1(context: Context): String {
        return runCatching {
            val pm = context.packageManager
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                val signingInfo = info.signingInfo
                if (signingInfo?.hasMultipleSigners() == true) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo?.signingCertificateHistory
                }
            } else {
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
            }
            val certBytes = signatures?.firstOrNull()?.toByteArray() ?: return@runCatching "Unknown"
            val digest = MessageDigest.getInstance("SHA-1").digest(certBytes)
            digest.joinToString(":") { b -> "%02X".format(b) }
        }.getOrDefault("Unknown")
    }

    fun createGoogleAccountPickerIntent(selectedEmail: String? = null): Intent {
        val selected = selectedEmail?.takeIf { it.isNotBlank() }?.let { Account(it, "com.google") }
        return AccountManager.newChooseAccountIntent(
            selected,
            null,
            arrayOf("com.google"),
            null,
            null,
            null,
            null
        )
    }

    suspend fun requestTokenForAccount(
        context: Context,
        accountEmail: String,
        isPersian: Boolean
    ): DriveAccountTokenOutcome = withContext(Dispatchers.IO) {
        try {
            val account = Account(accountEmail, "com.google")
            val token = GoogleAuthUtil.getToken(context, account, OAUTH_SCOPE_STRING)
            if (token.isNotBlank()) {
                DriveAccountTokenOutcome.Success(token, accountEmail)
            } else {
                DriveAccountTokenOutcome.Failure(
                    if (isPersian) "توکن دسترسی خالی دریافت شد." else "Received empty Drive access token."
                )
            }
        } catch (recoverable: UserRecoverableAuthException) {
            val intent = recoverable.intent
            if (intent != null) {
                DriveAccountTokenOutcome.Recoverable(intent, accountEmail)
            } else {
                DriveAccountTokenOutcome.Failure(
                    recoverable.localizedMessage ?: "Consent required"
                )
            }
        } catch (err: Throwable) {
            val (msg, isConfigErr) = describeDriveAuthException(context, err, isPersian)
            DriveAccountTokenOutcome.Failure(msg, isConfigErr)
        }
    }

    suspend fun trySilentGoogleAuthUtilToken(context: Context, email: String): String? =
        withContext(Dispatchers.IO) {
            if (email.isBlank()) return@withContext null
            runCatching {
                val account = Account(email.trim(), "com.google")
                GoogleAuthUtil.getToken(context, account, OAUTH_SCOPE_STRING).takeIf { it.isNotBlank() }
            }.getOrNull()
        }

    fun startBrowserPkceSession(context: Context, customClientId: String): PkceSession? {
        val cleanClientId = customClientId.trim()
        if (cleanClientId.isBlank()) return null

        val random = SecureRandom()
        val verifierBytes = ByteArray(32)
        random.nextBytes(verifierBytes)
        val codeVerifier = Base64.encodeToString(verifierBytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

        val stateBytes = ByteArray(16)
        random.nextBytes(stateBytes)
        val state = Base64.encodeToString(stateBytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

        val challengeBytes = MessageDigest.getInstance("SHA-256").digest(codeVerifier.toByteArray(Charsets.US_ASCII))
        val codeChallenge = Base64.encodeToString(challengeBytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

        // Installed / Android app custom-scheme redirect URI matching package name
        val redirectUri = "${context.packageName}:/oauth2redirect"
        val authUrl = Uri.Builder()
            .scheme("https")
            .authority("accounts.google.com")
            .path("o/oauth2/v2/auth")
            .appendQueryParameter("client_id", cleanClientId)
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", "${GoogleDriveBackupManager.SCOPE_DRIVE_FILE} ${GoogleDriveBackupManager.SCOPE_DRIVE_APPDATA} email")
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("access_type", "offline")
            .appendQueryParameter("prompt", "consent")
            .appendQueryParameter("state", state)
            .build()
            .toString()

        return PkceSession(
            codeVerifier = codeVerifier,
            state = state,
            clientId = cleanClientId,
            redirectUri = redirectUri,
            authUrl = authUrl
        )
    }

    suspend fun exchangePkceCodeForTokens(
        session: PkceSession,
        code: String
    ): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        runCatching {
            val form = FormBody.Builder()
                .add("client_id", session.clientId)
                .add("code", code)
                .add("code_verifier", session.codeVerifier)
                .add("redirect_uri", session.redirectUri)
                .add("grant_type", "authorization_code")
                .build()

            val request = Request.Builder()
                .url("https://oauth2.googleapis.com/token")
                .post(form)
                .build()

            oauthHttpClient.newCall(request).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val err = runCatching { JSONObject(raw).optString("error_description") }.getOrDefault(raw)
                    throw IllegalStateException("OAuth Token HTTP ${resp.code}: $err")
                }
                val obj = JSONObject(raw)
                val access = obj.optString("access_token")
                val refresh = obj.optString("refresh_token")
                require(access.isNotBlank()) { "No access_token in OAuth response" }
                access to refresh
            }
        }
    }

    suspend fun refreshAccessToken(
        clientId: String,
        refreshToken: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(clientId.isNotBlank() && refreshToken.isNotBlank())
            val form = FormBody.Builder()
                .add("client_id", clientId.trim())
                .add("refresh_token", refreshToken.trim())
                .add("grant_type", "refresh_token")
                .build()
            val request = Request.Builder()
                .url("https://oauth2.googleapis.com/token")
                .post(form)
                .build()
            oauthHttpClient.newCall(request).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Refresh HTTP ${resp.code}: $raw")
                }
                val access = JSONObject(raw).optString("access_token")
                require(access.isNotBlank()) { "Empty refreshed access token" }
                access
            }
        }
    }

    fun describeDriveAuthException(
        context: Context,
        err: Throwable,
        isPersian: Boolean
    ): Pair<String, Boolean> {
        val apiEx = err as? ApiException
        val statusCode = apiEx?.statusCode
        val rawMsg = err.localizedMessage ?: err.message ?: err.javaClass.simpleName
        val pkg = getAppPackageName(context)
        val sha1 = getAppSigningSha1(context)

        val isConfig = statusCode == 10 ||
            statusCode == 12500 ||
            rawMsg.contains("DEVELOPER_ERROR", ignoreCase = true) ||
            rawMsg.contains("10:", ignoreCase = true) ||
            rawMsg.contains("12500", ignoreCase = true)

        val human = when {
            statusCode == 12501 || rawMsg.contains("12501") || rawMsg.contains("cancel", ignoreCase = true) -> {
                if (isPersian) "ورود به Google Drive توسط کاربر لغو شد." else "Google Drive sign-in was cancelled."
            }
            statusCode == 7 || rawMsg.contains("NETWORK_ERROR", ignoreCase = true) -> {
                if (isPersian) "خطای شبکه یا عدم دسترسی به سرویس Google Play." else "Network error reaching Google Play Services."
            }
            isConfig -> {
                if (isPersian) {
                    "خطای احراز هویت Google ($statusCode): برای امضای این APK یک Android OAuth Client در Google Cloud ثبت نشده است.\nPackage: $pkg\nSHA-1: $sha1\nمی‌توانید از روش انتخاب حساب / مرورگر یا توکن OAuth در پایین استفاده کنید."
                } else {
                    "Google OAuth config error ($statusCode): Android OAuth client for this APK signing key is not registered in Google Cloud.\nPackage: $pkg\nSHA-1: $sha1\nUse Account Fallback / Browser OAuth below."
                }
            }
            else -> {
                if (isPersian) "خطای ورود Google Drive: $rawMsg" else "Google Drive login error: $rawMsg"
            }
        }
        return human to isConfig
    }
}
