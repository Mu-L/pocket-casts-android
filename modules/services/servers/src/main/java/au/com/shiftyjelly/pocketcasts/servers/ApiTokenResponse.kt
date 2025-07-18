package au.com.shiftyjelly.pocketcasts.servers

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ApiTokenResponse(
    @Json(name = "token") val token: String? = null,
)
