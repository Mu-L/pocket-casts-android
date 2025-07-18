package au.com.shiftyjelly.pocketcasts.crashlogging.fakes

import au.com.shiftyjelly.pocketcasts.crashlogging.fakes.FakeEncryptedLogging.UploadState.FINISHED
import au.com.shiftyjelly.pocketcasts.crashlogging.fakes.FakeEncryptedLogging.UploadState.IN_PROGRESS
import au.com.shiftyjelly.pocketcasts.crashlogging.fakes.FakeEncryptedLogging.UploadState.NOT_STARTED
import com.automattic.encryptedlogging.EncryptedLogging
import java.io.File

class FakeEncryptedLogging : EncryptedLogging {

    enum class UploadState {
        NOT_STARTED,
        IN_PROGRESS,
        FINISHED,
    }

    var toUpload = listOf<Pair<File, UploadState>>()
    var uploaded = listOf<Pair<File, UploadState>>()

    override fun enqueueSendingEncryptedLogs(
        uuid: String,
        file: File,
        shouldUploadImmediately: Boolean,
    ) {
        toUpload += (file to if (shouldUploadImmediately) IN_PROGRESS else NOT_STARTED)
    }

    override fun resetUploadStates() {
        toUpload = toUpload.map {
            it.first to NOT_STARTED
        }
    }

    override fun uploadEncryptedLogs() {
        uploaded += toUpload.map {
            it.first to FINISHED
        }
        toUpload = emptyList()
    }
}
