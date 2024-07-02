package io.phone.build.voiplib.example

import android.content.Context
import android.os.Build
import android.util.Base64
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.phone.build.voipsdkandroid.push.Middleware
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

class VoIPGRIDMiddleware(private val context: Context) : Middleware {

    private var handling: String? = null
    private val prefs by lazy {
        PreferenceManager.getDefaultSharedPreferences(context)
    }

    private val client = OkHttpClient()
    suspend fun register(token: String): Boolean = withContext(Dispatchers.IO) {
        print("[TOKEN] $token")
        val decodedBytes1 = Base64.decode(token, Base64.DEFAULT)
        val decodedString1 = String(decodedBytes1)
        val decodedBytes2 = Base64.decode(decodedString1, Base64.DEFAULT)
        val decodedString2 = String(decodedBytes2)
        val decodedBytes3 = Base64.decode(decodedString2, Base64.DEFAULT)
        val decodedString3 = String(decodedBytes3)

        print("[RETURN]  ${decodedString3}")

        val key = "b6aed9ab7cdf85432c321757b4d48153"

        val parts = decodedString3.split(key)

        for (part in parts) {
            print(part)
        }


        return@withContext true
    }

    suspend fun unregister(): Boolean = withContext(Dispatchers.IO) {
        val data = FormBody.Builder().apply {
            add("token", token!!)
            add("sip_user_id", prefs.getString("username", "")!!)
            add("app", context.packageName!!)
        }.build()

        val request = createMiddlewareRequest()
            .delete(data)
            .build()

        return@withContext client.newCall(request).execute().isSuccessful
    }

    private fun createMiddlewareRequest(url: String = "https://vialerpush.voipgrid.nl/api/android-device/") = Request.Builder()
        .url(url)
        .addHeader("Authorization", "Token ${prefs.getString("voipgrid_username", "")}:${prefs.getString("voipgrid_api_token", "")}")

    /*override fun respond(
        remoteMessage: RemoteMessage,
        available: Boolean,
        reason: UnavailableReason?,
    ) {
        val data = FormBody.Builder().apply {
            add("unique_key", remoteMessage.data["unique_key"]!!)
            add("available", if (available) "true" else "false")
            add("message_start_time", remoteMessage.data["message_start_time"]!!)
            add("sip_user_id", prefs.getString("username", "")!!)
        }.build()

        val request = createMiddlewareRequest("https://vialerpush.voipgrid.nl/api/call-response/")
            .post(data)
            .build()

        client.newCall(request).enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                }

                override fun onResponse(call: Call, response: Response) {
                }
            }
        )
    }

    override fun inspect(remoteMessage: RemoteMessage): Boolean {
        val key = remoteMessage.data["unique_key"]!!

        if (handling == key) {
            return false
        }

        return true.also {
            handling = key
        }
    }

    override fun tokenReceived(token: String) {
        Companion.token = token
    }*/

    companion object {
        var token: String? = null
    }
}
