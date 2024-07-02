package io.phone.build.voipexample.ui.settings

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.text.InputType
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.R
import androidx.preference.SwitchPreferenceCompat
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import io.phone.build.voiplib.example.VoIPGRIDMiddleware
import io.phone.build.voipsdkandroid.PIL
import io.phone.build.voipsdkandroid.configuration.Auth
import io.phone.build.voipsdkandroid.configuration.AuthAssistant
import kotlinx.coroutines.withContext
import org.json.JSONObject

class SettingsFragment : PreferenceFragmentCompat() {

    private val prefs by lazy {
        PreferenceManager.getDefaultSharedPreferences(activity)
    }

    private val pil by lazy { PIL.instance }

    private val voIPGRIDMiddleware by lazy { VoIPGRIDMiddleware(requireActivity()) }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        loadFromFile()
        setPreferencesFromResource(io.phone.build.voipexample.R.xml.settings, rootKey)
        findPreference<EditTextPreference>("username")?.summaryProvider = Preference.SummaryProvider<EditTextPreference> {
            prefs.getString("username", "")
        }

        findPreference<Preference>("voipgrid_middleware_token")?.summaryProvider = Preference.SummaryProvider<Preference> {
            VoIPGRIDMiddleware.token
        }

        findPreference<EditTextPreference>("voipgrid_username")?.summaryProvider = Preference.SummaryProvider<EditTextPreference> {
            prefs.getString("voipgrid_username", "")
        }

        findPreference<EditTextPreference>("password")?.apply {
            setOnBindEditTextListener {
                it.inputType = InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            summaryProvider = Preference.SummaryProvider<EditTextPreference> {
                prefs.getString(
                    "password",
                    ""
                )
            }
        }

        findPreference<EditTextPreference>("voipgrid_password")?.apply {
            setOnBindEditTextListener {
                it.inputType = InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            summaryProvider = Preference.SummaryProvider<EditTextPreference> {
                prefs.getString(
                    "voipgrid_password",
                    ""
                )
            }
        }
        findPreference<EditTextPreference>("transport")?.apply {
            setOnBindEditTextListener {
                it.inputType = InputType.TYPE_TEXT_VARIATION_URI
            }
            summaryProvider = Preference.SummaryProvider<EditTextPreference> {
                prefs.getString(
                    "transport",
                    ""
                )
            }
        }

        findPreference<EditTextPreference>("proxy")?.apply {
            setOnBindEditTextListener {
                it.inputType = InputType.TYPE_TEXT_VARIATION_URI
            }
            summaryProvider = Preference.SummaryProvider<EditTextPreference> {
                prefs.getString(
                    "proxy",
                    ""
                )
            }
        }

        findPreference<EditTextPreference>("domain")?.apply {
            setOnBindEditTextListener {
                it.inputType = InputType.TYPE_TEXT_VARIATION_URI
            }
            summaryProvider = Preference.SummaryProvider<EditTextPreference> {
                prefs.getString(
                    "domain",
                    ""
                )
            }
        }

        findPreference<EditTextPreference>("port")?.apply {
            setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_NUMBER
            }
            summaryProvider = Preference.SummaryProvider<EditTextPreference> {
                prefs.getString(
                    "port",
                    ""
                )
            }
        }

        arrayOf("username", "password", "domain", "port", "proxy", "transport").forEach {
            findPreference<EditTextPreference>(it)?.setOnPreferenceChangeListener { _, _ ->
                Handler().postDelayed(
                    {
                        activity?.runOnUiThread { updateAuthenticationStatus() }
                    },
                    1000
                )
                true
            }
        }

        arrayOf("voipgrid_username", "voipgrid_password").forEach {
            findPreference<EditTextPreference>(it)?.setOnPreferenceChangeListener { _, _ ->
                Handler().postDelayed(
                    {
                        activity?.runOnUiThread { updateVoipgridAuthenticationStatus() }
                    },
                    1000
                )
                true
            }
        }

        findPreference<Preference>("status")?.setOnPreferenceClickListener {
            updateAuthenticationStatus()
            true
        }

        findPreference<Preference>("stop")?.setOnPreferenceClickListener {
            pil.stop()
            true
        }

        findPreference<Preference>("echo_cancellation_calibration")?.setOnPreferenceClickListener {
            pil.performEchoCancellationCalibration()
            true
        }

        findPreference<Preference>("voipgrid_middleware_register")?.setOnPreferenceClickListener {
            GlobalScope.launch {
                val newToken = prefs.getString("voipgrid_api_token", "")
                performRegister(newToken.toString())
            }
            true
        }

        findPreference<Preference>("voipgrid_middleware_unregister")?.setOnPreferenceClickListener {
            GlobalScope.launch {
                val message = if (voIPGRIDMiddleware.unregister()) "Unregistered!" else "Unregistration failed..."
                requireActivity().runOnUiThread { Toast.makeText(requireActivity(), message, Toast.LENGTH_LONG).show() }
            }
            true
        }

        findPreference<SwitchPreferenceCompat>("use_application_provided_ringtone")?.setOnPreferenceChangeListener { _, newValue ->
            PIL.instance.preferences = PIL.instance.preferences.copy(useApplicationProvidedRingtone = newValue as Boolean)
            true
        }
    }

    private fun updateVoipgridAuthenticationStatus() {
        val queue = Volley.newRequestQueue(requireActivity())

        val url = "https://api-prod.mipbx.vn/api/v1/users/login"

        val requestData = JSONObject().apply {
            put("email", prefs.getString("voipgrid_username", ""))
            put("password", prefs.getString("voipgrid_password", ""))
            put("type", "sf")
        }

        val request = JsonObjectRequest(
            Request.Method.POST,
            url,
            requestData,
            { response ->
                print("[RESPONSE]  $response")
                val apiToken = response.getString("data")
                updateVoipgridSummary(true, apiToken)
                prefs.edit().putString("voipgrid_api_token", apiToken).apply()
            },
            { error ->
                Toast.makeText(
                    requireContext(),
                    error.networkResponse.statusCode.toString(),
                    Toast.LENGTH_LONG
                ).show()
                updateVoipgridSummary(false)
                prefs.edit().remove("voipgrid_api_token").apply()
            }
        )

        queue.add(request)
    }

    private fun updateVoipgridSummary(authenticated: Boolean, token: String? = null) {
        print("[RETURN TOKEN SUMMARY] $token")
        val summary = if (authenticated) "$token" else "Authentication failed"

//        val tripleEncodedToken = pil.decodeTokenThreeTimes(token.toString())
//        println("[RETURN] Token đã mã hóa 3 lần: $tripleEncodedToken")
//
//        if (tripleEncodedToken != null) {
//
//            val key = "b6aed9ab7cdf85432c321757b4d48153"
//            val slicedStrings = pil.sliceStringWithKey(tripleEncodedToken, key)
//            println("[RETURN] Chuỗi đã cắt: $slicedStrings")
//            val decodedParts = pil.decodeEachPart(slicedStrings)
//            println("[RETURN] Các phần tử đã giải mã: $decodedParts")
//            decodedParts.forEachIndexed { index, part ->
//                println("Phần tử $index: $part")
//            }
//        } else {
//            println("[RETURN] Mã hóa thất bại")
//        }

        activity?.runOnUiThread {
            findPreference<Preference>("voipgrid_status")?.summaryProvider = Preference.SummaryProvider<Preference> {
                summary
            }

            findPreference<Preference>("voipgrid_middleware_register")?.isEnabled = authenticated
            findPreference<Preference>("voipgrid_middleware_unregister")?.isEnabled = authenticated
        }

    }

    private fun performRegister(token: String) {
        print("[RETURN TOKEN SUMMARY] $token")
        findPreference<Preference>("status")?.summaryProvider = Preference.SummaryProvider<Preference> {
            "Checking authentication..."
        }
    }

    /**
     * Updates the authentication status field.
     *
     */
    private fun updateAuthenticationStatus() {
        findPreference<Preference>("status")?.summaryProvider = Preference.SummaryProvider<Preference> {
            "Checking authentication..."
        }

        pil.fetchAuth()

        GlobalScope.launch(Dispatchers.IO) {
            val summary = if (pil.performRegistrationCheck()) "Authenticated" else "Authentication failed"
            withContext(Dispatchers.Main) {
                Toast.makeText(context, summary, Toast.LENGTH_SHORT).show()
            }
            /*activity?.runOnUiThread {
                findPreference<Preference>("status")?.summaryProvider = Preference.SummaryProvider<Preference> {
                    summary
                }
            }*/
        }
    }

    @SuppressLint("ApplySharedPref")
    private fun loadFromFile() {
        /*val keyToDefault = mapOf(
            "username" to getString(io.phone.build.voipexample.R.string.default_sip_user),
            "password" to getString(io.phone.build.voipexample.R.string.default_sip_password),
            "domain" to getString(io.phone.build.voipexample.R.string.default_sip_domain),
            "port" to getString(io.phone.build.voipexample.R.string.default_sip_port),
            "voipgrid_username" to getString(io.phone.build.voipexample.R.string.default_voipgrid_username),
            "voipgrid_password" to getString(io.phone.build.voipexample.R.string.default_voipgrid_password)
        )

        keyToDefault.forEach {
            if (prefs.getString(it.key, "")!!.isEmpty()) {
                prefs.edit().putString(it.key, it.value).commit()
            }
        }*/
    }

    override fun onResume() {
        super.onResume()
        updateAuthenticationStatus()
        updateVoipgridAuthenticationStatus()
    }
}