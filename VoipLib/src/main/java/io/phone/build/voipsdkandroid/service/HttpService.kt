package io.phone.build.voipsdkandroid.service

import io.phone.build.voipsdkandroid.configuration.Auth
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

interface HttpService {
    @POST("validateLicence")
    fun validateLicence(@Body request: LicenceRequest): Response<LicenceResponse>

    @POST("path/to/auth/endpoint")
    fun getAuth(@Body request: AuthRequest): Call<Auth>
}

data class AuthRequest(val accessToken: String)

data class LicenceRequest(val licenceKey: String, val accessToken: String)
data class LicenceResponse(val isSuccessful: Boolean)

val api = Retrofit.Builder()
    .baseUrl("https://api-prod.mipbx.vn/api/v1/users/login")
    .addConverterFactory(GsonConverterFactory.create())
    .build()
    .create(HttpService::class.java)