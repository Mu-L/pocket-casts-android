package au.com.shiftyjelly.pocketcasts.servers.sync

import android.content.res.Resources
import au.com.shiftyjelly.pocketcasts.localization.helper.LocaliseHelper
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import retrofit2.HttpException
import timber.log.Timber

fun HttpException.parseErrorResponse(moshi: Moshi): ErrorResponse? {
    val errorBody = this.response()?.errorBody() ?: return null
    return try {
        moshi.adapter(ErrorResponse::class.java).fromJson(errorBody.source())
    } catch (e: Exception) {
        Timber.e(e)
        null
    }
}

@JsonClass(generateAdapter = true)
data class ErrorResponse(
    @Json(name = "errorMessage") val message: String,
    @Json(name = "errorMessageId") val messageId: String,
) {

    fun messageLocalized(resources: Resources): String {
        return LocaliseHelper.serverMessageIdToMessage(messageId, resources::getString) ?: message
    }
}
