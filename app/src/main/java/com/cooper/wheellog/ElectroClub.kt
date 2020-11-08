package com.cooper.wheellog

import android.net.Uri
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.*
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlin.coroutines.*

class ElectroClub {

    companion object {
        @JvmStatic val instance: ElectroClub = ElectroClub()

        const val LOGIN_METHOD = "login"
        const val UPLOAD_METHOD = "uploadTrack"
        const val GET_GARAGE_METHOD = "getUserGarage"
        const val GET_GARAGE_METHOD_FILTRED = "garage"
        const val SUCCESS = 1
        const val ERROR = 0
    }

    private val url = "https://electro.club/api/v1"
    private val accessToken = BuildConfig.ec_accessToken
    private val client = OkHttpClient().newBuilder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

    var userToken: String? = null
    var userId: String? = null
    var lastError: String? = null
    var selectedGarage: String = "0"
    var methodCallback: ((method: String, type: Int, message: Any?)->Unit)? = null

    suspend fun loginAsync(email: String, password: String): Boolean {
        userToken = null
        userId = null

        val urlWithParams = Uri.parse(url)
                .buildUpon()
                .appendQueryParameter("method", LOGIN_METHOD)
                .appendQueryParameter("access_token", accessToken)
                .appendQueryParameter("email", email)
                .appendQueryParameter("password", password)
                .build()
                .toString()

        val request = Request.Builder()
                .url(urlWithParams)
                .method("GET", null)
                .build()

        return suspendCoroutine { continuation ->
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    userToken = null
                    userId = null
                    lastError = "[unexpected] " + e.message
                    methodCallback?.invoke(LOGIN_METHOD, ERROR, lastError)
                    e.printStackTrace()
                    continuation.resume(false)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        val json = JSONObject(response.body!!.string())
                        val nickname: String
                        if (!response.isSuccessful) {
                            userToken = null
                            userId = null
                            parseError(json)
                            methodCallback?.invoke(LOGIN_METHOD, ERROR, lastError)
                            continuation.resume(false)
                            return
                        } else {
                            val userObj = json.getObjectSafe("data")?.getObjectSafe("user")
                            if (userObj != null) {
                                userToken = userObj.getString("user_token")
                                userId = userObj.getString("user_id")
                                nickname = userObj.getString("nickname")
                            } else {
                                userToken = null
                                userId = null
                                continuation.resume(false)
                                return
                            }
                        }

                        WheelLog.AppConfig.setEcUserId(userId)
                        WheelLog.AppConfig.setEcToken(userToken)
                        methodCallback?.invoke(LOGIN_METHOD, SUCCESS, nickname)
                        continuation.resume(true)
                    }
                }
            })
        }
    }

    fun uploadTrack(data: ByteArray, fileName: String, verified: Boolean): Boolean = runBlocking {
        return@runBlocking async { uploadTrackAsync(data, fileName, verified) }.await()
    }

    suspend fun uploadTrackAsync(data: ByteArray, fileName: String, verified: Boolean): Boolean {
        if (userToken == null)
        {
            lastError = "Missing parameters"
            methodCallback?.invoke(UPLOAD_METHOD, ERROR, lastError)
            return false
        }
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"), Locale.getDefault())
        val currentLocalTime = calendar.time
        val date = SimpleDateFormat("Z", Locale.getDefault())
        var localTime = date.format(currentLocalTime)
        localTime = StringBuilder(localTime).insert(localTime.length - 2, ":").toString()
        val mediaType = "text/csv".toMediaTypeOrNull()
        val bodyBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("method", UPLOAD_METHOD)
                .addFormDataPart("access_token", accessToken)
                .addFormDataPart("user_token", userToken!!)
                .addFormDataPart("file", fileName, data.toRequestBody(mediaType))
                .addFormDataPart("time_zone", localTime)
        if (selectedGarage != "0") {
            bodyBuilder.addFormDataPart("garage_id", selectedGarage)
        }
        if (verified) {
            bodyBuilder.addFormDataPart("verified", "1")
        }

        val request = Request.Builder()
                .url(url)
                .method("POST", bodyBuilder.build())
                .build()

        return suspendCoroutine { continuation ->
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    lastError = "[unexpected] " + e.message
                    methodCallback?.invoke(UPLOAD_METHOD, ERROR, lastError)
                    e.printStackTrace()
                    continuation.resume(false)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        val json = JSONObject(response.body!!.string())
                        if (!response.isSuccessful) {
                            parseError(json)
                            methodCallback?.invoke(UPLOAD_METHOD, ERROR, lastError)
                            continuation.resume(false)
                        } else {
                            methodCallback?.invoke(UPLOAD_METHOD, SUCCESS, json)
                            continuation.resume(true)
                        }
                    }
                }
            })
        }
    }

    fun getAndSelectGarageByMac(mac: String): Boolean = runBlocking {
        return@runBlocking async { getAndSelectGarageByMacAsync(mac) }.await()
    }

    suspend fun getAndSelectGarageByMacAsync(mac: String): Boolean {
        if (selectedGarage != "0") {
            return true // already selected
        }
        val transportList = getGarageAsync()
        if (transportList.length() == 0) {
            return false
        }

        return suspendCoroutine { continuation ->
            val len = transportList.length() - 1
            //var primaryId: String? = null
            for (i in 0..len) {
                val transport = transportList.getJSONObject(i)
                val m = transport.getStringSafe("MAC")
                //val isPrimary = transport.getStringSafe("primary") == "1"
                if (m != null && m == mac) {
                    selectedGarage = transport.getString("id")
                    continuation.resume(true)
                    methodCallback?.invoke(GET_GARAGE_METHOD_FILTRED, SUCCESS, transport.getString("name"))
                    break
                }
                /*if (isPrimary) {
                    primaryId = transport.getString("id")
                }*/
            }

            // TODO todo todo need UI
            /*if (primaryId != null)
            {
                selectedGarage = primaryId
                success(selectedGarage)
            }*/
        }
    }

    suspend fun getGarageAsync(): JSONArray {
        if (userToken == null || userId == null)
        {
            lastError = "Missing parameters"
            methodCallback?.invoke(GET_GARAGE_METHOD, ERROR, lastError)
            return JSONArray()
        }
        val urlWithParams = Uri.parse(url)
                .buildUpon()
                .appendQueryParameter("method", GET_GARAGE_METHOD)
                .appendQueryParameter("access_token", accessToken)
                .appendQueryParameter("user_token", userToken)
                .appendQueryParameter("user_id", userId)
                .build()
                .toString()

        val request = Request.Builder()
                .url(urlWithParams)
                .method("GET", null)
                .build()

        return suspendCoroutine { continuation ->
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    lastError = "[unexpected] " + e.message
                    methodCallback?.invoke(GET_GARAGE_METHOD, ERROR, lastError)
                    e.printStackTrace()
                    continuation.resume(JSONArray())
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        val json = JSONObject(response.body!!.string())
                        if (!response.isSuccessful) {
                            parseError(json)
                            methodCallback?.invoke(GET_GARAGE_METHOD, ERROR, lastError)
                            continuation.resume(JSONArray())
                        } else {
                            val data = json.getObjectSafe("data")
                            if (data == null || !data.has("transport_list")) {
                                lastError = "no transport"
                                methodCallback?.invoke(GET_GARAGE_METHOD, ERROR, lastError)
                                return
                            }
                            val transportList = data.getJSONArray("transport_list")
                            methodCallback?.invoke(GET_GARAGE_METHOD, SUCCESS, transportList.length())
                            continuation.resume(transportList)
                        }
                    }
                }
            })
        }
    }

    private fun parseError(jsonObject: JSONObject?) {
        lastError = jsonObject
                ?.getObjectSafe("data")
                ?.getStringSafe("error")
                ?: "Unknown error"
    }

    private fun JSONObject.getObjectSafe(name: String): JSONObject? {
        if (this.has(name)) {
            return this.getJSONObject(name)
        }
        return null
    }

    private fun JSONObject.getStringSafe(name: String): String? {
        if (this.has(name)) {
            return this.getString(name)
        }
        return null
    }
}