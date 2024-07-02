package io.phone.build.voipsdkandroid.utils

import android.util.Base64

class ParseString {
    fun base64Encode(input: String): String {
        val data = input.toByteArray(Charsets.UTF_8)
        return Base64.encodeToString(data, Base64.DEFAULT)
    }

    fun base64Decode(input: String): String {
        val decodedBytes = Base64.decode(input, Base64.DEFAULT)
        return String(decodedBytes, Charsets.UTF_8)
    }

    fun encodeTokenThreeTimes(token: String): String? {
        var encodedToken = token
        repeat(3) {
            encodedToken = base64Encode(encodedToken)
        }
        return encodedToken
    }

    fun decodeTokenThreeTimes(token: String): String? {
        var decodedToken = token
        repeat(3) {
            decodedToken = base64Decode(decodedToken)
        }
        return decodedToken
    }

    fun sliceStringWithKey(input: String, key: String): List<String> {
        return input.split(key)
    }
}