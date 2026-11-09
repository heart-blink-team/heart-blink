package kr.heartblink.car.data

import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.withLock
import kr.heartblink.car.BuildConfig

/**
 * The one Supabase client of the app. Built lazily on first use.
 *
 * When SUPABASE_URL or SUPABASE_ANON_KEY is empty in local.properties, [enabled] is false,
 * [client] is null and the repository does nothing, so the demo screens keep working.
 *
 * Each car is an anonymous Supabase user. The session is saved by supabase-kt
 * (SharedPreferences through multiplatform-settings) and reloaded on the next start,
 * so the same car keeps the same user id across restarts.
 */
object Backend {
    private const val TAG = "Backend"

    val url: String = BuildConfig.SUPABASE_URL
    val enabled: Boolean = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    val client: SupabaseClient? by lazy {
        if (!enabled) null
        else createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY) {
            httpEngine = OkHttp.create()
            install(Auth)
            install(Postgrest)
            install(Storage)
            install(Realtime)
        }
    }

    private val signInLock = Mutex()

    /**
     * My user id, signing in anonymously the first time. Null when offline.
     * Throws [HeartError.Network] when a saved session exists but cannot be refreshed right now;
     * in that case we must not sign in again, or the car would become a new user and lose its profile.
     */
    suspend fun userId(): String? {
        val c = client ?: return null
        return signInLock.withLock {
            val auth = c.auth
            auth.awaitInitialization()
            when (val status = auth.sessionStatus.value) {
                is SessionStatus.Authenticated ->
                    status.session.user?.id ?: auth.retrieveUserForCurrentSession(updateSession = true).id
                is SessionStatus.RefreshFailure -> {
                    // the automatic refresh can give up for good when the app went to the background in the
                    // middle of it, so load the saved session again, which refreshes it right away
                    Log.i(TAG, "session refresh had failed, trying again")
                    auth.loadFromStorage(autoRefresh = true)
                    val now = withTimeoutOrNull(15_000) {
                        auth.sessionStatus.first { it is SessionStatus.Authenticated || it is SessionStatus.NotAuthenticated }
                    }
                    if (now !is SessionStatus.Authenticated) throw HeartError.Network
                    now.session.user?.id ?: auth.retrieveUserForCurrentSession(updateSession = true).id
                }
                else -> {
                    Log.i(TAG, "no saved session, signing in anonymously")
                    auth.signInAnonymously()
                    auth.currentUserOrNull()?.id ?: auth.retrieveUserForCurrentSession(updateSession = true).id
                }
            }
        }
    }
}
