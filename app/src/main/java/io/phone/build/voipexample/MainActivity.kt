package io.phone.build.voipexample

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import io.phone.build.voipexample.R
import io.phone.build.voipsdkandroid.PIL
import io.phone.build.voipsdkandroid.configuration.AuthAssistant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val pil by lazy { PIL.instance }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        pil.licenceConfig = AuthAssistant(accessToken = "V1RCa1MwNUZOVlZaTTFacFZqSjRNMWRYTlc1a1YxSjBUa1F4YVU1dFJteGFSR3hvV1dwa2FscEhXVFJPVkZGNlRXMU5lazFxUlROT1ZHUnBUa2RSTUU5RVJURk5NRFZWVmxSS1QyUjZNRGxaYWxwb1dsZFJOVmxYU1ROWk1sSnRUMFJWTUUxNlNtcE5la2w0VG5wVk0xbHFVbXRPUkdkNFRsUk9hazF0ZUROWk1HaExaRzFXU1dFelpFNVZla0kxVkZWU1NtUXdlSFJOV0VKcVVqQnZNRlJITldGa1Ywa3lXVmRXYTA5WFJtbE9NazVyV21wbk1VNUVUWGxaZWsxNVRWUmpNVTR5U1RCYVJGRTBUVlJWZWxRd1VrSmtNRFV6VUZReGFVNXRSbXhhUkd4b1dXcGthbHBIV1RST1ZGRjZUVzFOZWsxcVJUTk9WR1JwVGtkUk1FOUVSVEZOTVhCeFVWUkNUMDFyVlhkVU1WSlNUV3MxY1ZOWVpHRldSVVY2VkRCU1MySkZPVVZSVkVaUVVrZDBObGRXVWs1T1ZtdzJXa2RvVG1Gck1EbFphbHBvV2xkUk5WbFhTVE5aTWxKdFQwUlZNRTE2U21wTmVrbDRUbnBWTTFscVVtdE9SR2Q0VGxST2ExSXphRFk9", licencesKey = "trialabc")
        CoroutineScope(Dispatchers.Default).launch {
            pil.startedCore()
        }
        val navView: BottomNavigationView = findViewById(R.id.nav_view)

        val navController = findNavController(R.id.nav_host_fragment)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home,
                R.id.navigation_dashboard
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
    }
}